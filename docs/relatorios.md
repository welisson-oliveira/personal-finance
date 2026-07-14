# Relatórios

Dois relatórios visuais (gráficos em SVG/CSS, sem dependência externa): **evolução mensal** (receita × despesa dos últimos meses) e **gasto por categoria** no mês selecionado.

## Backend

### `ReportService`

- `monthlyEvolution(userId, months)` — para os últimos `months` meses (clamp 1–24): `receita` = `type=INCOME` (exclui `ignored`); `despesa` = `budget_group` ESSENTIAL + NON_ESSENTIAL; `saldo` = receita − despesa. Reaproveita as somas do `DashboardService`.
- `categoryBreakdown(userId, year, month)` — agrupa as despesas do mês por categoria (`findExpensesWithCategoryInPeriod`), usando `userShare` quando `shared` (`effectiveAmount`), ordenado do maior para o menor.

### Endpoints — `ReportController` (`/api/reports`)

- `GET /monthly-evolution?months=6` → `List<MonthlyPointResponse {year, month, receita, despesa, saldo}>`.
- `GET /category-breakdown?year=&month=` → `List<CategoryTotalResponse {categoryId, categoryName, categoryIcon, categoryColor, total}>` (sem params usa o mês corrente).

Sem entidade/migration nova — só agregação de leitura sobre `transactions`.

## Frontend (`feature/reports/`, `report.service`)

`report.service`: `monthlyEvolution(months)` e `categoryBreakdown(year, month)`.

- **`reports`** (`app-reports`) — página `/reports`.
  - **Evolução mensal:** barras agrupadas (receita verde × despesa vermelha) por mês + saldo abaixo de cada mês. É **independente** do seletor global (mostra sempre os últimos 6 meses); escala pelas maiores barras.
  - **Gasto por categoria:** barras horizontais na cor da categoria, **seguindo o mês global** via `effect(() => { period.period(); loadBreakdown(); })`.
  - Gráficos são `div`/CSS puros (altura/largura proporcionais), sem lib de chart.
- Rota em `app.routes.ts` e item **"Relatórios"** (ícone `insights`) no `navItems` do `LayoutComponent` (logo após o Dashboard).

## Onde mexer

- Novo relatório → método no `ReportService` + endpoint no `ReportController` + card no `reports.component`.
- Mudar a janela da evolução → `months` no `reports.component` (o backend faz clamp 1–24).
- Trocar por biblioteca de gráficos → hoje é SVG/CSS; um chart lib exigiria nova dependência (o projeto evita).

## Testes relevantes

`ReportServiceTest` (evolução: um ponto por mês + saldo, clamp 1–24; breakdown: agrupa/ordena e usa `userShare`).
