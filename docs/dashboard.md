# Dashboard

Visão mensal 50/30/20: receita real, despesas por grupo, investimentos, saldo e destaques. O período vem do seletor de mês global.

## Backend — `DashboardService.getMonthly(user, year, month)`

Retorna `DashboardResponse`. Cálculos (mês corrente do usuário):

| Métrica                                              | Cálculo                                                                                                              |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `receitaBruta`                                       | soma de `type=INCOME` e `income_type=INCOME`                                                                         |
| `reembolsos`                                         | soma de `income_type=REIMBURSEMENT`                                                                                  |
| `receitaReal`                                        | **hoje = `receitaBruta`** (reembolsos calculados à parte, não somados)                                               |
| `despesasEssenciais` / `despesasNaoEssenciais`       | soma de despesas por `budget_group` (usa `userShare` quando `shared`)                                                |
| `totalDespesas`                                      | essenciais + não essenciais                                                                                          |
| `investido`                                          | despesas com **`budget_group=INVESTMENT`**                                                                           |
| `resgatado`                                          | receitas com **`income_type=INVESTMENT`**                                                                            |
| `saldo`                                              | receitaReal − totalDespesas                                                                                          |
| `rendaBase`                                          | base do 50/30/20: renda real do mês; **se 0, cai para `monthlyNetIncome`** do usuário                                |
| `percentual{Essenciais,NaoEssenciais,Investimentos}` | cada grupo / `rendaBase`                                                                                             |
| `destaques`                                          | maior supermercado/delivery (via `subcategory` das regras), qtd assinaturas, qtd compras, qtd PIX enviados/recebidos |

> Atenção: `investido` (budget_group) e `resgatado` (income_type) usam eixos diferentes de propósito. `buildDestaques` filtra despesas por `incomeType IS NULL`.

Queries em `TransactionRepository` (`sumByUserIdAndTypeAndIncomeTypeAndDateBetween`, `sumExpenseByBudgetGroupAndDateBetween`, `findExpensesWithCategoryInPeriod`, `count*InPeriod`).

### Endpoint — `DashboardController`

- `GET /api/dashboard/monthly?year=&month=` → `DashboardResponse`.

## Frontend

- **`feature/dashboard`** (`app-dashboard`) — injeta `DashboardService` + `PeriodService`.
  - **Recarrega ao mudar o mês global:** `effect(() => { period.period(); load(); })`.
  - `load()` → `dashboard.service.getMonthly(year, month)`. Helpers: `fmt()` (BRL), `clamp()` (barra 50/30/20 limitada a 100), `hasBase()`, `baseLabel()` ("renda do mês" vs "salário configurado").
  - Quando não há base de renda, os percentuais mostram "—" com aviso; percentual > 100% em vermelho.
- **Seletor de mês global** — `layout/month-selector` (`app-month-selector`) + `PeriodService` (`core/services/`): estado `signal<{year,month}>` persistido em `localStorage` (`selected_period`); `monthString()`, `label()`, `prev()`/`next()`, `isCurrentMonth()`/`goToCurrent()` (botão "mês atual"). É o **único controle** que dispara os `effect()` do dashboard e da lista de transações.

## Fluxo ponta-a-ponta

Usuário troca o mês na toolbar → `PeriodService.set()` → `effect()` do dashboard recarrega → `getMonthly` recalcula. O salário líquido (Configurações, ver [autenticacao-e-usuarios.md](./autenticacao-e-usuarios.md)) entra como `rendaBase` quando o mês não tem renda registrada.

## Onde mexer

- Nova métrica → `DashboardService.getMonthly` (+ query no repo se preciso), `DashboardResponse`, `dashboard.component`.
- Mudar a base do 50/30/20 → lógica de `rendaBase` em `getMonthly`.
- Novo destaque → `buildDestaques` + `Destaques` + template.

## Testes relevantes

`DashboardServiceTest` (saldo, percentuais, investido vs resgatado, renda zero, precedência/fallback do salário líquido).
