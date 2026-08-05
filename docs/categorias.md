# Categorias

Categorias **hierárquicas (categoria → subcategoria) e 100% do usuário** (Opção 2): todo mundo começa com uma cópia editável da árvore inicial, e pode renomear/excluir/criar à vontade. Nada é global travado. Preview visual na criação e um seletor com busca reutilizável (agrupado por pai).

## Backend

### `CategoryService` / `CategoryController` (`/api/categories`)

- `GET /` — lista **só as categorias do usuário** (`findByUserId`, com `LEFT JOIN FETCH` do pai). As globais (semente) existem só internamente como alvo das regras globais de classificação e **não** aparecem aqui.
- `POST /` — cria (`CreateCategoryRequest {name, icon, color, parentId?}`), 201. `parentId` opcional torna a categoria uma **subcategoria**.
- `PUT /{id}` — atualiza categoria do usuário (inclui `parentId`).
- `DELETE /{id}` — exclui categoria do usuário (204); subcategorias caem junto (`ON DELETE CASCADE`).

**`Category`** (`categories`): name, icon, color; `@ManyToOne User`; **`@ManyToOne parent` (auto-referência, `parent_id` — V16)**: `null` = principal, não-nulo = subcategoria. `CategoryResponse` expõe `global` (= `user==null`), `parentId`, `parentName`. `CategoryService` valida o pai (posse do usuário, sem ciclo, **só um nível** — subcategoria não tem subcategoria).

**Provisionamento (Opção 2).** A árvore inicial vive em `DefaultCategories.TREE` (Java, não é seed SQL). `CategoryProvisioningService.provisionDefaults(user)` copia a árvore como categorias **do usuário** (idempotente — no-op se já tiver categorias); chamado em `UserService.register`. `CategoryBootstrapRunner` (`ApplicationRunner`) provisiona os usuários **já existentes** no startup e **remapeia** os dados que apontavam pras categorias globais antigas (V2) pras novas por nome (`DefaultCategories.legacyTopLevel`): **transações** e **metas de orçamento** (a meta segue contando o `spent` depois que as transações migram; nunca remapeia duas metas pra mesma categoria — a 2ª mantém a antiga, respeitando 1 meta por categoria).

**Inteligência com categorias dinâmicas.** A classificação nunca casa por nome de categoria hardcoded — ela carrega só um FK. Na importação, quando uma **regra global** classifica, `TransactionImportService.resolveUserCategoryId` traduz a categoria global pra categoria **do usuário** de mesmo nome (ou null se ele apagou); regras aprendidas do usuário já apontam pra categoria dele. Aprender uma correção grava a categoria escolhida (principal ou subcategoria) — funciona em qualquer categoria criada na hora. Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

**Metas + roll-up.** Uma meta numa categoria **principal** soma também os gastos das suas subcategorias (`TransactionRepository.sumExpenseByCategoryIdsAndDateBetween` com `[pai] + filhos`). Ver [metas-de-orcamento.md](./metas-de-orcamento.md).

### Merchant rules (leitura)

`MerchantRuleController` (`GET /api/merchant-rules`) devolve as regras visíveis (global + usuário) — relacionado a categorias porque cada regra aponta para uma categoria. Ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).

## Frontend

- **`feature/categories/category-list`** (`app-category-list`) — **árvore** (`tree` getter agrupa cada principal com suas subcategorias, ordenadas). Cada principal tem um **"+"** (`addSubcategory`, abre o form já com o pai pré-selecionado) + editar/excluir; subcategorias aparecem indentadas (`.sub-item`) com editar/excluir. Como tudo agora é do usuário, não há mais item read-only.
- **`feature/categories/category-form-dialog`** (`app-category-form-dialog`) — **template e estilos inline**. Form reativo (name obrigatório, icon, color, **`parentId`**). Select **"Categoria pai (opcional)"** listando as principais (escondido quando se edita uma categoria que **já tem** subcategorias — não pode virar filha). Preview ao vivo, ícones sugeridos e paleta. Injeta `CategoryService` (carrega as principais).
- **`shared/category-select`** (`app-category-select`) — reutilizável: `mat-select` com **busca no painel**; as opções são **ordenadas como árvore** (`ordered` getter: cada principal seguida das suas subcategorias, indentadas). Two-way `[(value)]` (`''` = "Nenhuma"). "➕ Nova categoria…" no rodapé cria e seleciona (emite `categoryCreated` antes do `valueChange`). Usado no **editar transação**, **metas** e no **preview de importação**. A **lista de transações** oferece o mesmo no menu de categoria da célula (`createCategoryFor`).

## Onde mexer

- Novo atributo de categoria → `Category` (+ migration), `CreateCategoryRequest`, `CategoryResponse`, `CategoryService`, `category-form-dialog`.
- Mudar a árvore inicial → `DefaultCategories.TREE` (e `legacyTopLevel` se renomear/mover principais).
- Mudar seletor de categoria (aparência/busca/árvore) → `shared/category-select`.
- Tradução de categoria global→usuário na importação → `TransactionImportService.resolveUserCategoryId`.

## Testes relevantes

`CategoryProvisioningServiceTest` (provisiona a árvore inteira como categorias do usuário; no-op quando já tem categorias), `UserServiceTest` (register provisiona), `BudgetGoalServiceTest` (roll-up: meta no pai soma as subcategorias). Cobertura indireta pelos testes de classificação/importação.
