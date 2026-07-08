# Importação de PDFs

Upload de extrato/fatura Nubank (PDF) → parse + classificação automática → preview editável → confirmação vira transações. É o principal ponto de entrada de dados.

## Backend

### Pipeline — `TransactionImportService`

`parseAndPreview(file, documentType, user)`:

1. Extrai texto do PDF (PDFBox `Loader` + `PDFTextStripper`).
2. Resolve o tipo de documento (`documentType` informado ou `DocumentTypeDetector.detect`).
3. Despacha para `NubankExtratoParser` ou `NubankFaturaParser`.
4. Para cada transação: se `INCOME`, `IncomeClassificationService.classify` (senão zera `incomeType`); normaliza a descrição (`MerchantNormalizationService`); classifica o estabelecimento (`MerchantClassificationService`) definindo categoria/`budgetGroup`/`needsReview` conforme a confiança (≥80 auto).
5. Salva `ImportSession` (status `PENDING`) e guarda a lista parseada num cache em memória (`ConcurrentHashMap<UUID, List<ParsedTransactionDTO>>` por sessão).

`confirm(sessionId, clientList, user)`:

- Persiste **apenas** os DTOs com `included = true` como `Transaction` (resolvendo o apelido via `merchant_display_names`).
- Cria linhas em `review_queue` para os `needsReview` (guardando o `type`).
- Marca a sessão `CONFIRMED` e limpa o cache. **Usa a lista enviada pelo cliente** (com as edições do preview), não o cache.

`cancel` (→ CANCELLED), `deleteSession` (apaga sessão + transações + itens de revisão), `getHistory`.

### Parsers (`service/parser/`)

- **`DocumentTypeDetector`** — heurística `detect(text)` → `FATURA` (período vigente, vencimento…) ou `EXTRATO` (total de entradas/saídas, conta…).
- **`NubankExtratoParser`** — percorre linhas após "Movimenta…", rastreia blocos entradas/saídas; trata "Pagamento de fatura" como `INTERNAL` (`included=false`), crédito em conta, boletos, transferências multilinha; nomes de mês PT. **RDB vira investimento** (`autoClassification=INVESTMENT`, `included=true`): Aplicação RDB → Despesa com `budgetGroup=INVESTMENT` (alimenta `investido`); Resgate RDB → Receita com `incomeType=INVESTMENT` (alimenta `resgatado`). **`resolveType` sobrepõe o bloco pela palavra "recebida"/"enviada"** (correção de tipo). Retorna `ParseResult(periodStart, periodEnd, transactions)`.
- **`NubankFaturaParser`** — período + ano do vencimento (trata virada de ano); seções por portador ("Welisson W Oliveira", "Rosangela Oliveira"); filtra "Pagamentos"; parcela `Parcela n/n` → `installmentInfo`; estorno (`-R$`) → `INCOME`.

### Endpoints — `ImportController` (`/api/import`)

- `POST /parse` (multipart `file`, opcional `documentType`) → `ImportPreviewResponse` (201).
- `POST /{id}/confirm` (body `List<ParsedTransactionDTO>`) → persiste confirmados.
- `POST /{id}/cancel`.
- `GET /history` → `List<ImportSessionResponse>` com contagem de transações.
- `DELETE /{id}` → apaga a sessão e o que veio dela (204).

### DTO central — `ParsedTransactionDTO`

Campos: date, description, amount, type, cardHolder, installmentInfo, normalizedDescription, incomeType, budgetGroup, categoryId/Name, notes, knownPersonId, `needsReview`, **`included`** (default true), `autoClassification` (badge `OWN_TRANSFER`/`INVESTMENT`/`INTERNAL`). **Serve também de body do confirm.**

## Frontend (`feature/import/`, `import.service`)

`import.service`: `parse(file)` → `POST /api/import/parse`; `confirm(sessionId, txs)` → `POST /api/import/{id}/confirm`; `cancel`, `getHistory`, `deleteSession`.

- **`upload`** (`app-upload`) — página `/import`. Drag-and-drop + input, **só PDF**. `upload()` → `parse()` e navega para `/import/preview` passando `preview` no `state` do router. Também embute o **histórico** (`groupByMonth`, status CONFIRMED/CANCELLED/PENDING), `goToTransactions()` (seta o período global e navega) e exclusão via `ConfirmDialogComponent`. Injeta `PeriodService`.
- **`preview`** (`app-preview`) — lê `preview` do `state` (sem ele, redireciona para `/import`). Tabela Material editável (colunas: included, date, description, amount, type, incomeType, budgetGroup, category, notes) com `CategoryService.getAll()`; listas `incomeTypes`/`budgetGroups` com tooltips; `autoClassificationLabel/Tooltip`. `confirm()` → `importService.confirm()` → `/dashboard`; `cancel()`.
- **`history`** (`app-import-history`) — lista de sessões. **Nota: rota `/import/history` redireciona para `/import`** (o histórico está embutido no upload); o componente existe mas não é roteado.

## Fluxo ponta-a-ponta

Upload PDF → parse+classificação → preview (usuário ajusta tipo/categoria/inclusão) → confirm → transações salvas + itens desconhecidos vão para a [fila de revisão](./fila-de-revisao.md) → dashboard atualizado.

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

`TransactionImportServiceTest` (confirm persiste só `included`, usa dados do cliente e não o cache, exclui OWN_TRANSFER, cria/pula review-queue), `NubankExtratoParserTest` e `NubankFaturaParserTest` (fixtures reais `extrato.pdf`/`fatura.pdf`), `import.service.spec` e `preview.component.spec` (os únicos specs de frontend além do `app.component`).
