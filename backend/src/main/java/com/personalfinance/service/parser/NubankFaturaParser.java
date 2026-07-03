package com.personalfinance.service.parser;

import com.personalfinance.dto.response.ParsedTransactionDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NubankFaturaParser {

  private static final Pattern PERIOD_PATTERN =
      Pattern.compile("Per.odo vigente: (\\d{2}) ([A-Z]{3}) a (\\d{2}) ([A-Z]{3})");

  private static final Pattern DUE_DATE_YEAR =
      Pattern.compile("Data de vencimento: \\d{2} [A-Z]{3} (\\d{4})");

  // Matches: DD MMM [???? XXXX] DESCRIPTION R$ AMOUNT  (or ?R$ AMOUNT for credits)
  private static final Pattern TX_LINE =
      Pattern.compile("^(\\d{2}) ([A-Z]{3}) (.+) (.?)R\\$ ([\\d.,]+)$");

  // Matches: DD MMM start (no R$ at end) → multi-line transaction
  private static final Pattern TX_DATE_ONLY = Pattern.compile("^(\\d{2}) ([A-Z]{3}) (.+)$");

  // Matches a standalone amount line: [?]R$ AMOUNT
  private static final Pattern AMOUNT_LINE = Pattern.compile("^(.?)R\\$ ([\\d.,]+)$");

  // Installment pattern in description
  private static final Pattern INSTALLMENT = Pattern.compile("(?:- )?Parcela (\\d+/\\d+)$");

  // Card prefix: 4 chars + space + 4 digits + space
  private static final Pattern CARD_PREFIX = Pattern.compile("^.{4} (\\d{4}) (.+)");

  public ParseResult parse(String text) {
    LocalDate periodStart = null;
    LocalDate periodEnd = null;
    int year = LocalDate.now().getYear();

    String[] lines = text.split("\\n");

    for (String rawLine : lines) {
      String line = rawLine.trim();
      Matcher pm = PERIOD_PATTERN.matcher(line);
      if (pm.find() && periodStart == null) {
        Matcher ym = DUE_DATE_YEAR.matcher(text);
        if (ym.find()) year = Integer.parseInt(ym.group(1));
        int startDay = Integer.parseInt(pm.group(1));
        int startMonth = monthNum(pm.group(2));
        int endDay = Integer.parseInt(pm.group(3));
        int endMonth = monthNum(pm.group(4));
        int startYear = (startMonth > endMonth) ? year - 1 : year;
        periodStart = LocalDate.of(startYear, startMonth, startDay);
        periodEnd = LocalDate.of(year, endMonth, endDay);
      }
    }

    List<ParsedTransactionDTO> transactions = new ArrayList<>();
    String currentHolder = null;
    boolean inPayments = false;
    boolean started = false;

    boolean multiLine = false;
    int multiDay = 0;
    String multiMon = null;
    List<String> multiDesc = new ArrayList<>();

    for (String rawLine : lines) {
      String line = rawLine.trim();

      if (!started) {
        if (line.startsWith("TRANSA")) {
          started = true;
        }
        continue;
      }

      if (isBoilerplate(line)) continue;
      if (line.isEmpty()) continue;

      // Section: Welisson
      if (line.matches("Welisson W Oliveira R\\$.*")) {
        currentHolder = "Welisson W Oliveira";
        inPayments = false;
        multiLine = false;
        multiDesc = new ArrayList<>();
        continue;
      }

      // Section: Rosangela
      if (line.matches("Compras de Rosangela Oliveira.*")) {
        currentHolder = "Rosangela Oliveira";
        inPayments = false;
        multiLine = false;
        multiDesc = new ArrayList<>();
        continue;
      }

      // Section: Payments (filter)
      if (line.matches("Pagamentos.*R\\$.*")) {
        inPayments = true;
        multiLine = false;
        multiDesc = new ArrayList<>();
        continue;
      }

      if (inPayments) continue;

      // Handle multi-line continuation
      if (multiLine) {
        Matcher am = AMOUNT_LINE.matcher(line);
        if (am.matches()) {
          boolean isCredit = !am.group(1).isEmpty();
          BigDecimal amount = parseBrazilian(am.group(2));
          String rawDesc = String.join(" ", multiDesc).replaceAll("\\s+", " ").trim();
          String cleanDesc = stripCardPrefix(rawDesc);
          String installment = extractInstallment(cleanDesc);
          if (installment != null) {
            cleanDesc = cleanDesc.replaceAll("(?:- )?Parcela \\d+/\\d+$", "").trim();
          }
          if (currentHolder != null) {
            addTransaction(
                transactions,
                LocalDate.of(year, monthNum(multiMon), multiDay),
                cleanDesc,
                amount,
                isCredit ? "INCOME" : "EXPENSE",
                currentHolder,
                installment);
          }
          multiLine = false;
          multiDesc = new ArrayList<>();
          continue;
        }

        // Check if next transaction date starts (abandons current multi-line)
        Matcher nextTx = TX_LINE.matcher(line);
        if (nextTx.matches() || isSection(line)) {
          multiLine = false;
          multiDesc = new ArrayList<>();
          // fall through to process this line normally
        } else {
          multiDesc.add(line);
          continue;
        }
      }

      // Single-line transaction
      Matcher txm = TX_LINE.matcher(line);
      if (txm.matches() && currentHolder != null) {
        int day = Integer.parseInt(txm.group(1));
        String mon = txm.group(2);
        String descPart = txm.group(3).trim();
        boolean isCredit = !txm.group(4).isEmpty();
        BigDecimal amount = parseBrazilian(txm.group(5));

        if (descPart.startsWith("Pagamento em")) continue;

        String cleanDesc = stripCardPrefix(descPart);
        String installment = extractInstallment(cleanDesc);
        if (installment != null) {
          cleanDesc = cleanDesc.replaceAll("(?:- )?Parcela \\d+/\\d+$", "").trim();
        }

        addTransaction(
            transactions,
            LocalDate.of(year, monthNum(mon), day),
            cleanDesc,
            amount,
            isCredit ? "INCOME" : "EXPENSE",
            currentHolder,
            installment);
        continue;
      }

      // Multi-line transaction start (no R$ at end)
      Matcher partm = TX_DATE_ONLY.matcher(line);
      if (partm.matches() && currentHolder != null) {
        multiLine = true;
        multiDay = Integer.parseInt(partm.group(1));
        multiMon = partm.group(2);
        multiDesc = new ArrayList<>();
        multiDesc.add(partm.group(3).trim());
      }
    }

    return new ParseResult(periodStart, periodEnd, transactions);
  }

  private void addTransaction(
      List<ParsedTransactionDTO> list,
      LocalDate date,
      String description,
      BigDecimal amount,
      String type,
      String cardHolder,
      String installmentInfo) {
    list.add(
        ParsedTransactionDTO.builder()
            .date(date)
            .description(description)
            .amount(amount)
            .type(type)
            .cardHolder(cardHolder)
            .installmentInfo(installmentInfo)
            .build());
  }

  private String stripCardPrefix(String desc) {
    Matcher m = CARD_PREFIX.matcher(desc);
    if (m.matches()) return m.group(2).trim();
    return desc.trim();
  }

  private String extractInstallment(String desc) {
    Matcher m = INSTALLMENT.matcher(desc);
    if (m.find()) return m.group(1);
    return null;
  }

  private boolean isSection(String line) {
    return line.matches("Welisson W Oliveira R\\$.*")
        || line.matches("Compras de Rosangela Oliveira.*")
        || line.matches("Pagamentos.*R\\$.*");
  }

  private boolean isBoilerplate(String line) {
    if (line.isEmpty()) return true;
    if (line.matches("\\d+ de \\d+")) return true;
    if (line.equals("WELISSON WILSON OLIVEIRA")) return true;
    if (line.startsWith("FATURA")) return true;
    if (line.startsWith("TRANSA")) return true;
    if (line.startsWith("Em cumprimento")) return true;
    if (line.startsWith("Como assegurado")) return true;
    return false;
  }

  private BigDecimal parseBrazilian(String value) {
    return new BigDecimal(value.replace(".", "").replace(",", "."));
  }

  private int monthNum(String s) {
    String m = s.toUpperCase();
    if (m.startsWith("JAN")) return 1;
    if (m.startsWith("FEV")) return 2;
    if (m.startsWith("MAR")) return 3;
    if (m.startsWith("ABR")) return 4;
    if (m.startsWith("MAI")) return 5;
    if (m.startsWith("JUN")) return 6;
    if (m.startsWith("JUL")) return 7;
    if (m.startsWith("AGO")) return 8;
    if (m.startsWith("SET")) return 9;
    if (m.startsWith("OUT")) return 10;
    if (m.startsWith("NOV")) return 11;
    if (m.startsWith("DEZ")) return 12;
    throw new IllegalArgumentException("Unknown month: " + s);
  }

  public record ParseResult(
      LocalDate periodStart, LocalDate periodEnd, List<ParsedTransactionDTO> transactions) {}
}
