# Metas de orçamento

Teto de gasto **por categoria**, recorrente mensal (mesma meta todo mês). O acompanhamento (gasto vs meta) é calculado para o mês selecionado no seletor global.

## Backend

### Entidade / migration

- **`V10__budget_goals.sql`**: tabela `budget_goals` (`user_id`, `category_id`, `amount > 0`), UNIQUE `(user_id, category_id)` — uma meta por categoria por usuário. FKs com `ON DELETE CASCADE`.
- **`BudgetGoal`** (`budget_goals`): `@ManyToOne` user/category, `amount`, timestamps.

### `BudgetGoalService`

- `findAll(userId, year, month)` — lista as metas e calcula o **progresso do mês**: `spent` = gasto do mês na categoria (`sumExpenseByCategoryIdsAndDateBetween`, usa `userShare` quando `shared`), **líquido de reembolsos** (um `INCOME reimbursement=true` na categoria entra como `−amount`) e com piso em zero (`.max(0)` — reembolso maior que o gasto não vira "gasto negativo"), `remaining` = amount − spent (pode ser negativo), `percentage` = spent / amount × 100.
- `create` — valida categoria e **rejeita meta duplicada** para a mesma categoria (UNIQUE); progresso calculado no mês corrente.
- `update` — altera só o `amount` (a categoria é fixa); posse validada.
- `delete` — posse validada (`AccessDeniedException` → 403).
- `bulkUpsert(items, user)` — cria-ou-atualiza várias metas de uma vez (usado por "Aplicar metas sugeridas"): para cada `{categoryId, amount}`, atualiza a meta existente da categoria ou cria uma nova.
- **`suggest(user, year, month)`** — motor de sugestão **50/30/20** (determinístico, sem IA). Base da renda = salário configurado (`monthlyNetIncome`), senão a **mediana** da renda mensal dos últimos 3 meses. Para cada categoria de despesa dos **últimos 3 meses** (`findExpensesWithCategoryInPeriod`), estima o gasto pela **mediana das somas mensais** (categoria presente em ≤1 mês → mediana 0 → descartada), coloca-a na sua **faixa dominante** (grupo 50/30 onde mais gastou) e, quando o total histórico da faixa **estoura o teto** (50% / 30% da base), **reduz proporcionalmente** as metas para caberem no teto. Valores **arredondados para R$10**. O piso de 20% vira uma **meta única de aporte mensal** (investimentos não têm categoria). Marca `hasGoal` quando a categoria já tem meta (será atualizada).

### Endpoints — `BudgetGoalController` (`/api/budget-goals`)

- `GET /?year=&month=` → `List<BudgetGoalResponse>` (progresso do mês; sem params usa o mês corrente).
- `GET /suggestions?year=&month=` → `BudgetSuggestionResponse` (sugestão 50/30/20 a partir do histórico).
- `POST /` — `CreateBudgetGoalRequest {categoryId, amount(@DecimalMin 0.01)}` (201).
- `POST /bulk` — `BulkBudgetGoalRequest {goals: [{categoryId, amount}]}` → cria/atualiza em lote e devolve `List<BudgetGoalResponse>`.
- `PUT /{id}` — atualiza o valor.
- `DELETE /{id}` (204).

`BudgetGoalResponse`: `id, categoryId, categoryName, categoryIcon, categoryColor, amount, spent, remaining, percentage`.

`BudgetSuggestionResponse`: `rendaBase`, `buckets: [{group, cap, historicalTotal, suggestedTotal, overCap, categories: [{categoryId, categoryName, categoryIcon, categoryColor, historicalMonthly, suggestedAmount, hasGoal}]}]`, `investimentos: {cap, historicalMonthly, suggestedAmount}`.

## Frontend (`feature/budget-goals/`, `budget-goal.service`)

`budget-goal.service`: `getAll(year, month)` → `GET /api/budget-goals?year=&month=`; `create`/`update`/`delete`; `suggestions(year, month)` → `GET /suggestions`; `bulkUpsert(goals)` → `POST /bulk`.

- **`budget-goal-list`** (`app-budget-goal-list`) — página `/budget-goals`. **Reage ao mês global** via `effect(() => { period.period(); load(); })`. Cada meta é um card com barra de progresso (verde <80%, âmbar 80–100%, vermelho >100% via `barColor`), gasto vs meta e "resta/excedeu". Criar/editar via dialog; excluir via `ConfirmDialogComponent`. Botão **"Sugerir metas"** abre o `budget-suggestion-dialog`.
- **`budget-goal-form-dialog`** (`app-budget-goal-form-dialog`) — template inline. Usa `CategorySelectComponent` (na criação, filtra categorias que já têm meta) e `CurrencyMaskDirective` (valor). Na edição, a categoria é fixa (só muda o valor).
- **`budget-suggestion-dialog`** (`app-budget-suggestion-dialog`) — consome `suggestions()`, mostra as metas propostas **agrupadas por faixa** (Essenciais/Não essenciais) com checkbox + valor editável por categoria e o **total selecionado vs teto** colorido (vermelho quando passa); investimentos aparecem como meta de aporte informativa (sem categoria). "Aplicar" chama `bulkUpsert` e recarrega. Vazio quando não há base de renda ou histórico suficiente.
- Rota em `app.routes.ts` (filha do shell) e item **"Metas"** (ícone `savings`) no `navItems` do `LayoutComponent`.

## Fluxo ponta-a-ponta

Usuário define meta por categoria → ao abrir a tela (ou trocar o mês global) → `getAll(year, month)` calcula o gasto do mês por categoria → barras mostram o progresso. Não há bloqueio: estourar a meta só destaca em vermelho.

## Regras de domínio

- **Uma meta por categoria** (UNIQUE) — a criação impede duplicar.
- Meta é **recorrente** (não há coluna de mês); o progresso é sempre relativo ao mês consultado. Para metas específicas por mês, seria preciso evoluir o modelo.
- `spent` respeita rateio (`userShare` quando `shared`), como o dashboard, e é **líquido de reembolsos** (contra-lançamentos da categoria abatem o gasto; piso em zero). Ver [transacoes.md](./transacoes.md).

## Onde mexer

- Meta por mês específico → adicionar `year_month` à tabela/entidade e ao filtro de `findAll`.
- Meta por grupo 50/30/20 (além de categoria) → nova dimensão em `BudgetGoal` + query por `budgetGroup`.
- ~~Resumo no dashboard~~ → **feito nos Relatórios**: o `reports.component` consome `GET /api/budget-goals?year=&month=` e cruza por `categoryId` com o "Gasto por categoria", destacando categorias que estouraram o teto (`remaining < 0`). Ver [relatorios.md](./relatorios.md#integração-com-as-metas-gargalo--teto).

## Testes relevantes

`BudgetGoalServiceTest` (cálculo de progresso, percentual > 100 ao estourar, rejeita meta duplicada, bloqueia exclusão de outro usuário, **roll-up de subcategorias**, **sugestão 50/30/20** monta as faixas e reduz a faixa que estoura o teto, **ignora categoria esporádica** (≤1 mês), **`bulkUpsert`** cria novas e atualiza existentes).
