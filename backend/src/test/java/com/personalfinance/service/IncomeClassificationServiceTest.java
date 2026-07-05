package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.response.ParsedTransactionDTO;
import com.personalfinance.model.entity.KnownPerson;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.repository.KnownPersonRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncomeClassificationServiceTest {

  @Mock private KnownPersonRepository knownPersonRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private MerchantNormalizationService normalizationService;

  @InjectMocks private IncomeClassificationService service;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String HOLDER = "João Silva";

  @Test
  void classify_open_banking_own_name_sets_own_transfer_excluded() {
    ParsedTransactionDTO tx = dto("Transferência via Open Banking João Silva 500,00");

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getIncomeType()).isEqualTo("OWN_TRANSFER");
    assertThat(tx.getAutoClassification()).isEqualTo("OWN_TRANSFER");
    assertThat(tx.isIncluded()).isFalse();
  }

  @Test
  void classify_known_person_uses_their_income_type() {
    ParsedTransactionDTO tx = dto("Transferência recebida pelo Pix Maria Fernanda Santos 1000,00");
    KnownPerson person =
        KnownPerson.builder()
            .id(UUID.randomUUID())
            .name("Maria Fernanda Santos")
            .defaultIncomeType("REIMBURSEMENT")
            .active(true)
            .build();
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(person));

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getIncomeType()).isEqualTo("REIMBURSEMENT");
    assertThat(tx.getKnownPersonId()).isEqualTo(person.getId());
  }

  @Test
  void classify_uses_history_when_previous_transaction_exists() {
    ParsedTransactionDTO tx = dto("Salário Empresa XYZ");
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of());
    when(normalizationService.normalize("Salário Empresa XYZ")).thenReturn("Empresa XYZ");

    Transaction existing =
        Transaction.builder()
            .incomeType("INCOME")
            .date(LocalDate.of(2026, 4, 5))
            .amount(BigDecimal.valueOf(3000))
            .build();
    when(transactionRepository.findByUserIdAndEffectiveName(USER_ID, "Empresa XYZ"))
        .thenReturn(List.of(existing));

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getIncomeType()).isEqualTo("INCOME");
  }

  @Test
  void classify_history_reimbursement_is_learned() {
    ParsedTransactionDTO tx = dto("Reembolso empresa ABC");
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of());
    when(normalizationService.normalize("Reembolso empresa ABC")).thenReturn("Empresa ABC");

    Transaction existing =
        Transaction.builder()
            .incomeType("REIMBURSEMENT")
            .date(LocalDate.of(2026, 3, 10))
            .amount(BigDecimal.valueOf(200))
            .build();
    when(transactionRepository.findByUserIdAndEffectiveName(USER_ID, "Empresa ABC"))
        .thenReturn(List.of(existing));

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getIncomeType()).isEqualTo("REIMBURSEMENT");
  }

  @Test
  void classify_history_own_transfer_is_not_used_as_learned_type() {
    ParsedTransactionDTO tx = dto("Transferência própria desconhecida");
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of());
    when(normalizationService.normalize("Transferência própria desconhecida"))
        .thenReturn("Transferência própria desconhecida");

    Transaction existingOwnTransfer =
        Transaction.builder()
            .incomeType("OWN_TRANSFER")
            .date(LocalDate.of(2026, 4, 1))
            .amount(BigDecimal.valueOf(500))
            .build();
    when(transactionRepository.findByUserIdAndEffectiveName(
            USER_ID, "Transferência própria desconhecida"))
        .thenReturn(List.of(existingOwnTransfer));

    service.classify(tx, USER_ID, HOLDER);

    // OWN_TRANSFER from history is skipped; falls back to default INCOME
    assertThat(tx.getIncomeType()).isEqualTo("INCOME");
  }

  @Test
  void classify_falls_back_to_income_when_no_history_no_known_person() {
    ParsedTransactionDTO tx = dto("Pix recebido de alguém desconhecido");
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of());
    when(normalizationService.normalize(anyString())).thenReturn("alguém desconhecido");
    when(transactionRepository.findByUserIdAndEffectiveName(any(), any())).thenReturn(List.of());

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getIncomeType()).isEqualTo("INCOME");
    assertThat(tx.isIncluded()).isTrue();
  }

  private ParsedTransactionDTO dto(String description) {
    return ParsedTransactionDTO.builder()
        .date(LocalDate.now())
        .description(description)
        .amount(BigDecimal.valueOf(100))
        .type("INCOME")
        .build();
  }
}
