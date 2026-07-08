# Pessoas conhecidas

Cadastro de pessoas recorrentes em PIX (Wilson, Paula, etc.) e como tratar as entradas vindas delas. É a base do aprendizado de **receita** (que não usa merchant rules).

## Backend

### `KnownPersonService` / `KnownPersonController` (`/api/known-persons`)

- `GET /` — lista pessoas ativas do usuário.
- `POST /` — cria (`CreateKnownPersonRequest`), 201.
- `PUT /{id}` — atualiza.
- `PATCH /{id}/deactivate` — desativa (soft delete, `active=false`), 204.

**`KnownPerson`** (`known_persons`): user, name, `relationship` (`HOUSE_MEMBER`/`FAMILY`/`FRIEND`/`OTHER`), **`defaultIncomeType`** (`REIMBURSEMENT`/`INCOME`/`OWN_TRANSFER`/`ALWAYS_REVIEW`, default `REIMBURSEMENT`), `defaultLabel` (opcional), `active`.

### Ligação com a classificação de receita

No import, `IncomeClassificationService.classify` faz match difuso (≥2 partes do nome) contra as pessoas ativas; ao casar, aplica o `defaultIncomeType` da pessoa e pré-preenche o notes com `defaultLabel`. Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

Exemplos: Wilson → `REIMBURSEMENT` / "Reembolso - Aluguel"; Paula → `REIMBURSEMENT` / "Rateio supermercado".

## Frontend (`feature/known-persons/`, `known-person.service`)

`known-person.service`: `getAll()` → `GET /api/known-persons`; `create` → `POST`; `update` → `PUT /{id}`; `deactivate` → `PATCH /{id}/deactivate`.

- **`known-person-list`** (`app-known-person-list`) — lista com mapas `relationshipLabel`/`incomeTypeLabel`; criar/editar via dialog; `deactivate()` em vez de exclusão dura.
- **`known-person-form-dialog`** (`app-known-person-form-dialog`) — **template/estilos inline**. Form: name (obrigatório), relationship (default HOUSE_MEMBER), defaultIncomeType (default REIMBURSEMENT), defaultLabel (opcional).

## Regras de domínio

- `defaultIncomeType` decide se a entrada da pessoa conta como receita: `REIMBURSEMENT`/`OWN_TRANSFER` nunca contam; `INCOME` conta; `ALWAYS_REVIEW` sempre cai na fila. Ver o glossário no [índice](./README.md).
- A alteração feita no preview de import é por transação e **não** muda o padrão da pessoa.

## Onde mexer

- Novo tipo de tratamento de entrada → `defaultIncomeType` (enum de valores + CHECK na migration) + `IncomeClassificationService` + form dialog.
- Melhorar o match de nome → `IncomeClassificationService` (lógica de similaridade).

## Testes relevantes

`IncomeClassificationServiceTest` (match de known-person aplica o tipo, own-transfer excluído, aprendizado por histórico, fallback INCOME).
