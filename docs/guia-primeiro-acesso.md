# Guia: Primeiro acesso — importando histórico sem saber o saldo inicial

## Cenário

Você está começando a usar o sistema hoje, mas quer importar dados a partir de um mês anterior (ex.: maio). Você só sabe quanto tem na conta **agora**.

---

## Passo a passo

### 1. Configurar o ponto zero temporário

Vá em **Configurações** e defina:

| Campo | Valor |
|---|---|
| Saldo inicial | `R$ 0,00` |
| Data de referência | `01/05/2026` (primeiro dia do mês mais antigo que você vai importar) |

> O sistema vai contar todas as movimentações a partir dessa data.

---

### 2. Importar tudo, da data mais antiga para a mais recente

Siga essa ordem:

1. **Extrato de maio** (`.pdf` do Nubank extrato)
2. **Fatura com vencimento em junho** (compras de maio)
3. **Extrato de junho**
4. **Fatura com vencimento em julho** (compras de junho)
5. **Extrato de julho** (até hoje)

> **Por que essa ordem?** A fatura cobre compras do mês anterior. O extrato de cada mês tem o pagamento da fatura do mês anterior. Importar em ordem cronológica evita confusão na tela de importação.

---

### 3. Calcular o saldo real de 1º de maio

Após importar tudo, abra o **Dashboard** no mês de **julho** e anote o valor do card **Saldo Geral**.

O sistema mostra esse valor partindo do zero, portanto:

```
Saldo em 01/05 = Saldo real hoje − Saldo Geral mostrado pelo sistema
```

**Exemplo:**
- Hoje você tem **R$ 12.000** na conta
- O sistema mostra **R$ 4.300** no Saldo Geral
- Saldo em 1º de maio = **R$ 12.000 − R$ 4.300 = R$ 7.700**

> Se o resultado for negativo, significa que você tinha menos dinheiro em maio do que a soma das movimentações líquidas desde lá — isso é normal se houve aportes em investimentos ou entradas não importadas.

---

### 4. Corrigir o saldo inicial nas configurações

Volte em **Configurações** e atualize:

| Campo | Valor |
|---|---|
| Saldo inicial | `R$ 7.700` (o valor calculado no passo anterior) |
| Data de referência | `01/05/2026` (mantém) |

---

### 5. Verificar

Abra o Dashboard de **julho**. O Saldo Geral deve bater com o que você tem na conta hoje.

Navegue para **maio** e **junho** — os saldos históricos também estarão corretos.

---

## O que o sistema preserva ao resetar importações

Se você precisar recomeçar as importações do zero (ex.: erro, duplicata), o script `scripts/dev-reset-imports.sh` apaga apenas os dados de importação, preservando:

| O que fica | Por quê |
|---|---|
| Categorias | Você criou e organizou; perder daria retrabalho |
| Regras de merchant | Inteligência de classificação automática acumulada |
| Apelidos de estabelecimentos | Nomes amigáveis que você definiu |
| Metas de orçamento | Configuração 50/30/20 |
| Pessoas conhecidas | Contatos para divisão de despesas |
| Usuários | Acesso e configurações do perfil |

| O que é removido | Por quê |
|---|---|
| Transações | São os dados importados — recomeço limpo. As pendências de **revisão** vão junto: hoje a revisão é **inline** (flag `needs_review` na própria transação), não uma fila separada |
| Sessões de importação | Histórico de arquivos importados |
| Saldo inicial | Deve ser reconfigurado após o novo import |

> A tabela `review_queue` (criada nas migrations V1/V9) é **legada** — a fila de revisão foi substituída pela revisão inline e nenhuma entidade/repositório a usa mais. O script ainda faz `DELETE FROM review_queue` por segurança, mas ela fica sempre vazia.

---

## Notas importantes sobre a fatura

- Compras feitas em maio com vencimento em junho aparecem no **mês de junho** no Dashboard (data de competência = vencimento da fatura)
- Isso é correto: o dinheiro saiu da sua conta em junho, não em maio
- O extrato de maio terá o **pagamento da fatura de abril** — que deve ficar como `ignored = true` para não duplicar com os itens da fatura de abril
