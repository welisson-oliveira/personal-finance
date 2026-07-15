# Relatórios

Relatórios visuais (gráficos em SVG/CSS, sem dependência externa): **evolução mensal** (receita × despesa dos últimos meses), **onde vai seu dinheiro** (gasto por categoria com % do total, variação vs mês anterior e cruzamento com as metas) e **maiores gastos do mês**. É a tela para achar o **gargalo de dinheiro** (Dashboard = foto de saúde do mês / 50-30-20; Relatórios = tendência no tempo + ranking).

## Backend

### `ReportService`

- `monthlyEvolution(userId, months)` — para os últimos `months` meses (clamp 1–24): `receita` = `type=INCOME` (exclui `ignored`); `despesa` = `budget_group` ESSENTIAL + NON_ESSENTIAL; `saldo` = receita − despesa. Reaproveita as somas do `DashboardService`.
- `categoryBreakdown(userId, year, month)` — agrupa as despesas do mês por categoria (`findExpensesWithCategoryInPeriod`), usando `userShare` quando `shared` (`effectiveAmount`), ordenado do maior para o menor, com bucket "Sem categoria". **Também computa o mês anterior** (`totalsForMonth(ym.minusMonths(1))`) e casa por `categoryId`: cada linha carrega `previousTotal` e `deltaPercent` (variação mês a mês; `null` quando o mês anterior foi 0).
- `topExpenses(userId, year, month, limit)` — os `limit` maiores **lançamentos individuais** do mês (reusa `findExpensesWithCategoryInPeriod`, ordena por `effectiveAmount` desc), cada um com descrição, categoria (nome+cor), data e valor.

### Endpoints — `ReportController` (`/api/reports`)

- `GET /monthly-evolution?months=6` → `List<MonthlyPointResponse {year, month, receita, despesa, saldo}>`.
- `GET /category-breakdown?year=&month=` → `List<CategoryTotalResponse {categoryId, categoryName, categoryIcon, categoryColor, total, previousTotal, deltaPercent}>` (sem params usa o mês corrente).
- `GET /top-expenses?year=&month=&limit=10` → `List<TopExpenseResponse {id, description, categoryName, categoryColor, date, amount}>`.

Sem entidade/migration nova — só agregação de leitura sobre `transactions`. O **% do total** por categoria é derivado no frontend.

### Integração com as Metas (gargalo × teto)

O cruzamento com **Metas de orçamento** (ver [metas-de-orcamento.md](./metas-de-orcamento.md)) é **merge no frontend**: os Relatórios chamam `GET /api/budget-goals?year=&month=` (que já devolve `amount`/`spent`/`remaining`/`percentage` por categoria) e juntam por `categoryId`. Uma categoria com `remaining < 0` **estourou a meta** e é destacada como gargalo. Sem backend novo.

## Frontend (`feature/reports/`, `report.service`)

`report.service`: `monthlyEvolution(months)`, `categoryBreakdown(year, month)` e `topExpenses(year, month, limit)`.

- **`reports`** (`app-reports`) — página `/reports`. Injeta `ReportService`, `BudgetGoalService` e `PeriodService`.
  - **Evolução mensal:** barras agrupadas (receita verde × despesa vermelha) por mês + saldo abaixo de cada mês. É **independente** do seletor global (mostra sempre os últimos 6 meses); escala pelas maiores barras.
  - **Onde vai seu dinheiro:** barras horizontais na cor da categoria + **% do total** (`pctOfTotal`), **Δ vs mês anterior** (`deltaPercent`: ▲ vermelho quando sobe / ▼ verde quando cai) e, quando há meta, `meta R$` com selo **"estourou a meta"** (`isOverGoal`/`goalFor`). **Segue o mês global** via `effect(() => { period.period(); loadBreakdown(); loadGoals(); loadTopExpenses(); })`.
  - **Maiores gastos do mês:** ranking dos 10 maiores lançamentos (posição, descrição, categoria, data e valor). Segue o mês global.
  - Gráficos são `div`/CSS puros (altura/largura proporcionais), sem lib de chart.
- Rota em `app.routes.ts` e item **"Relatórios"** (ícone `insights`) no `navItems` do `LayoutComponent` (logo após o Dashboard).

## Onde mexer

- Novo relatório → método no `ReportService` + endpoint no `ReportController` + card no `reports.component`.
- Mudar a janela da evolução → `months` no `reports.component` (o backend faz clamp 1–24).
- Mais/menos linhas no ranking → `limit` em `loadTopExpenses` (`report.service.topExpenses`).
- Cruzar outra dimensão das Metas → `goalsByCategory` no `reports.component` (merge por `categoryId`).
- Trocar por biblioteca de gráficos → hoje é SVG/CSS; um chart lib exigiria nova dependência (o projeto evita).

## Testes relevantes

`ReportServiceTest` (evolução: um ponto por mês + saldo, clamp 1–24; breakdown: agrupa/ordena e usa `userShare`; **delta mês a mês e `null` sem base anterior**; **`topExpenses` ordena por valor efetivo desc e respeita `limit`**).
