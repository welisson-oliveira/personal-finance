-- Reset de importações (dev)
-- Remove: transactions, import_sessions
-- Preserva: categories, merchant_rules, merchant_display_names,
--            budget_goals, known_persons, users
--
-- Execute via: scripts/dev-reset-imports.sh

BEGIN;

-- 1. Transações (referencia import_sessions e categories — mantemos categories)
DELETE FROM transactions;

-- 2. Sessões de importação
DELETE FROM import_sessions;

-- 3. Resetar saldo inicial para reconfiguração após novo import
UPDATE users
SET opening_balance      = NULL,
    opening_balance_date = NULL;

COMMIT;

-- Resumo do que ficou
SELECT 'categories'             AS tabela, COUNT(*) AS registros FROM categories
UNION ALL
SELECT 'merchant_rules',                   COUNT(*)              FROM merchant_rules
UNION ALL
SELECT 'merchant_display_names',           COUNT(*)              FROM merchant_display_names
UNION ALL
SELECT 'budget_goals',                     COUNT(*)              FROM budget_goals
UNION ALL
SELECT 'known_persons',                    COUNT(*)              FROM known_persons
UNION ALL
SELECT 'users',                            COUNT(*)              FROM users
UNION ALL
SELECT 'transactions (deve ser 0)',        COUNT(*)              FROM transactions
UNION ALL
SELECT 'import_sessions (deve ser 0)',     COUNT(*)              FROM import_sessions
ORDER BY tabela;
