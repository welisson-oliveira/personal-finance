-- Unify the transaction classification into three top-level types: INCOME | EXPENSE | INVESTMENT.
-- Drops the overlapping income_type axis; investments become a first-class type with a direction,
-- own-account transfers become an "ignored" flag, and reimbursement collapses into plain income.

ALTER TABLE transactions ADD COLUMN investment_direction VARCHAR(20);
ALTER TABLE transactions ADD COLUMN ignored              BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN needs_review         BOOLEAN NOT NULL DEFAULT FALSE;

-- Old CHECK constraints block the new values; drop them before remapping.
ALTER TABLE transactions DROP CONSTRAINT chk_transactions_type;
ALTER TABLE transactions DROP CONSTRAINT chk_income_type;
ALTER TABLE transactions DROP CONSTRAINT chk_budget_group;

-- Aporte (ex.: Aplicação RDB): EXPENSE + budget_group=INVESTMENT -> INVESTMENT / CONTRIBUTION.
UPDATE transactions
   SET type = 'INVESTMENT', investment_direction = 'CONTRIBUTION', budget_group = NULL
 WHERE type = 'EXPENSE' AND budget_group = 'INVESTMENT';

-- Resgate (ex.: Resgate RDB): INCOME + income_type=INVESTMENT -> INVESTMENT / REDEMPTION.
UPDATE transactions
   SET type = 'INVESTMENT', investment_direction = 'REDEMPTION'
 WHERE type = 'INCOME' AND income_type = 'INVESTMENT';

-- Transferência entre contas próprias -> receita marcada como ignorada (fora dos cálculos).
UPDATE transactions
   SET ignored = TRUE
 WHERE type = 'INCOME' AND income_type = 'OWN_TRANSFER';

-- Reembolso e demais receitas viram INCOME simples: basta descartar a coluna income_type.
ALTER TABLE transactions DROP COLUMN income_type;

-- Recreate the constraints for the new model.
ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_type
    CHECK (type IN ('INCOME', 'EXPENSE', 'INVESTMENT'));
ALTER TABLE transactions
    ADD CONSTRAINT chk_budget_group
    CHECK (budget_group IN ('ESSENTIAL', 'NON_ESSENTIAL') OR budget_group IS NULL);
ALTER TABLE transactions
    ADD CONSTRAINT chk_investment_direction
    CHECK (investment_direction IN ('CONTRIBUTION', 'REDEMPTION') OR investment_direction IS NULL);

-- Merchant rules no longer classify anything as an investment (investments come from the parser).
UPDATE merchant_rules SET expense_type = NULL WHERE expense_type = 'INVESTMENT';
ALTER TABLE merchant_rules DROP CONSTRAINT chk_merchant_expense_type;
ALTER TABLE merchant_rules
    ADD CONSTRAINT chk_merchant_expense_type
    CHECK (expense_type IN ('ESSENTIAL', 'NON_ESSENTIAL') OR expense_type IS NULL);

-- Known persons: default_income_type -> default_treatment (INCOME | IGNORE | ALWAYS_REVIEW).
ALTER TABLE known_persons DROP CONSTRAINT chk_known_income_type;
UPDATE known_persons SET default_income_type = 'IGNORE' WHERE default_income_type = 'OWN_TRANSFER';
UPDATE known_persons SET default_income_type = 'INCOME' WHERE default_income_type = 'REIMBURSEMENT';
ALTER TABLE known_persons ALTER COLUMN default_income_type SET DEFAULT 'INCOME';
ALTER TABLE known_persons RENAME COLUMN default_income_type TO default_treatment;
ALTER TABLE known_persons
    ADD CONSTRAINT chk_known_treatment
    CHECK (default_treatment IN ('INCOME', 'IGNORE', 'ALWAYS_REVIEW'));
