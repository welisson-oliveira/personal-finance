# Categorias

CRUD de categorias do usuário (as globais do sistema são visíveis mas somente leitura), com preview visual na criação e um seletor com busca reutilizável.

## Backend

### `CategoryService` / `CategoryController` (`/api/categories`)

- `GET /` — lista categorias globais + do usuário (`findByUserIdOrUserIsNull`).
- `POST /` — cria (`CreateCategoryRequest {name, icon, color}`), 201.
- `PUT /{id}` — atualiza categoria do usuário.
- `DELETE /{id}` — exclui categoria do usuário (204).

**`Category`** (`categories`): name, icon, color; `@ManyToOne User` nullable — **`user=null` = global/seed**. Categorias globais não podem ser editadas/excluídas. `CategoryResponse` expõe `global` (= `user==null`). Seeds em `V2` (13 categorias). `user_id` foi adicionado em `V4`.

### Merchant rules (leitura)

`MerchantRuleController` (`GET /api/merchant-rules`) devolve as regras visíveis (global + usuário) — relacionado a categorias porque cada regra aponta para uma categoria. Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

## Frontend

- **`feature/categories/category-list`** (`app-category-list`) — lista; `openCreate()`/`openEdit()` abrem o form dialog; `confirmDelete()` via `ConfirmDialogComponent`. **Categorias globais são read-only** — `openEdit`/`confirmDelete` retornam cedo quando `cat.global`. O ícone é exibido na cor da categoria.
- **`feature/categories/category-form-dialog`** (`app-category-form-dialog`) — **template e estilos inline** (sem `.html`/`.scss`). Form reativo (name obrigatório, icon, color). Tem **preview ao vivo** do chip, grade de **ícones sugeridos** (`suggestedIcons`) e **paleta de cores** (`palette`, 16 cores, toggle). Usa `CategoryService`.
- **`shared/category-select`** (`app-category-select`) — reutilizável: `mat-select` de categoria com **busca no painel** e ícone/cor nas opções; two-way `[(value)]` (`''` = "Nenhuma"). No rodapé do painel há **"➕ Nova categoria…"**: abre o `category-form-dialog`, persiste via `CategoryService.create` e **já seleciona** a criada, emitindo `@Output() categoryCreated` **antes** do `valueChange` para o pai sincronizar sua lista (`(categoryCreated)="categories = categories.concat($event)"`). Usado no **editar transação**, **metas** e no **preview de importação** (seletor de categoria por linha). A **lista de transações** oferece o mesmo "➕ Nova categoria…" no menu de categoria da célula (`createCategoryFor`).

## Onde mexer

- Novo atributo de categoria → `Category` (+ migration), `CreateCategoryRequest`, `CategoryResponse`, `CategoryService`, `category-form-dialog`.
- Mudar seletor de categoria (aparência/busca) → `shared/category-select` (afeta editar transação e resolver revisão de uma vez).
- Nova categoria global → seed em `V2` (ou nova migration).

## Testes relevantes

Sem testes unitários dedicados de categoria no momento; cobertura indireta via os testes de classificação/dashboard que dependem das categorias seedadas.
