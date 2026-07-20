# Classificação e aprendizado (cross-cutting)

Como o sistema reconhece estabelecimentos, aprende com as correções do usuário e propaga classificações. É transversal: alimenta a importação e a edição/revisão inline de transações.

## As três tabelas de conhecimento

| Tabela / entidade                                | Chave                                | Papel                                                                                                                                              |
| ------------------------------------------------ | ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `merchant_rules` / `MerchantRule`                | `normalizedName` (+ `user` nullable) | **override** de um estabelecimento: categoria + `expenseType` (budget group) **+ `type` + `ignored` + `investmentDirection`** (V14). `user=null` = regra global do sistema. `createdBy` SYSTEM/USER, `confidence` 0–100 |
| `merchant_aliases` / `MerchantAlias`             | `alias` (único) → `MerchantRule`     | mapeia variações do texto bruto ("AmazonMktplc", "iFood - NuPay") para o `normalizedName` de uma regra                                             |
| `merchant_display_names` / `MerchantDisplayName` | `(user, normalizedName)`             | apelido/nome amigável **por usuário** (o "notes" de exibição)                                                                                      |

Seeds: `V2` (13 categorias globais), `V3` (~28 `merchant_rules` SYSTEM + aliases).

## Services

- **`MerchantNormalizationService.normalize(raw)`** — minúsculo; acha o primeiro `MerchantAlias` global cujo alias é substring do texto; retorna o `normalizedName` da regra; senão devolve o texto original.
- **`MerchantClassificationService.classify(normalizedName, userId)`** — tenta regra do usuário (`findUserRuleByNormalizedName`), depois regra global (`findGlobalByNormalizedName`); senão `ClassificationResult.unknown()`. **`findUserOverride(normalizedName, userId)`** devolve **só** a regra do usuário (o override aplicado na importação antes das heurísticas).
- **`ClassificationResult`** — valor imutável (categoryId/name, subcategory, expenseType, confidence, **type, ignored, investmentDirection**). `isKnown()` = confidence > 0; `isAutoClassifiable()` = confidence ≥ 80.
- **`IncomeClassificationService.classify(tx, userId, holderName)`** (só para `INCOME`) — "open banking" + nome do titular → `ignored` (transferência própria); casa `KnownPerson` ativo (match difuso de ≥2 partes do nome) → aplica `defaultTreatment` (`IGNORE`→`ignored`, `ALWAYS_REVIEW`→`needsReview`, `INCOME`→receita) + `defaultLabel`; senão fica como receita simples. Ver [pessoas-conhecidas.md](./pessoas-conhecidas.md).

## Aprendizado (como o sistema "para de perguntar")

- **Ao editar uma transação** (qualquer tipo): faz upsert de uma `MerchantRule` do usuário com `confidence=100`, `createdBy=USER`, gravando **`type`, `ignored`, categoria, `expenseType` e `investmentDirection`**. Assim a próxima importação do mesmo estabelecimento vem já classificada — inclusive corrigindo heurísticas (ex.: "esta transferência Open Banking é meu salário, não é transferência própria, não ignore").
- **Aplicação na importação:** para **cada** transação, se existe uma regra **do usuário** (`findUserOverride`), ela é aplicada **antes** de qualquer heurística (own-transfer, investimento do parser, regra global de despesa) — `applyUserOverride` seta tipo/ignored/categoria/grupo/direção e `needsReview=false`.
- **Trava por sinal (crédito × débito):** o sinal do extrato é a **verdade** para dinheiro-que-entra vs. dinheiro-que-sai. Uma regra aprendida num Pix **enviado** (`pix joão` = despesa) **não** pode transformar um Pix **recebido** da mesma pessoa em despesa. `flipsMoneyDirection(tx, rule)` compara a direção (money-in = `INCOME` ou `INVESTMENT/REDEMPTION`; money-out = o resto): se a regra **inverteria** a direção lida pelo parser, o override é **ignorado** e a transação segue pela heurística da direção correta. A regra ainda pode **refinar dentro da mesma direção** (ex.: um débito que na verdade é aporte). Correção do histórico já gravado: migração `V19` conserta transações de extrato cujo tipo conflita com a redação autoritativa (`recebido` = entrada, `enviado` = saída).
- **Guarda "Pagamento de fatura":** o aprendizado **não** cria regra para pagamentos de fatura — o `ignored` deles é estrutural (anti-duplicação, tratado pela conciliação), não uma classificação de estabelecimento.
- **Alias:** ao resolver a revisão, a descrição bruta é salva como novo `MerchantAlias` (dedup por `findByAliasIgnoreCase`), então o mesmo estabelecimento é reconhecido da próxima vez.
- **Precedência na importação:** override do usuário → (investimento do parser / heurística de receita / regra global de despesa) → desconhecido (`needsReview`). Limiar de auto-classificação: confidence ≥ 80.

## Propagação por "nome efetivo"

`TransactionRepository.findByUserIdAndEffectiveName(userId, name)` casa `normalizedDescription` quando existe, senão a `description` bruta. É a chave usada para:

- propagar tipo/categoria/budgetGroup/investmentDirection/ignored ao editar uma transação e limpar `needs_review` das iguais (`TransactionService.propagateClassification`);
- propagar apelido (`updateNotes` + `merchant_display_names`);
- resolver a revisão inline aplicando a classificação a todas as transações do mesmo nome (`TransactionService.update`);
- histórico de classificação de receita (`IncomeClassificationService`).

## Apelido de exibição (`merchant_display_names`)

Nome amigável por `(user, normalizedName)`, definido via `PATCH /transactions/{id}/notes` ou ao resolver revisão. Aplicado a **todas** as transações do mesmo nome efetivo e às importações futuras (`confirm` resolve o notes por essa tabela). Apelido vazio **apaga** o mapeamento.

## Merchant rules — leitura no frontend

`MerchantRuleController` (`GET /api/merchant-rules`) devolve regras visíveis (global + usuário), lidas direto do repositório (`flag global = user==null`). Hoje só há consumo de leitura.

## Onde mexer

- Novo estabelecimento global → seed em `V3` (ou nova migration) em `merchant_rules` (+ aliases).
- Mudar limiar de auto-classificação → `ClassificationResult.isAutoClassifiable()`.
- Nova regra de normalização → `MerchantNormalizationService` (hoje é substring de alias global).

## Testes relevantes

`MerchantClassificationServiceTest` (classify global + **`findUserOverride` carrega type/ignored**), `MerchantNormalizationServiceTest`, `IncomeClassificationServiceTest` (open-banking → ignored, known-person por tratamento, receita simples), `TransactionServiceTest` (propagação + **aprende override de receita com type/ignored**; **não aprende para "Pagamento de fatura"**), `TransactionImportServiceTest` (**trava por sinal**: `flipsMoneyDirection` bloqueia regra de despesa sobre entrada, permite refinamento na mesma direção, trata resgate como entrada, ignora regra sem tipo).
