# Fila de revisão

Estabelecimentos desconhecidos (ou de baixa confiança) importados caem aqui para o usuário classificar uma vez — e o sistema aprende para não perguntar de novo.

## Backend

### Entidade

**`ReviewQueue`** (`review_queue`): user, importSession, rawDescription, normalizedDescription, **`type`** (INCOME/EXPENSE — adicionado em V9), amount, transactionDate, suggestedCategory, status (`PENDING`/`REVIEWED`/`SKIPPED`), resolvedAt. Não guarda referência à `Transaction` — o casamento é por nome efetivo.

### `ReviewQueueService`

- `findPending(userId)` → itens `PENDING` ordenados por criação. `toResponse` mapeia o `type` (default `EXPENSE` para linhas antigas sem tipo).
- `resolve(reviewId, request, user)`:
  1. Valida posse.
  2. **Despesa:** upsert de `MerchantRule` USER (confidence 100) + cria `MerchantAlias` da descrição bruta (dedup). **Receita:** não cria regra/alias (receita é corrigida só nos itens).
  3. Se veio apelido, upsert em `merchant_display_names`.
  4. **Aplica a resolução a todas as transações com o mesmo nome efetivo** (`findByUserIdAndEffectiveName`): seta `type` e, conforme o tipo, categoria+budgetGroup (despesa) ou incomeType (receita), limpando os campos do outro lado.
  5. **Auto-resolve os itens irmãos** `PENDING` com o mesmo `normalizedDescription`.
  6. Marca o item `REVIEWED`.

### Endpoints — `ReviewController` (`/api/review`)

- `GET /pending` → `List<ReviewQueueItemResponse>`.
- `POST /{id}/resolve` (body `ResolveReviewRequest {categoryId?, budgetGroup?, merchantName, transactionNotes?, type, incomeType?}`).

## Frontend (`feature/review/`, `review.service`)

`review.service`: `getPending()` → `GET /api/review/pending`; `resolve(id, req)` → `POST /api/review/{id}/resolve`.

- **`review-queue`** (`app-review-queue`) — carrega pendentes + categorias. Cada item mostra **selo Receita/Despesa**, data, valor e categoria sugerida. `openResolveDialog(item)` abre o `ResolveDialogComponent`; ao resolver, chama `resolve()` e remove o item da lista otimisticamente.
- **`resolve-dialog`** (`app-resolve-dialog`) — **toggle Receita/Despesa** (`MatButtonToggle`) que controla `isIncome`. Campos: `type` (pré-selecionado do `item.type`), `categoryId` (do `suggestedCategoryId`), `budgetGroup` (default NON_ESSENTIAL), `incomeType` (default INCOME), `merchantName` (do normalizado/bruto, **read-only**), `transactionNotes`. `valid` ramifica por tipo. `confirm()` monta o `ResolveReviewRequest` zerando o lado que não se aplica. Usa `CategorySelectComponent`.

## Fluxo ponta-a-ponta

Importação marca desconhecidos como `needsReview` → viram `ReviewQueue` no confirm → usuário classifica na fila (corrigindo o tipo se preciso) → aprende `merchant_rule`/alias (despesa) e aplica a todas as transações iguais → próximos imports já chegam classificados.

## Regras de domínio

- Correção de tipo: o toggle existe porque o parser pode inferir errado (ver [importacao-de-pdfs.md](./importacao-de-pdfs.md) e [PR #31]). A resolução aplica o tipo a **todas** as transações com o mesmo nome.
- Aprendizado/propagação: ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

## Onde mexer

- Novo campo na resolução → `ResolveReviewRequest` + `ReviewQueueService.resolve` + `resolve-dialog`.
- Mudar o que entra na fila → `needsReview` no pipeline de importação.

## Testes relevantes

`ReviewQueueServiceTest` (marca reviewed, cria alias + dedup, guarda posse, aplica a todas as transações, receita limpa campos de despesa), `ReviewControllerTest` (pending, resolve cria regra/alias, posse 400).
