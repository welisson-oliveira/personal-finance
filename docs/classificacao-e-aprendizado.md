# Classificação e aprendizado (cross-cutting)

Como o sistema reconhece estabelecimentos, aprende com as correções do usuário e propaga classificações. É transversal: alimenta a importação, a fila de revisão e a edição de transações.

## As três tabelas de conhecimento

| Tabela / entidade                                | Chave                                | Papel                                                                                                                                              |
| ------------------------------------------------ | ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `merchant_rules` / `MerchantRule`                | `normalizedName` (+ `user` nullable) | categoria + `expenseType` (budget group) de um estabelecimento. `user=null` = regra global do sistema. `createdBy` SYSTEM/USER, `confidence` 0–100 |
| `merchant_aliases` / `MerchantAlias`             | `alias` (único) → `MerchantRule`     | mapeia variações do texto bruto ("AmazonMktplc", "iFood - NuPay") para o `normalizedName` de uma regra                                             |
| `merchant_display_names` / `MerchantDisplayName` | `(user, normalizedName)`             | apelido/nome amigável **por usuário** (o "notes" de exibição)                                                                                      |

Seeds: `V2` (13 categorias globais), `V3` (~28 `merchant_rules` SYSTEM + aliases).

## Services

- **`MerchantNormalizationService.normalize(raw)`** — minúsculo; acha o primeiro `MerchantAlias` global cujo alias é substring do texto; retorna o `normalizedName` da regra; senão devolve o texto original.
- **`MerchantClassificationService.classify(normalizedName, userId)`** — tenta regra do usuário (`findUserRuleByNormalizedName`), depois regra global (`findGlobalByNormalizedName`); senão `ClassificationResult.unknown()`.
- **`ClassificationResult`** — valor imutável (categoryId/name, subcategory, expenseType, confidence). `isKnown()` = confidence > 0; `isAutoClassifiable()` = confidence ≥ 80.
- **`IncomeClassificationService.classify(tx, userId, holderName)`** (só para `INCOME`) — "open banking" + nome do titular → `OWN_TRANSFER`; casa `KnownPerson` ativo (match difuso de ≥2 partes do nome) → aplica `defaultIncomeType` + `defaultLabel`; senão consulta o histórico (`findByUserIdAndEffectiveName`, incomeType mais recente que não seja OWN_TRANSFER); fallback `INCOME`. Ver [pessoas-conhecidas.md](./pessoas-conhecidas.md).

## Aprendizado (como o sistema "para de perguntar")

- **Ao resolver na fila de revisão** (despesa) ou **ao editar uma transação** (despesa com budget group): faz upsert de uma `MerchantRule` do usuário com `confidence=100`, `createdBy=USER` (`findUserRuleByNormalizedName`). Assim a próxima importação do mesmo estabelecimento é auto-classificada.
- **Alias:** ao resolver a revisão, a descrição bruta é salva como novo `MerchantAlias` (dedup por `findByAliasIgnoreCase`), então o mesmo estabelecimento é reconhecido da próxima vez.
- **Receita não é aprendida como regra** — a classificação de receita vem de `KnownPerson` e do histórico, nunca de `merchant_rules`.
- **Precedência na importação:** regra do usuário → regra global → desconhecido (`needsReview`). Limiar de auto-classificação: confidence ≥ 80.

## Propagação por "nome efetivo"

`TransactionRepository.findByUserIdAndEffectiveName(userId, name)` casa `normalizedDescription` quando existe, senão a `description` bruta. É a chave usada para:

- propagar categoria/budgetGroup/incomeType ao editar uma transação (`TransactionService.propagateClassification`);
- propagar apelido (`updateNotes` + `merchant_display_names`);
- aplicar a resolução da fila a todas as transações do mesmo nome (`ReviewQueueService`);
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

`MerchantClassificationServiceTest`, `MerchantNormalizationServiceTest`, `IncomeClassificationServiceTest` (open-banking, known-person, aprendizado por histórico, fallback), `TransactionServiceTest` (propagação + aprende regra; receita não aprende), `ReviewQueueServiceTest` (aprende regra/alias, aplica a todas as transações).
