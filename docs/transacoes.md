# Transações

Listar, filtrar, ordenar, editar, renomear (apelido), confirmar revisão e excluir transações. Editar uma transação propaga a classificação para as iguais e ensina o sistema, **sem** resolver a revisão (isso é explícito).

## Backend

### `TransactionService`

- `findAll(userId, month, type, categoryId, needsReview, search, budgetGroup, includeIgnored, pageable)` — paginado via **Specifications** (`TransactionSpecifications`: `forUser`, `inDateRange`, `ofType`, `inCategory`, `needingReview`, `descriptionContains`, `ofBudgetGroup`, `excludingIgnored`). `descriptionContains` casa (case-insensitive) em description/normalizedDescription/notes; `ofBudgetGroup` filtra Essencial/Não Essencial. **`excludingIgnored` só é aplicado quando `includeIgnored=false`** (default) — com `includeIgnored=true` as ignoradas aparecem. Ordenação vem do `pageable` (`?sort=campo,dir`).
- `create(request, user)` — transação `source=MANUAL` (inclui campos de rateio `shared`/`totalAmount`/`userShare`).
- `update(id, request, user)` — **NÃO** limpa `needs_review` (editar um campo não resolve a revisão). Chama **`propagateClassification`**: copia tipo/categoria/budgetGroup/investmentDirection/ignored para todas as transações de mesmo nome efetivo (sem tocar no `needs_review` delas) e, para despesa, faz upsert de `MerchantRule` USER (aprende para o futuro). Valor, data e descrição **não** se propagam (são por transação).
- `confirmReview(id, user)` — **única** forma de resolver a revisão: `needs_review=false` só naquela transação, sem alterar mais nada nem propagar.
- `updateNotes(id, user, notes)` — upsert/delete em `merchant_display_names` e propaga o apelido a todas as transações do mesmo nome efetivo.
- `delete(id, user)` — com checagem de posse (`AccessDeniedException` → 403).

### Endpoints — `TransactionController` (`/api/transactions`)

- `GET /` — paginado; filtros `month`, `type`, `categoryId`, `needsReview`, `search`, `budgetGroup`, `includeIgnored`; `@PageableDefault(size=50, sort="date")` (o front passa `?sort=campo,dir`).
- `POST /` — `CreateTransactionRequest` (201).
- `PUT /{id}` — atualiza (dispara propagação + aprendizado; não resolve revisão).
- `PATCH /{id}/notes` — `UpdateNotesRequest {notes}` (apelido, propaga).
- `PATCH /{id}/review` — confirma a revisão (`needs_review=false`); devolve a `TransactionResponse` atualizada.
- `DELETE /{id}` (204).

### Entidade — `Transaction` (`transactions`)

description, `normalizedDescription`, amount, `type` (enum INCOME/EXPENSE/INVESTMENT), `budgetGroup`, `investmentDirection`, `ignored`, `needsReview`, date, notes; `@ManyToOne` category/importSession/knownPerson/user; source (MANUAL/EXTRATO/FATURA), cardHolder, installmentInfo; `shared`/`totalAmount`/`userShare`; timestamps.

## Frontend (`feature/transactions/`, `transaction.service`)

`transaction.service`: `findAll({month,type,categoryId,needsReview,search,budgetGroup,includeIgnored,sort,page,size})` → `GET /api/transactions`; `update(id, req)` → `PUT`; `updateNotes(id, notes)` → `PATCH /{id}/notes`; `confirmReview(id)` → `PATCH /{id}/review`; `delete(id)` → `DELETE`.

