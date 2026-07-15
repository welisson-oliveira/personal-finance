-- Turn merchant rules into full user overrides: besides category + expense_type they can now carry
-- the transaction type, the ignored flag and the investment direction. A user correction on the
-- Transactions screen is learned as such a rule and, on the next import, overrides the heuristics
-- (own-transfer, investment, etc.) for that merchant.

ALTER TABLE merchant_rules ADD COLUMN type VARCHAR(20);
ALTER TABLE merchant_rules ADD COLUMN ignored BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE merchant_rules ADD COLUMN investment_direction VARCHAR(20);
