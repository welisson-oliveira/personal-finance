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
public class NubankExtratoParser {

  private static final Pattern PERIOD_PATTERN =
      Pattern.compile("(\\d{2}) DE ([A-Z]+) DE (\\d{4})\\s+(\\d{2}) DE ([A-Z]+) DE (\\d{4})");

  private static final Pattern DAY_DATE =
      Pattern.compile("^(\\d{2}) ([A-Z]{3}) (\\d{4}) Total de (entradas|sa.das)");

  private static final Pattern DIRECTION_ONLY =
      Pattern.compile("^Total de (entradas|sa.das) [+\\-]");

  private static final Pattern AMOUNT_ONLY = Pattern.compile("^\\d{1,3}(?:\\.\\d{3})*,\\d{2}$");

  private static final Pattern TRAILING_DECIMAL = Pattern.compile("([\\d.]+,[\\d]{2})$");

  private static final Pattern BOLETO_AMOUNT =
      Pattern.compile("^Pagamento de boleto efetuado (.+?) ([\\d.]+,[\\d]{2})$");

  public ParseResult parse(String text, String accountHolderName) {
    LocalDate periodStart = null;
    LocalDate periodEnd = null;

    String[] lines = text.split("\\n");

    for (String rawLine : lines) {
      String line = rawLine.trim().toUpperCase();
      Matcher pm = PERIOD_PATTERN.matcher(line);
      if (pm.find() && periodStart == null) {
        periodStart =
            LocalDate.of(
                Integer.parseInt(pm.group(3)),
                monthNum(pm.group(2)),
                Integer.parseInt(pm.group(1)));
        periodEnd =
            LocalDate.of(
                Integer.parseInt(pm.group(6)),
                monthNum(pm.group(5)),
                Integer.parseInt(pm.group(4)));
      }
    }

    List<ParsedTransactionDTO> transactions = new ArrayList<>();
    LocalDate currentDate = null;
    boolean incomeBlock = true;
    List<String> descLines = new ArrayList<>();
    boolean collecting = false;
    boolean started = false;

    for (String rawLine : lines) {
      String line = rawLine.trim();

      if (!started) {
        if (line.startsWith("Movimenta")) {
          started = true;
        }
        continue;
      }

      if (isBoilerplate(line, accountHolderName)) continue;
      if (line.isEmpty()) continue;

      Matcher dm = DAY_DATE.matcher(line);
      if (dm.find()) {
        if (collecting) {
          emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
        }
        collecting = false;
        descLines = new ArrayList<>();
        currentDate =
            LocalDate.of(
                Integer.parseInt(dm.group(3)),
                monthNum(dm.group(2)),
                Integer.parseInt(dm.group(1)));
        incomeBlock = "entradas".equals(dm.group(4));
        continue;
      }

      Matcher dirm = DIRECTION_ONLY.matcher(line);
      if (dirm.find()) {
        if (collecting) {
          emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
        }
        collecting = false;
        descLines = new ArrayList<>();
        incomeBlock = "entradas".equals(dirm.group(1));
        continue;
      }

      if (isRdb(line) || isPagamentoFatura(line)) {
        if (collecting) {
          emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
          collecting = false;
          descLines = new ArrayList<>();
        }
        continue;
      }

      if (isCreditoConta(line)) {
        if (collecting) {
          emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
          collecting = false;
          descLines = new ArrayList<>();
        }
        BigDecimal amount = extractTrailing(line);
        if (amount != null && currentDate != null) {
          transactions.add(
              ParsedTransactionDTO.builder()
                  .date(currentDate)
                  .description("Crédito em conta")
                  .amount(amount)
                  .type("INCOME")
                  .build());
        }
        continue;
      }

      if (line.startsWith("Pagamento de boleto efetuado")) {
        if (collecting) {
          emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
          collecting = false;
          descLines = new ArrayList<>();
        }
        Matcher bm = BOLETO_AMOUNT.matcher(line);
        if (bm.matches() && currentDate != null) {
          transactions.add(
              ParsedTransactionDTO.builder()
                  .date(currentDate)
                  .description("Pagamento de boleto efetuado " + bm.group(1))
                  .amount(parseBrazilian(bm.group(2)))
                  .type("EXPENSE")
                  .build());
        }
        continue;
      }

      if (line.startsWith("Transfer")) {
        if (collecting) {
          emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
        }
        collecting = true;
        descLines = new ArrayList<>();
        descLines.add(line);
        continue;
      }

      if (AMOUNT_ONLY.matcher(line).matches()) {
        if (collecting) {
          BigDecimal amount = parseBrazilian(line);
          emitTransaction(descLines, currentDate, incomeBlock, amount, transactions);
          collecting = false;
          descLines = new ArrayList<>();
        }
        continue;
      }

      if (collecting) {
        descLines.add(line);
      }
    }

    if (collecting) {
      emitFromAccumulated(descLines, currentDate, incomeBlock, transactions);
    }

    return new ParseResult(periodStart, periodEnd, transactions);
  }

