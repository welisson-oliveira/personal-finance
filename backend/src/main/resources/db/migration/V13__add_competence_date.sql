-- Cash-regime "competence date": the month a transaction should hit the Dashboard/Reports.
-- For credit-card purchases that is the fatura's payment (due) month, not the purchase month.
-- Pix/débito/manual leave it NULL and fall back to `date` via COALESCE in the aggregation queries.

ALTER TABLE transactions ADD COLUMN competence_date DATE;

-- Backfill historical fatura purchases to their estimated payment month. The exact due date of
-- old faturas was not stored; the vencimento lands roughly 10 days after the statement closes
-- (import_sessions.period_end), which is enough to place them in the correct payment month.
UPDATE transactions t
SET competence_date = (s.period_end + INTERVAL '10 days')::date
FROM import_sessions s
WHERE t.import_session_id = s.id
  AND t.source = 'FATURA'
  AND s.period_end IS NOT NULL;

CREATE INDEX idx_transactions_competence_date ON transactions (competence_date);
