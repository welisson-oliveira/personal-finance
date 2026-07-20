-- Corrige transações de extrato cujo TIPO foi invertido por uma regra de merchant que ignorava o
-- sinal do extrato (crédito/débito). Uma regra aprendida num Pix ENVIADO ("pix joão" = despesa)
-- passava a marcar como despesa também um Pix RECEBIDO da mesma pessoa — a receita aparecia em
-- "Onde vai seu dinheiro". O texto "recebido/recebida" (dinheiro entrou) e "enviado/enviada"
-- (dinheiro saiu) é a fonte autoritativa usada pelo parser (NubankExtratoParser.resolveType);
-- aqui ele conserta o histórico já gravado.
--
-- Restrito a source='EXTRATO', onde essa redação é padrão. Só toca linhas cujo tipo CONFLITA com a
-- redação — idempotente (rodar de novo não muda nada).

-- Recebimentos gravados como despesa → volta a INCOME e limpa a classificação de despesa (receita
-- não carrega grupo 50/30/20 nem categoria de despesa).
UPDATE transactions
SET type = 'INCOME',
    budget_group = NULL,
    category_id = NULL,
    investment_direction = NULL
WHERE type = 'EXPENSE'
  AND source = 'EXTRATO'
  AND (LOWER(description) LIKE '%recebid%');

-- Envios gravados como receita → viram EXPENSE e entram para revisão (para o usuário classificar
-- categoria/grupo). Limpa direção de investimento, que não se aplica.
UPDATE transactions
SET type = 'EXPENSE',
    investment_direction = NULL,
    needs_review = true
WHERE type = 'INCOME'
  AND source = 'EXTRATO'
  AND (LOWER(description) LIKE '%enviad%');
