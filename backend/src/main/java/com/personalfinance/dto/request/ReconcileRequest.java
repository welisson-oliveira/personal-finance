package com.personalfinance.dto.request;

import java.util.UUID;
import lombok.Data;

/**
 * Manually links an extrato "Pagamento de fatura" to a fatura, deleting the payment (substitute).
 */
@Data
public class ReconcileRequest {
  private UUID extratoPaymentId;
  private UUID faturaSessionId;
}
