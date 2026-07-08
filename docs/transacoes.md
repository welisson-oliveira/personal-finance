# Transações

Listar, filtrar, editar, renomear (apelido) e excluir transações. Editar uma transação propaga a classificação para as iguais e ensina o sistema.

## Backend

### `TransactionService`

- `findAll(userId, month, type, categoryId, pageable)` — paginado via **Specifications** (`TransactionSpecifications`: `forUser`, `inDateRange`, `ofType`, `inCategory`, `excludingOwnTransfer`). **Exclui OWN_TRANSFER** da listagem.
- `create(request, user)` — transação `source=MANUAL` (inclui campos de rateio `shared`/`totalAmount`/`userShare`).
- `update(id, request, user)` — atualiza e chama **`propagateClassification`**: copia categoria/budgetGroup/incomeType para todas as transações de mesmo nome efetivo e, para despesa, faz upsert de `MerchantRule` USER (aprende para o futuro). Valor, data, descrição e tipo **não** se propagam (são por transação).
- `updateNotes(id, user, notes)` — upsert/delete em `merchant_display_names` e propaga o apelido a todas as transações do mesmo nome efetivo.
- `delete(id, user)` — com checagem de posse (`AccessDeniedException` → 403).

### Endpoints — `TransactionController` (`/api/transactions`)

- `GET /` — paginado; filtros `month`, `type`, `categoryId`; `@PageableDefault(size=50, sort="date")`.
- `POST /` — `CreateTransactionRequest` (201).
- `PUT /{id}` — atualiza (dispara propagação + aprendizado).
- `PATCH /{id}/notes` — `UpdateNotesRequest {notes}` (apelido, propaga).
- `DELETE /{id}` (204).

### Entidade — `Transaction` (`transactions`)

description, `normalizedDescription`, amount, `type` (enum), `incomeType`, `budgetGroup`, date, notes; `@ManyToOne` category/importSession/knownPerson/user; source (MANUAL/EXTRATO/FATURA), cardHolder, installmentInfo; `shared`/`totalAmount`/`userShare`; timestamps.

## Frontend (`feature/transactions/`, `transaction.service`)

`transaction.service`: `findAll({month,type,categoryId,page,size})` → `GET /api/transactions`; `update(id, req)` → `PUT`; `updateNotes(id, notes)` → `PATCH /{id}/notes`; `delete(id)` → `DELETE`.

- **`transaction-list`** (`app-transaction-list`) — injeta `PeriodService`.
  - **Reage ao mês global:** `effect(() => { period.period(); pageIndex = 0; load(); })`. `ngOnInit` também honra `?month=` via `period.setFromMonthString()`.
  - Filtros `filterType`/`filterCategoryId`; paginação (`MatPaginator`, size 20).
  - **Apelido inline:** clicar na descrição entra em edição (`editingId`); Enter/salvar → `updateNotes()` + reload. O apelido substitui o nome real como rótulo principal.
  - `openEditDialog(tx)` → `TransactionEditDialogComponent`; resultado → `update()`. `confirmDelete()` via `ConfirmDialogComponent`. Chips com tooltip para incomeType e budgetGroup (colunas Entrada e Grupo).
- **`transaction-edit-dialog`** (`app-transaction-edit-dialog`) — usa `CurrencyMaskDirective` (valor) e `CategorySelectComponent` (categoria). **Form dinâmico por tipo:** `onTypeChange()` — INCOME limpa budgetGroup/categoria, EXPENSE limpa incomeType. `confirm()` devolve `UpdateTransactionRequest` preservando `notes/shared/totalAmount/userShare` do original.

## Fluxo ponta-a-ponta

Mês selecionado na toolbar → lista recarrega → usuário edita categoria/grupo/tipo → salva → propaga para as iguais (existentes) e ensina `merchant_rule` (importações futuras). Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

## Regras de domínio

- Receita × Despesa dinâmico: receita tem `incomeType`, despesa tem `budget_group`/categoria (regra recorrente; ver glossário no [índice](./README.md)).
- OWN_TRANSFER não aparece na lista (`excludingOwnTransfer`).
- Rateio: quando `shared`, o dashboard soma `userShare` (ver [dashboard.md](./dashboard.md)).

## Onde mexer

- Novo filtro na lista → `TransactionSpecifications` + `findAll` + filtros do `transaction-list`.
- Novo campo editável → `CreateTransactionRequest`/`UpdateTransactionRequest`, `TransactionService.update`, `transaction-edit-dialog`.
- Mudar regra de propagação → `TransactionService.propagateClassification`.

## Testes relevantes

`TransactionServiceTest` (propaga classificação, aprende merchant rule, não aprende para receita), `TransactionControllerTest` (auth 401, create MANUAL 201, update, delete posse 204/403, filtro de mês exclui OWN_TRANSFER, rateio).
