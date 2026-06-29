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

class NubankExtratoParserTest {

  private static NubankExtratoParser parser;
  private static NubankExtratoParser.ParseResult result;

  private static final String HOLDER = "Welisson Wilson Oliveira";

  @BeforeAll
  static void setUp() throws Exception {
    parser = new NubankExtratoParser();
    try (InputStream is =
        NubankExtratoParserTest.class.getClassLoader().getResourceAsStream("extrato.pdf")) {
      assertThat(is).isNotNull();
      try (PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
        String text = new PDFTextStripper().getText(doc);
        result = parser.parse(text, HOLDER);
      }
    }
  }

  @Test
  void period_is_may_2026() {
    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 5, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 5, 31));
  }

  @Test
  void rdb_transactions_are_filtered() {
    List<ParsedTransactionDTO> txs = result.transactions();
    assertThat(txs)
        .noneMatch(t -> t.getDescription().contains("Resgate RDB"))
        .noneMatch(
            t ->
                t.getDescription().toLowerCase().contains("aplica")
                    && t.getDescription().contains("RDB"));
  }

  @Test
  void pagamento_fatura_is_filtered() {
    assertThat(result.transactions())
        .noneMatch(t -> t.getDescription().startsWith("Pagamento de fatura"));
  }

  @Test
  void open_banking_own_transfer_is_filtered() {
    assertThat(result.transactions())
        .noneMatch(
            t ->
                t.getDescription().contains("Open Banking") && t.getDescription().contains(HOLDER));
  }

  @Test
  void paula_pix_is_present_as_income() {
    List<ParsedTransactionDTO> paulaTxs =
        result.transactions().stream()
            .filter(t -> t.getDescription().contains("PAULA DANIELE SANTOS OLIV"))
            .toList();
    assertThat(paulaTxs).isNotEmpty();
    assertThat(paulaTxs).allMatch(t -> "INCOME".equals(t.getType()));
  }

  @Test
  void mediar_concilia_pix_is_present_as_income() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("MEDIAR")
                    && "INCOME".equals(t.getType())
                    && t.getAmount().compareTo(new BigDecimal("300.00")) == 0);
  }

  @Test
  void boleto_sabesp_is_present_as_expense() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("SABESP")
                    && "EXPENSE".equals(t.getType())
                    && t.getAmount().compareTo(new BigDecimal("643.89")) == 0);
  }

  @Test
  void boleto_pref_mun_is_present_as_expense() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDescription().contains("PREF MUN TAUBATE")
                    && "EXPENSE".equals(t.getType())
                    && t.getAmount().compareTo(new BigDecimal("209.10")) == 0);
  }

  @Test
  void transactions_have_correct_dates() {
    assertThat(result.transactions())
        .anyMatch(
            t ->
                t.getDate().equals(LocalDate.of(2026, 5, 1))
                    && t.getDescription().contains("PAULA"));
  }

  @Test
  void at_least_twenty_transactions_present() {
    assertThat(result.transactions()).hasSizeGreaterThanOrEqualTo(20);
  }
}
