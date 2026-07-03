package com.personalfinance.service.parser;

import static org.assertj.core.api.Assertions.*;

import com.personalfinance.dto.response.ParsedTransactionDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NubankFaturaParserTest {

  private static NubankFaturaParser parser;
  private static NubankFaturaParser.ParseResult result;

  @BeforeAll
  static void setUp() throws Exception {
    parser = new NubankFaturaParser();
    try (InputStream is =
        NubankFaturaParserTest.class.getClassLoader().getResourceAsStream("fatura.pdf")) {
      assertThat(is).isNotNull();
      try (PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
        String text = new PDFTextStripper().getText(doc);
        result = parser.parse(text);
      }
    }
  }

  @Test
  void period_is_may_to_jun_2026() {
    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 5, 8));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 6, 8));
  }

  @Test
  void welisson_transactions_have_correct_holder() {
    List<ParsedTransactionDTO> welissonTxs =
        result.transactions().stream()
            .filter(t -> "Welisson W Oliveira".equals(t.getCardHolder()))
            .toList();
    assertThat(welissonTxs).isNotEmpty();
  }

  @Test
  void rosangela_transactions_have_correct_holder() {
    List<ParsedTransactionDTO> rosangelaTxs =
        result.transactions().stream()
            .filter(t -> "Rosangela Oliveira".equals(t.getCardHolder()))
            .toList();
    assertThat(rosangelaTxs).isNotEmpty();
  }

  @Test
  void amazon_installment_info_extracted() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("Amazonmktplc*Belezavar")
                    && "5/11".equals(t.getInstallmentInfo()));
  }

  @Test
  void estorno_amazon_is_income() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("Amazonmktplc*Anacaroli")
                    && "INCOME".equals(t.getType())
                    && t.getAmount().compareTo(new BigDecimal("79.99")) == 0);
  }

  @Test
  void payment_line_is_filtered() {
    assertThat(result.transactions())
        .noneMatch(
            t -> t.getDescription() != null && t.getDescription().startsWith("Pagamento em"));
  }

  @Test
  void nagumo_is_present_under_rosangela() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("Nagumo")
                    && "Rosangela Oliveira".equals(t.getCardHolder())
                    && "EXPENSE".equals(t.getType()));
  }

  @Test
  void anthropic_is_present_under_welisson() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("Anthropic")
                    && "Welisson W Oliveira".equals(t.getCardHolder())
                    && "EXPENSE".equals(t.getType()));
  }

  @Test
  void at_least_fifty_transactions_present() {
    assertThat(result.transactions()).hasSizeGreaterThanOrEqualTo(50);
  }

  @Test
  void all_transactions_have_positive_amounts() {
    assertThat(result.transactions()).allMatch(t -> t.getAmount().compareTo(BigDecimal.ZERO) > 0);
  }
}
