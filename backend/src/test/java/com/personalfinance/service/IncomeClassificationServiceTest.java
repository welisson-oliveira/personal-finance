package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.response.ParsedTransactionDTO;
import com.personalfinance.model.entity.KnownPerson;
import com.personalfinance.repository.KnownPersonRepository;
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

  @InjectMocks private IncomeClassificationService service;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String HOLDER = "João Silva";

  @Test
  void classify_open_banking_own_name_is_ignored_and_excluded() {
    ParsedTransactionDTO tx = dto("Transferência via Open Banking João Silva 500,00");

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.isIgnored()).isTrue();
    assertThat(tx.getAutoClassification()).isEqualTo("OWN_TRANSFER");
    assertThat(tx.isIncluded()).isFalse();
  }

  @Test
  void classify_known_person_income_treatment_keeps_plain_income() {
    ParsedTransactionDTO tx = dto("Transferência recebida pelo Pix Maria Fernanda Santos 1000,00");
    KnownPerson person =
        KnownPerson.builder()
            .id(UUID.randomUUID())
            .name("Maria Fernanda Santos")
            .defaultTreatment("INCOME")
            .defaultLabel("Aluguel")
            .active(true)
            .build();
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(person));

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getKnownPersonId()).isEqualTo(person.getId());
    assertThat(tx.getNotes()).isEqualTo("Aluguel");
    assertThat(tx.isIgnored()).isFalse();
    assertThat(tx.isNeedsReview()).isFalse();
  }

  @Test
  void classify_known_person_ignore_treatment_marks_ignored() {
    ParsedTransactionDTO tx = dto("Transferência recebida pelo Pix Fulano Conta 750,00");
    KnownPerson person =
        KnownPerson.builder()
            .id(UUID.randomUUID())
            .name("Fulano Conta")
            .defaultTreatment("IGNORE")
            .active(true)
            .build();
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(person));

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.isIgnored()).isTrue();
    assertThat(tx.isIncluded()).isFalse();
    assertThat(tx.getKnownPersonId()).isEqualTo(person.getId());
  }

  @Test
  void classify_known_person_always_review_flags_review() {
    ParsedTransactionDTO tx = dto("Transferência recebida pelo Pix Carlos Eduardo Lima 750,00");
    KnownPerson person =
        KnownPerson.builder()
            .id(UUID.randomUUID())
            .name("Carlos Eduardo Lima")
            .defaultTreatment("ALWAYS_REVIEW")
            .active(true)
            .build();
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of(person));

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.isNeedsReview()).isTrue();
    assertThat(tx.isIgnored()).isFalse();
    assertThat(tx.getKnownPersonId()).isEqualTo(person.getId());
  }

  @Test
  void classify_unknown_third_party_is_plain_income() {
    ParsedTransactionDTO tx = dto("Pix recebido de alguém desconhecido");
    when(knownPersonRepository.findByUserIdAndActiveTrue(USER_ID)).thenReturn(List.of());

    service.classify(tx, USER_ID, HOLDER);

    assertThat(tx.getType()).isEqualTo("INCOME");
    assertThat(tx.isIgnored()).isFalse();
    assertThat(tx.isNeedsReview()).isFalse();
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
