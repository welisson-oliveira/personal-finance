# Dashboard

Visão mensal 50/30/20: receita real, despesas por grupo, investimentos, saldo e destaques. O período vem do seletor de mês global.

## Backend — `DashboardService.getMonthly(user, year, month)`

Retorna `DashboardResponse`. Cálculos (mês corrente do usuário). **As agregações usam a competência (`COALESCE(competence_date, date)`), não a data da compra** — compras de cartão contam no mês de pagamento da fatura (regime de caixa). Ver [transacoes.md](./transacoes.md#regras-de-domínio).

| Métrica                                              | Cálculo                                                                                                              |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `entradas`                                           | soma de `type=INCOME` (exclui `ignored`) — "Entradas do mês / Receita disponível"                                    |
| `despesasEssenciais` / `despesasNaoEssenciais`       | soma de `type=EXPENSE` por `budget_group` (usa `userShare` quando `shared`, exclui `ignored`)                        |
| `totalDespesas`                                      | essenciais + não essenciais                                                                                          |
| `aplicado`                                           | **aporte líquido**: `INVESTMENT/CONTRIBUTION` − `INVESTMENT/REDEMPTION` — "Aplicado em investimentos"                |
| `resgatado`                                          | soma de `INVESTMENT/REDEMPTION` — "Resgatado dos investimentos"                                                      |
| `resultado`                                          | entradas − totalDespesas — "Resultado do mês" (evita o termo "Saldo")                                                |
| `rendaBase`                                          | base do 50/30/20: `entradas` do mês; **se 0, cai para `monthlyNetIncome`** do usuário                               |
| `percentual{Essenciais,NaoEssenciais,Investimentos}` | cada grupo / `rendaBase` (investimentos usa `aplicado` líquido)                                                     |
| `destaques`                                          | maior supermercado/delivery (via `subcategory` das regras), qtd assinaturas, qtd compras, qtd PIX enviados/recebidos |

> A Home segue os 4 blocos da visão de produto: **quanto entrou / para onde foi / está seguindo o 50-30-20 / quanto sobrou**. `reembolso` deixou de existir (vira `INCOME`).

Queries em `TransactionRepository` (`sumIncomeByUserIdAndDateBetween`, `sumInvestmentByDirectionAndDateBetween`, `sumExpenseByBudgetGroupAndDateBetween`, `findExpensesWithCategoryInPeriod`, `count*InPeriod`) — todas excluem `ignored`.

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

`DashboardServiceTest` (entradas/totais/resultado, percentuais, aporte líquido = aporte − resgate, renda zero, precedência/fallback do salário líquido).
