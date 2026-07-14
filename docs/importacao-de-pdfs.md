# Importação de PDFs

Upload de extrato/fatura Nubank (PDF) → parse + classificação automática → preview editável → confirmação vira transações. É o principal ponto de entrada de dados.

## Backend

### Pipeline — `TransactionImportService`

`parseAndPreview(file, documentType, user)`:

1. Extrai texto do PDF (PDFBox `Loader` + `PDFTextStripper`).
2. Resolve o tipo de documento (`documentType` informado ou `DocumentTypeDetector.detect`).
3. Despacha para `NubankExtratoParser` ou `NubankFaturaParser`.
4. Normaliza a descrição (`MerchantNormalizationService`). Depois, por tipo: `INVESTMENT` (RDB) fica como o parser deixou; `INCOME` passa por `IncomeClassificationService.classify` (own-transfer → `ignored`; known-person → `ignored`/`needsReview`); `EXPENSE` é classificado pelo `MerchantClassificationService` definindo categoria/`budgetGroup`/`needsReview` conforme a confiança (≥80 auto).
5. Salva `ImportSession` (status `PENDING`) **persistindo a lista parseada como JSON** na coluna `preview_json` (via Jackson + `JavaTimeModule`) — sobrevive a restart do backend.

`getPreview(sessionId, user)` — **retomar importação pendente**: relê o `preview_json` da sessão e reconstrói o `ImportPreviewResponse`. Só funciona enquanto a sessão está `PENDING` (com JSON presente); caso contrário lança `IllegalStateException` (→ 409, "re-upload the PDF"). Usado quando o usuário sai do preview sem confirmar e quer voltar.

`confirm(sessionId, clientList, user)`:

- Persiste **apenas** os DTOs com `included = true` como `Transaction` (resolvendo o apelido via `merchant_display_names`).
- Persiste o `needs_review` **na própria `Transaction`** (não há mais tabela `review_queue`) — a resolução é inline na lista de transações.
- Marca a sessão `CONFIRMED` e **zera o `preview_json`**. **Usa a lista enviada pelo cliente** (com as edições do preview), não o JSON persistido.

`cancel` (→ CANCELLED), `deleteSession` (apaga sessão + transações + itens de revisão), `getHistory`.

### Parsers (`service/parser/`)

- **`DocumentTypeDetector`** — heurística `detect(text)` → `FATURA` (período vigente, vencimento…) ou `EXTRATO` (total de entradas/saídas, conta…).
- **`NubankExtratoParser`** — percorre linhas após "Movimenta…", rastreia blocos entradas/saídas; trata "Pagamento de fatura" como `INTERNAL` (`included=false`), crédito em conta, boletos, transferências multilinha; nomes de mês PT. **RDB vira investimento** (`type=INVESTMENT`, `autoClassification=INVESTMENT`, `included=true`): Aplicação RDB → `investmentDirection=CONTRIBUTION` (aporte); Resgate RDB → `investmentDirection=REDEMPTION` (resgate). **`resolveType` sobrepõe o bloco pela palavra "recebida"/"enviada"** (correção de tipo). Retorna `ParseResult(periodStart, periodEnd, transactions)`.
- **`NubankFaturaParser`** — período + ano do vencimento (trata virada de ano); seções por portador ("Welisson W Oliveira", "Rosangela Oliveira"); filtra "Pagamentos"; parcela `Parcela n/n` → `installmentInfo`; estorno (`-R$`) → `INCOME`.

### Endpoints — `ImportController` (`/api/import`)

- `POST /parse` (multipart `file`, opcional `documentType`) → `ImportPreviewResponse` (201).
- `GET /{id}/preview` → `ImportPreviewResponse` reconstruído do `preview_json` (retomar sessão `PENDING`; 409 se já confirmada/cancelada).
- `POST /{id}/confirm` (body `List<ParsedTransactionDTO>`) → persiste confirmados.
- `POST /{id}/cancel`.
- `GET /history` → `List<ImportSessionResponse>` com contagem de transações.
- `DELETE /{id}` → apaga a sessão e o que veio dela (204).

