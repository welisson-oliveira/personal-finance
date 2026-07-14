package com.personalfinance.dto.request;

import com.personalfinance.dto.response.ParsedTransactionDTO;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Body of the confirm-import endpoint. Besides the (possibly edited) transactions, it carries the
 * user's reconciliation decisions:
 *
 * <ul>
 *   <li>FATURA import: {@code reconcileExtratoPaymentIds} = extrato "Pagamento de fatura"
 *       transactions to delete (replaced by the fatura's items).
 *   <li>EXTRATO import: the decision travels on each {@link ParsedTransactionDTO#isReconciled()} —
 *       reconciled bill payments are skipped (not persisted).
 * </ul>
 *
 * When {@code reconcileExtratoPaymentIds} is {@code null} the server falls back to automatic
 * value-based reconciliation (legacy behaviour).
 */
@Data
public class ConfirmImportRequest {
  private List<ParsedTransactionDTO> transactions;
  private List<UUID> reconcileExtratoPaymentIds;
}