  private void emitFromAccumulated(
      List<String> descLines,
      LocalDate date,
      boolean incomeBlock,
      List<ParsedTransactionDTO> transactions) {
    if (descLines.isEmpty() || date == null) return;
    BigDecimal amount = null;
    for (String dl : descLines) {
      Matcher m = TRAILING_DECIMAL.matcher(dl);
      if (m.find()) {
        try {
          amount = parseBrazilian(m.group(1));
          break;
        } catch (Exception ignored) {
        }
      }
    }
    if (amount != null) {
      emitTransaction(descLines, date, incomeBlock, amount, transactions);
    }
  }

  private void emitTransaction(
      List<String> descLines,
      LocalDate date,
      boolean incomeBlock,
      BigDecimal amount,
      List<ParsedTransactionDTO> transactions) {
    if (date == null || descLines.isEmpty()) return;
    String firstLine = descLines.get(0);
    String type = incomeBlock ? "INCOME" : "EXPENSE";
    boolean openBanking =
        firstLine.contains("via")
            && descLines.size() > 1
            && descLines.stream().anyMatch(l -> l.contains("Open Banking"));
    transactions.add(
        ParsedTransactionDTO.builder()
            .date(date)
            .description(String.join(" ", descLines).replaceAll("\\s+", " ").trim())
            .amount(amount)
            .type(type)
            .build());
  }

  private boolean isBoilerplate(String line, String holderName) {
    if (line.isEmpty()) return true;
    if (line.matches("\\d+ de \\d+")) return true;
    if (line.contains("Tem alguma")) return true;
    if (line.startsWith("Caso a solu")) return true;
    if (line.startsWith("Extrato gerado")) return true;
    if (line.contains("nubank.com.br")) return true;
    if (line.contains("0800")) return true;
    if (line.startsWith("Nu Financeira") || line.startsWith("Nu Pagamentos")) return true;
    if (line.startsWith("N") && line.contains("nos responsabilizamos")) return true;
    if (line.startsWith("Asseguramos")) return true;
    if (line.startsWith("O saldo l")) return true;
    if (line.contains("VALORES EM R$")) return true;
    if (line.contains("CPF Ag")) return true;
    if (line.matches("\\d{6,}-\\d")) return true;
    if (holderName != null && line.trim().equals(holderName.trim())) return true;
    return false;
  }

  private boolean isRdb(String line) {
    return line.startsWith("Resgate RDB") || line.matches("Aplica[^a-z]?[^a-z]?o RDB.*");
  }

  private boolean isPagamentoFatura(String line) {
    return line.startsWith("Pagamento de fatura");
  }

  private boolean isCreditoConta(String line) {
    return line.matches("Cr.dito em conta.*");
  }

  private BigDecimal extractTrailing(String line) {
    Matcher m = TRAILING_DECIMAL.matcher(line);
    if (m.find()) return parseBrazilian(m.group(1));
    return null;
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
