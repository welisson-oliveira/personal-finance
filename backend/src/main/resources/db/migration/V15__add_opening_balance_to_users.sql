-- Opening balance so the "Saldo Geral" can reflect the real account balance, not just the net of
-- transactions since the user started using the app. Both are optional; when set, the accumulated
-- balance = opening_balance + movements from opening_balance_date onward.

ALTER TABLE users ADD COLUMN opening_balance NUMERIC(19, 2);
ALTER TABLE users ADD COLUMN opening_balance_date DATE;
