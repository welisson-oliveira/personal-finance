-- Remove a tabela legada `review_queue`. A fila de revisão foi substituída pela revisão inline
-- (flag `needs_review` na própria transação) — não há mais entidade nem repositório que a use,
-- então a tabela só ocupava espaço e confundia o script de reset. Criada em V1 e alterada em V9.
DROP TABLE IF EXISTS review_queue;