### DTO central — `ParsedTransactionDTO`

Campos: date, description, amount, type, cardHolder, installmentInfo, normalizedDescription, budgetGroup, `investmentDirection`, `ignored`, categoryId/Name, notes, knownPersonId, `needsReview`, **`included`** (default true), `autoClassification` (badge `OWN_TRANSFER`/`INVESTMENT`/`INTERNAL`/`INTERNAL_FATURA_EXISTS`). **Serve também de body do confirm.**

## Frontend (`feature/import/`, `import.service`)

`import.service`: `parse(file)` → `POST /api/import/parse`; `confirm(sessionId, txs)` → `POST /api/import/{id}/confirm`; `cancel`, `getHistory`, `deleteSession`.

- **`upload`** (`app-upload`) — página `/import`. Drag-and-drop + input, **só PDF**. `upload()` → `parse()` e navega para `/import/preview` passando `preview` no `state` do router. Também embute o **histórico** (`groupByMonth`, status CONFIRMED/CANCELLED/PENDING), `goToTransactions()` (seta o período global e navega) e exclusão via `ConfirmDialogComponent`. Injeta `PeriodService`. **Sessões `PENDING` têm botão "Retomar"** (`resumeSession` → `getPreview` → navega ao preview); se o preview expirou, mostra snackbar e recarrega o histórico.
- **`preview`** (`app-preview`) — lê `preview` do `state` (sem ele, redireciona para `/import`). Tabela Material editável (colunas: included, date, description, amount, type, budgetGroup, direction, category, notes) com `CategoryService.getAll()`; `budgetGroups`/`directions` com tooltips; `autoClassificationLabel/Tooltip`. `confirm()` → `importService.confirm()` → `/dashboard`; `cancel()`.
- **`history`** (`app-import-history`) — lista de sessões. **Nota: rota `/import/history` redireciona para `/import`** (o histórico está embutido no upload); o componente existe mas não é roteado.

## Fluxo ponta-a-ponta

Upload PDF → parse+classificação → preview (usuário ajusta tipo/categoria/inclusão) → confirm → transações salvas; os desconhecidos ficam com `needs_review=true` e são resolvidos **inline** na [lista de transações](./transacoes.md) → dashboard atualizado.

## Regras de domínio

- `OWN_TRANSFER` e transações `INTERNAL` (pagamento de fatura) chegam ao preview com `included=false` — visíveis mas desmarcadas.
- RDB (`Aplicação`/`Resgate`) chega classificado como investimento e **marcado para incluir** (alimenta o dashboard). O `parseAndPreview` preserva essa classificação (não roda os classificadores por cima quando `autoClassification=INVESTMENT`).
- Classificação de estabelecimento e aprendizado: ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).
- Pré-preenchimento de apelido no import vem de `merchant_display_names` (ver [transacoes.md](./transacoes.md)).

## Onde mexer

- Novo layout/banco de PDF → novo parser em `service/parser/` + ajuste no `DocumentTypeDetector` e no dispatch de `parseAndPreview`.
- Nova coluna no preview → `ParsedTransactionDTO` + tabela do `preview.component`.
- Regra de inclusão automática → `included`/`needsReview` nos parsers e em `parseAndPreview`.

## Testes relevantes

`TransactionImportServiceTest` (confirm persiste só `included`, usa dados do cliente e não o JSON, persiste `needs_review`/`ignored`/`investmentDirection` na Transaction, pula excluídos; `getPreview` devolve o preview persistido de sessão `PENDING` e recusa sessão não-pendente / sem JSON), `NubankExtratoParserTest` e `NubankFaturaParserTest` (fixtures reais `extrato.pdf`/`fatura.pdf`), `import.service.spec` e `preview.component.spec` (os únicos specs de frontend além do `app.component`).
