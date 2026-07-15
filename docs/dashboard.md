# Dashboard

Visão mensal 50/30/20: receita real, despesas por grupo, investimentos, saldo e destaques. O período vem do seletor de mês global.

## Backend — `DashboardService.getMonthly(user, year, month)`

Retorna `DashboardResponse`. Cálculos (mês corrente do usuário). **As agregações usam a competência (`COALESCE(competence_date, date)`), não a data da compra** — compras de cartão contam no mês de pagamento da fatura (regime de caixa). Ver [transacoes.md](./transacoes.md#regras-de-domínio).

| Métrica                                              | Cálculo                                                                                                              |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `entradas`                                           | soma de `type=INCOME` (exclui `ignored`) — "Entradas do mês / Receita disponível"                                    |
| `entradasBreakdown`                                  | **"De onde veio o dinheiro"**: entradas do mês agrupadas por **categoria** (`findIncomeWithCategoryInPeriod`, maior→menor, com "Sem categoria"). Receita agora pode ter categoria (ver [transacoes.md](./transacoes.md)); usa o valor bruto (como `entradas`). Drill-down expansível sob o card de Entradas |
| `despesasEssenciais` / `despesasNaoEssenciais`       | soma de `type=EXPENSE` por `budget_group` (usa `userShare` quando `shared`, exclui `ignored`)                        |
| `totalDespesas`                                      | **todas** as despesas não-ignoradas do mês (`sumAllExpenseByUserIdAndDateBetween`, com `userShare`) — não só as com grupo 50/30/20 |
| `despesasSemGrupo`                                   | `totalDespesas − essenciais − naoEssenciais` — despesas fora do 50/30/20 (ex.: pagamento de fatura contabilizado no mês de transição). Card "Outras despesas (sem grupo)" só quando > 0 |
| `aplicado`                                           | **aporte líquido**: `INVESTMENT/CONTRIBUTION` − `INVESTMENT/REDEMPTION` — "Aplicado em investimentos"                |
| `resgatado`                                          | soma de `INVESTMENT/REDEMPTION` — "Resgatado dos investimentos"                                                      |
| `resultado`                                          | entradas − totalDespesas — "Resultado do mês" (performance **isolada** do mês; agora conta toda despesa não-ignorada) |
| `saldoAcumulado`                                     | **Saldo Geral (em conta)**: `sumAccumulatedBalanceUntil` = Σ receitas − Σ despesas − aportes + resgates (não-ignorados, competência `<=` fim do mês). Card ao lado do Resultado; dá o contexto que o mês isolado não mostra |
| `pagamentosFaturaIgnorados`                          | soma dos "Pagamento de fatura" **ainda ignorados** no mês (`sumIgnoredBillPaymentsInPeriod`). Quando > 0, o Dashboard mostra um aviso — sinal do **furo de transição** (um pagamento ignorado que ainda existe = não foi conciliado a nenhuma fatura) |
| `rendaBase`                                          | base do 50/30/20: `entradas` do mês; **se 0, cai para `monthlyNetIncome`** do usuário                               |
| `percentual{Essenciais,NaoEssenciais,Investimentos}` | cada grupo / `rendaBase` (investimentos usa `aplicado` líquido)                                                     |
| `breakdown`                                          | **drill-down do 50/30/20**: `essenciais` e `naoEssenciais` — categorias que compõem cada bucket de despesa (`List<CategoryTotalResponse>`, maior→menor, com bucket "Sem categoria"). Reusa `findExpensesWithCategoryInPeriod` (uma única busca, compartilhada com `destaques`) agrupando por `budget_group`→categoria. Investimentos não têm categoria; o detalhe do 20% usa `aportes`/`resgatado`. |
| `destaques`                                          | maior supermercado/delivery (via `subcategory` das regras), qtd assinaturas, qtd compras, qtd PIX enviados/recebidos |

> **Meta em R$ + folga/estouro** são calculados no **frontend** a partir de `rendaBase`: meta = 50%/30%/20% × base; folga/estouro = meta − realizado. Essenciais/não-essenciais são **teto** (acima = estouro); investimentos são **piso** (abaixo = "faltam R$ X para os 20%"). Sem novo backend para isso.

> A Home segue os 4 blocos da visão de produto: **quanto entrou / para onde foi / está seguindo o 50-30-20 / quanto sobrou**. `reembolso` deixou de existir (vira `INCOME`).

### Resultado do mês × Saldo Geral, e o furo de transição

O regime de caixa faz o **Resultado do mês** oscilar (salário num mês, fatura no mês do pagamento). O **Saldo Geral (acumulado)** suaviza isso mostrando o saldo corrido em conta até o fim do mês — o negativo de um mês aparece coberto pelo acumulado do anterior.

No **mês de início do uso** há um "furo": o "Pagamento de fatura" do extrato entra `ignored=true` (para não duplicar com os itens da fatura), mas se a fatura daquele período nunca foi importada, aquela saída real não conta em lugar nenhum → Resultado/Saldo inflados. O tratamento é **enxuto** (sem status novo): o Dashboard **avisa** quando há `pagamentosFaturaIgnorados > 0`, e na tela de Transações o usuário **des-ignora** o pagamento ("Contabilizar neste mês"). Como `resultado`/`saldoAcumulado` contam **toda** despesa não-ignorada, des-ignorar já faz a saída entrar — sem precisar de grupo 50/30/20 (ela aparece em "Outras despesas (sem grupo)"). Ver [transacoes.md](./transacoes.md).

Queries em `TransactionRepository` (`sumIncomeByUserIdAndDateBetween`, `sumInvestmentByDirectionAndDateBetween`, `sumExpenseByBudgetGroupAndDateBetween`, `findExpensesWithCategoryInPeriod`, `count*InPeriod`) — todas excluem `ignored`.

### Endpoint — `DashboardController`

- `GET /api/dashboard/monthly?year=&month=` → `DashboardResponse`.

## Frontend

- **`feature/dashboard`** (`app-dashboard`) — injeta `DashboardService` + `PeriodService`.
  - **Recarrega ao mudar o mês global:** `effect(() => { period.period(); load(); })`.
  - `load()` → `dashboard.service.getMonthly(year, month)`. Helpers: `fmt()` (BRL), `clamp()` (barra 50/30/20 limitada a 100), `hasBase()`, `baseLabel()` ("renda do mês" vs "salário configurado").
  - Quando não há base de renda, os percentuais mostram "—" com aviso; percentual > 100% em vermelho.
  - **50/30/20 preciso:** por bucket mostra a **meta em R$** (`meta(fração)`) e a **folga/estouro** (`folga(realizado, fração)`); cada bucket **expande** (`toggle('ess'|'nao'|'inv')`) para listar as categorias que o compõem (do `breakdown`) com **% do bucket** (`bucketPct(total, bucketTotal)`). O bucket de investimentos expande para aportes/resgates brutos.
- **Seletor de mês global** — `layout/month-selector` (`app-month-selector`) + `PeriodService` (`core/services/`): estado `signal<{year,month}>` persistido em `localStorage` (`selected_period`); `monthString()`, `label()`, `prev()`/`next()`, `isCurrentMonth()`/`goToCurrent()` (botão "mês atual"). É o **único controle** que dispara os `effect()` do dashboard e da lista de transações.

## Fluxo ponta-a-ponta

Usuário troca o mês na toolbar → `PeriodService.set()` → `effect()` do dashboard recarrega → `getMonthly` recalcula. O salário líquido (Configurações, ver [autenticacao-e-usuarios.md](./autenticacao-e-usuarios.md)) entra como `rendaBase` quando o mês não tem renda registrada.

## Onde mexer

- Nova métrica → `DashboardService.getMonthly` (+ query no repo se preciso), `DashboardResponse`, `dashboard.component`.
- Mudar a base do 50/30/20 → lógica de `rendaBase` em `getMonthly`; meta em R$/folga são derivadas no `dashboard.component` (`meta`/`folga`).
- Drill-down do 50/30/20 → `buildBudgetBreakdown`/`categoriesForGroup` (backend) + `DashboardResponse.Breakdown` + template (`toggle`/`bucketPct`).
- Saldo Geral → `sumAccumulatedBalanceUntil` (query `CASE` com sinal) + `saldoAcumulado` + card `accumulated`.
- Furo de transição → `sumIgnoredBillPaymentsInPeriod` + `pagamentosFaturaIgnorados` + aviso; `sumAllExpenseByUserIdAndDateBetween` alimenta `totalDespesas`/`resultado`/`despesasSemGrupo`.
- Novo destaque → `buildDestaques` + `Destaques` + template.

## Testes relevantes

`DashboardServiceTest` (entradas/totais/resultado, percentuais, aporte líquido = aporte − resgate, renda zero, precedência/fallback do salário líquido, breakdown do 50/30/20 por categoria maior→menor com "Sem categoria", **saldoAcumulado + despesasSemGrupo + pagamentosFaturaIgnorados**).
