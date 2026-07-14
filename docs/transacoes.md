# Transações

Listar, filtrar, editar, renomear (apelido) e excluir transações. Editar uma transação propaga a classificação para as iguais e ensina o sistema.

## Backend

### `TransactionService`

- `findAll(userId, month, type, categoryId, needsReview, pageable)` — paginado via **Specifications** (`TransactionSpecifications`: `forUser`, `inDateRange`, `ofType`, `inCategory`, `needingReview`, `excludingIgnored`). **Exclui `ignored`** da listagem; `needsReview=true` filtra só os pendentes de revisão.
- `create(request, user)` — transação `source=MANUAL` (inclui campos de rateio `shared`/`totalAmount`/`userShare`).
- `update(id, request, user)` — resolve a transação (limpa `needs_review`) e chama **`propagateClassification`**: copia tipo/categoria/budgetGroup/investmentDirection/ignored para todas as transações de mesmo nome efetivo (e limpa o `needs_review` delas) e, para despesa, faz upsert de `MerchantRule` USER (aprende para o futuro). Isso substitui a antiga resolução da fila de revisão. Valor, data e descrição **não** se propagam (são por transação).
- `updateNotes(id, user, notes)` — upsert/delete em `merchant_display_names` e propaga o apelido a todas as transações do mesmo nome efetivo.
- `delete(id, user)` — com checagem de posse (`AccessDeniedException` → 403).

### Endpoints — `TransactionController` (`/api/transactions`)

- `GET /` — paginado; filtros `month`, `type`, `categoryId`; `@PageableDefault(size=50, sort="date")`.
- `POST /` — `CreateTransactionRequest` (201).
- `PUT /{id}` — atualiza (dispara propagação + aprendizado).
- `PATCH /{id}/notes` — `UpdateNotesRequest {notes}` (apelido, propaga).
- `DELETE /{id}` (204).

### Entidade — `Transaction` (`transactions`)

description, `normalizedDescription`, amount, `type` (enum INCOME/EXPENSE/INVESTMENT), `budgetGroup`, `investmentDirection`, `ignored`, `needsReview`, date, notes; `@ManyToOne` category/importSession/knownPerson/user; source (MANUAL/EXTRATO/FATURA), cardHolder, installmentInfo; `shared`/`totalAmount`/`userShare`; timestamps.

## Frontend (`feature/transactions/`, `transaction.service`)

`transaction.service`: `findAll({month,type,categoryId,page,size})` → `GET /api/transactions`; `update(id, req)` → `PUT`; `updateNotes(id, notes)` → `PATCH /{id}/notes`; `delete(id)` → `DELETE`.

- **`transaction-list`** (`app-transaction-list`) — injeta `PeriodService`.
  - **Reage ao mês global:** `effect(() => { period.period(); pageIndex = 0; load(); })`. `ngOnInit` também honra `?month=` via `period.setFromMonthString()`.
  - Filtros `filterType`/`filterCategoryId`; paginação (`MatPaginator`, size 20).
  - **Apelido inline:** clicar na descrição entra em edição (`editingId`); Enter/salvar → `updateNotes()` + reload. O apelido substitui o nome real como rótulo principal.
  - **Revisão inline:** coluna **Classificação** com chips (tipo, grupo/categoria para despesa, direção para investimento, "Ignorada") e selo **"Revisar"** quando `needsReview` (linha destacada). Clicar abre o editor inline (`startClassEdit`) com selects de tipo/grupo/direção + `app-category-select` + "ignorar"; `saveClass()` → `update()` resolve e limpa o selo. Filtro **"só pendentes"** (`pendingOnly` → `needsReview=true`). Isto absorve a antiga tela de fila de revisão.
  - `openEditDialog(tx)` → `TransactionEditDialogComponent` para edição completa; resultado → `update()`. `confirmDelete()` via `ConfirmDialogComponent`.
- **`transaction-edit-dialog`** (`app-transaction-edit-dialog`) — usa `CurrencyMaskDirective` (valor) e `CategorySelectComponent` (categoria). **Form dinâmico por tipo:** `onTypeChange()` — EXPENSE mostra categoria+grupo, INVESTMENT mostra direção, INCOME nenhum; checkbox **"ignorar nos cálculos"**. `confirm()` devolve `UpdateTransactionRequest` preservando `notes/shared/totalAmount/userShare` do original.

## Fluxo ponta-a-ponta

Mês selecionado na toolbar → lista recarrega → usuário edita categoria/grupo/tipo → salva → propaga para as iguais (existentes) e ensina `merchant_rule` (importações futuras). Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

## Regras de domínio

- Por tipo (regra recorrente; ver glossário no [índice](./README.md)): EXPENSE tem `budget_group`/categoria, INVESTMENT tem `investment_direction`, INCOME nenhum.
- Transações `ignored` (ex.: transferência própria) não aparecem na lista (`excludingIgnored`) nem entram nos cálculos.
- Rateio: quando `shared`, o dashboard soma `userShare` (ver [dashboard.md](./dashboard.md)).

## Onde mexer

- Novo filtro na lista → `TransactionSpecifications` + `findAll` + filtros do `transaction-list`.
- Novo campo editável → `CreateTransactionRequest`/`UpdateTransactionRequest`, `TransactionService.update`, `transaction-edit-dialog`.
- Mudar regra de propagação → `TransactionService.propagateClassification`.

## Testes relevantes

`TransactionServiceTest` (propaga classificação, aprende merchant rule, não aprende para receita), `TransactionControllerTest` (auth 401, create MANUAL 201, update, delete posse 204/403, filtro de mês exclui `ignored`, rateio).
