package com.personalfinance.service.parser;

import org.springframework.stereotype.Component;

@Component
public class DocumentTypeDetector {

  public String detect(String pdfText) {
    String upper = pdfText.toUpperCase();

    if (upper.contains("PERÍODO VIGENTE")
        || upper.contains("PERIODO VIGENTE")
        || upper.contains("DATA DE VENCIMENTO")
        || upper.contains("FATURA DE CRÉDITO")
        || upper.contains("FATURA NUBANK")) {
      return "FATURA";
    }

    if (upper.contains("TOTAL DE ENTRADAS")
        || upper.contains("TOTAL DE SAÍDAS")
        || upper.contains("TOTAL DE SAIDAS")
        || upper.contains("CONTA DE PAGAMENTO")
        || upper.contains("CONTA CORRENTE")) {
      return "EXTRATO";
    }

    throw new IllegalArgumentException(
        "Não foi possível detectar o tipo de documento automaticamente. "
            + "Verifique se é um extrato ou fatura do Nubank.");
  }
}
