-- Reembolso (contra-lançamento): uma ENTRADA marcada como reembolso não é receita — ela ABATE o
-- gasto da sua categoria/grupo (ex.: moradores devolvendo a parte deles da conta de luz tagueada
-- como "Contas"). Some das receitas (entradas/base 50-30-20) e entra como despesa negativa nas
-- agregações de gasto, deixando Dashboard, Relatórios e Metas coerentes.
ALTER TABLE transactions ADD COLUMN reimbursement BOOLEAN NOT NULL DEFAULT false;

-- A regra de merchant lembra que aquele remetente é reembolso, para auto-marcar nas próximas
-- importações (mesmo padrão de type/ignored/categoria).
ALTER TABLE merchant_rules ADD COLUMN reimbursement BOOLEAN NOT NULL DEFAULT false;