- **`transaction-list`** (`app-transaction-list`) — injeta `PeriodService`.
  - **Reage ao mês global:** `effect(() => { period.period(); pageIndex = 0; load(); })`. `ngOnInit` também honra `?month=` via `period.setFromMonthString()`.
  - **Filtros:** `filterType`, `filterCategoryId`, `filterBudgetGroup` (grupo 50/30/20), `filterSearch` (busca por descrição/apelido, com `debounceTime` via `Subject`), `pendingOnly` (`needsReview=true`) e `showIgnored` (`includeIgnored`). `clearFilters()` zera tudo.
  - **Ordenação:** `MatSort` na tabela (`date`/`description`/`type`/`amount`), início em `date desc`; `onSortChange` recarrega passando `sort=${active},${direction}`.
  - **Sem reload a cada edição:** `update()`/`updateNotes()`/`confirmReview()` retornam a `Transaction` atualizada e o componente faz `patchRow()` (substitui só a linha, nova ref do array) — a página não pisca. `MatPaginator` size 20.
  - **Apelido inline:** clicar na descrição entra em edição (`editingId`, input com `appAutofocus`); Enter/salvar → `updateNotes()` + `patchRow`.
  - **Colunas separadas + edição por campo:** Tipo/Categoria/Grupo-Direção com menus (`onTypePick`/`onCategoryPick`/`onGroupPick` → `quickUpdate`). Selo **"Revisar"** quando `needsReview` (linha destacada) com um botão **check "Confirmar revisão"** ao lado → `confirmReview(tx)` (sob "só pendentes" a linha sai da lista; senão só perde o selo). Como o edit não limpa mais `needs_review`, classificar campos **mantém** o "Revisar" até confirmar.
  - `openEditDialog(tx)` → `TransactionEditDialogComponent` para edição completa (Descrição com `cdkFocusInitial`); resultado → `update()` + `patchRow`. `confirmDelete()` via `ConfirmDialogComponent`.
- **`transaction-edit-dialog`** (`app-transaction-edit-dialog`) — usa `CurrencyMaskDirective` (valor) e `CategorySelectComponent` (categoria). **Form dinâmico por tipo:** `onTypeChange()` — EXPENSE mostra categoria+grupo, INVESTMENT mostra direção, INCOME nenhum; checkbox **"ignorar nos cálculos"**. `confirm()` devolve `UpdateTransactionRequest` preservando `notes/shared/totalAmount/userShare` do original.

## Fluxo ponta-a-ponta

Mês selecionado na toolbar → lista recarrega → usuário edita categoria/grupo/tipo → salva → propaga para as iguais (existentes) e ensina `merchant_rule` (importações futuras). Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

## Regras de domínio

- Por tipo (regra recorrente; ver glossário no [índice](./README.md)): EXPENSE tem `budget_group`/categoria, INVESTMENT tem `investment_direction`, INCOME nenhum.
- Transações `ignored` (ex.: transferência própria) não entram nos cálculos e, por padrão, não aparecem na lista (`excludingIgnored`) — mas o toggle **"Mostrar ignoradas"** (`includeIgnored`) as exibe.
- `needs_review` só é resolvido por **confirmação explícita** (`confirmReview` / botão de check na linha); editar campos preserva o selo.
- **Competência (regime de caixa):** `competence_date` é o mês em que a transação conta no **Dashboard/Relatórios/metas**. Compras de **fatura** têm competência = **mês do vencimento** (definido em lote na importação, ajustável); Pix/débito/manual usam a própria `date`. As agregações usam **`COALESCE(competence_date, date)`**. A **tabela** exibe a `date` da compra, mas o **filtro de mês** da lista usa a competência (`inDateRange` → `COALESCE`). Editável por transação no dialog. Ordenação e reconciliação continuam na `date`.
- Rateio: quando `shared`, o dashboard soma `userShare` (ver [dashboard.md](./dashboard.md)).

## Onde mexer

- Novo filtro na lista → `TransactionSpecifications` + `findAll` + filtros do `transaction-list`.
- Novo campo editável → `CreateTransactionRequest`/`UpdateTransactionRequest`, `TransactionService.update`, `transaction-edit-dialog`.
- Mudar regra de propagação → `TransactionService.propagateClassification`.

## Testes relevantes

`TransactionServiceTest` (propaga classificação, aprende merchant rule, não aprende para receita, **update preserva `needs_review`**, **`confirmReview` limpa sem propagar**), `TransactionControllerTest` (auth 401, create MANUAL 201, update, delete posse 204/403, filtro de mês exclui `ignored`, rateio).
