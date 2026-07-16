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

`updatePreview(sessionId, clientList, user)` — **salva as edições em andamento** de volta no `preview_json` da sessão (reusa `serializePreview`). Só sessões `PENDING` (senão `IllegalStateException` → 409). É o que faz as edições do preview **sobreviverem** a sair da tela / atualizar a página / fechar o app: o frontend chama a cada alteração, e o `getPreview` do "Retomar" passa a devolver o estado editado (antes, devolvia só o snapshot do parse).

`confirm(sessionId, clientList, user)`:

- Persiste **apenas** os DTOs com `included = true` como `Transaction` (resolvendo o apelido via `merchant_display_names`).
- Persiste o `needs_review` **na própria `Transaction`** (não há mais tabela `review_queue`) — a resolução é inline na lista de transações.
- **Reconciliação extrato↔fatura — sugerida, aprovável, nas duas ordens.** O sistema **sugere** o vínculo (casamento por valor) e o usuário **aprova ou ajusta**; o vínculo **substitui** (apaga o "Pagamento de fatura" do extrato).
  - **Sugestões no preview** (`buildReconciliation`, recalculado no `parseAndPreview` e no `getPreview`): retorna `List<ReconciliationSlotDTO>` (no `ImportPreviewResponse.reconciliation`). Import **FATURA** → 1 slot, candidatos = pagamentos do extrato (`pagamento de fatura%`) na janela ±60d, `suggestedId` = o que casa por valor (`amountsMatch` com `netTotal` dos itens). Import **EXTRATO** → 1 slot por pagamento parseado (`paymentIndex`), candidatos = faturas confirmadas por perto (total via `sumNetByImportSession`).
  - **Confirm dirigido por decisão** (`ConfirmImportRequest { transactions, reconcileExtratoPaymentIds }`; `ParsedTransactionDTO.reconciled`): **FATURA** apaga exatamente os `reconcileExtratoPaymentIds` aprovados; **EXTRATO** pula (não cria) os DTOs com `reconciled=true`. Se `reconcileExtratoPaymentIds` vier `null` (legado), cai no auto `reconcileBillPayment` por valor.
  - **Tela dedicada (histórico):** `GET /reconciliation` (pagamentos não conciliados + candidatos + sugestão) e `POST /reconcile { extratoPaymentId, faturaSessionId }` (apaga o pagamento). Front: `feature/import/reconciliation` (rota `/import/reconciliation`, botão "Conciliar pagamentos" na página Importar).
  - Como o pagamento já nasce `ignored`, os totais ficam corretos em qualquer ordem mesmo sem conciliar; a conciliação limpa a linha duplicada.
- Marca a sessão `CONFIRMED` e **zera o `preview_json`**. **Usa a lista enviada pelo cliente** (com as edições do preview), não o JSON persistido.

`cancel` (→ CANCELLED), `deleteSession` (apaga sessão + transações + itens de revisão), `getHistory`.

### Parsers (`service/parser/`)

- **`DocumentTypeDetector`** — heurística `detect(text)` → `FATURA` (período vigente, vencimento…) ou `EXTRATO` (total de entradas/saídas, conta…).
- **`NubankExtratoParser`** — percorre linhas após "Movimenta…", rastreia blocos entradas/saídas; trata "Pagamento de fatura" como `INTERNAL` **`ignored=true` por padrão** (fica registrado mas **não conta** nos totais/relatórios, evitando dupla contagem com os itens da fatura mesmo se a reconciliação não rodar), crédito em conta, boletos, transferências multilinha; nomes de mês PT. **RDB vira investimento** (`type=INVESTMENT`, `autoClassification=INVESTMENT`, `included=true`): Aplicação RDB → `investmentDirection=CONTRIBUTION` (aporte); Resgate RDB → `investmentDirection=REDEMPTION` (resgate). **`resolveType` sobrepõe o bloco pela palavra "recebida"/"enviada"** (correção de tipo). Retorna `ParseResult(periodStart, periodEnd, transactions)`.
- **`NubankFaturaParser`** — período + **data de vencimento completa** (dia/mês/ano; trata virada de ano) exposta como `ParseResult.dueDate`; seções por portador ("Welisson W Oliveira", "Rosangela Oliveira"); filtra "Pagamentos"; parcela `Parcela n/n` → `installmentInfo`; estorno (`-R$`) → `INCOME`.

**Competência (regime de caixa):** no `parseAndPreview`, cada item recebe `competenceDate` = **vencimento** (fatura) ou a própria `date` (extrato/RDB). No preview da fatura há um controle **"Competência (mês de pagamento)"** que redefine em lote; o `confirm` persiste `competence_date` (fallback `date`). Dashboard/Relatórios/metas agregam por `COALESCE(competence_date, date)`. Ver [transacoes.md](./transacoes.md).

### Endpoints — `ImportController` (`/api/import`)

- `POST /parse` (multipart `file`, opcional `documentType`) → `ImportPreviewResponse` (201).
- `GET /{id}/preview` → `ImportPreviewResponse` reconstruído do `preview_json` (retomar sessão `PENDING`; 409 se já confirmada/cancelada).
- `PUT /{id}/preview` (body `List<ParsedTransactionDTO>`) → regrava o `preview_json` com as edições em andamento (autosave; 204; 409 se não-pendente).
- `POST /{id}/confirm` (body `ConfirmImportRequest { transactions, reconcileExtratoPaymentIds }`) → persiste confirmados + aplica a conciliação.
- `GET /reconciliation` → `List<PendingReconciliationDTO>` (pagamentos do extrato ainda não conciliados + candidatos de fatura + sugestão).
- `POST /reconcile` (body `ReconcileRequest { extratoPaymentId, faturaSessionId }`) → apaga o pagamento (substitui). 204.
- `POST /{id}/cancel`.
- `GET /history` → `List<ImportSessionResponse>` com contagem de transações.
- `DELETE /{id}` → apaga a sessão e o que veio dela (204).

### DTO central — `ParsedTransactionDTO`

Campos: date, description, amount, type, cardHolder, installmentInfo, normalizedDescription, budgetGroup, `investmentDirection`, `ignored`, categoryId/Name, notes, knownPersonId, `needsReview`, **`included`** (default true), `autoClassification` (badge `OWN_TRANSFER`/`INVESTMENT`/`INTERNAL`/`INTERNAL_FATURA_EXISTS`). **Serve também de body do confirm.**

## Frontend (`feature/import/`, `import.service`)

`import.service`: `parse(file)` → `POST /api/import/parse`; `savePreview(sessionId, txs)` → `PUT /api/import/{id}/preview` (autosave); `confirm(sessionId, txs, reconcileExtratoPaymentIds?)` → `POST /api/import/{id}/confirm`; `getReconciliation()` → `GET /api/import/reconciliation`; `reconcile(paymentId, faturaId)` → `POST /api/import/reconcile`; `cancel`, `getHistory`, `deleteSession`.

- **`upload`** (`app-upload`) — página `/import`. Drag-and-drop + input, **só PDF**. `upload()` → `parse()` e navega para `/import/preview` passando `preview` no `state` do router. Também embute o **histórico** (`groupByMonth`, status CONFIRMED/CANCELLED/PENDING), `goToTransactions()` (seta o período global e navega) e exclusão via `ConfirmDialogComponent`. Injeta `PeriodService`. **Sessões `PENDING` têm botão "Retomar"** (`resumeSession` → `getPreview` → navega ao preview); se o preview expirou, mostra snackbar e recarrega o histórico.
- **`preview`** (`app-preview`) — lê `preview` do `state` (sem ele, redireciona para `/import`). Tabela Material editável (colunas: included, date, description, amount, type, budgetGroup, direction, category, notes, **actions**) com `CategoryService.getAll()`. **Paridade de ações com a lista de Transações:** o **Tipo** é um `mat-select` editável por linha (`onTypeChange` normaliza grupo/direção/categoria conforme o tipo); linhas com `needsReview` mostram o selo **"Revisar"** + botão **check "Confirmar revisão"** (`confirmReviewRow` → `needsReview=false` + autosave), como na lista. A coluna **actions** tem um botão de **editar tudo** por linha (`openRowEditor`) que abre o `TransactionEditDialogComponent` (com `hidePropagate: true` — nada foi persistido ainda) para corrigir os campos que os controles inline não cobrem (**valor, data, descrição, competência por linha**); o patch retorna e é escrito de volta na `ParsedTransaction` em memória + `persistEdits()`; `budgetGroups`/`directions` com tooltips; `autoClassificationLabel/Tooltip`. **Autosave:** cada alteração chama `persistEdits()` → `importService.savePreview()` (controles discretos na hora via `onEdit()`; texto de *notes* com `debounceTime(600)` via `onNotesInput()`), então sair da tela/atualizar/fechar não perde nada — o "Retomar" traz as edições de volta. Flag `finalized` (setada em `confirm()`/`cancel()`) faz o autosave virar no-op depois de confirmar/cancelar (o backend já zerou o `preview_json`). Categoria por linha usa `app-category-select` (com **"➕ Nova categoria…"** inline). **Painel de conciliação:** quando `preview.reconciliation` tem slots, mostra um card com a sugestão pré-selecionada (`reconcileSelection`) e opção "Não conciliar"; no `confirm()` monta o `reconcileExtratoPaymentIds` (FATURA) ou marca `reconciled` nos DTOs (EXTRATO). `confirm()` → `importService.confirm()` → `/dashboard`; `cancel()`.
- **`reconciliation`** (`app-reconciliation`, rota `/import/reconciliation`) — tela dedicada: lista os "Pagamento de fatura" não conciliados com `mat-select` de fatura (sugestão pré-selecionada) + botão "Conciliar" → `POST /reconcile` → remove da lista.
- **`history`** (`app-import-history`) — lista de sessões. **Nota: rota `/import/history` redireciona para `/import`** (o histórico está embutido no upload); o componente existe mas não é roteado.

## Fluxo ponta-a-ponta

Upload PDF → parse+classificação → preview (usuário ajusta tipo/categoria/inclusão) → confirm → transações salvas; os desconhecidos ficam com `needs_review=true` e são resolvidos **inline** na [lista de transações](./transacoes.md) → dashboard atualizado.

## Regras de domínio

- **Pagamento de fatura** (`INTERNAL`) chega `included=true` mas **`ignored=true`** — registrado como fluxo de caixa, porém fora dos cálculos (dupla proteção: mesmo sem reconciliação, não infla os totais). Se já existir uma fatura confirmada que **case por valor** (`INTERNAL_FATURA_EXISTS`), chega **desmarcado** (`included=false`) e, no confirm, é pulado de vez. Ao confirmar a fatura, `reconcileBillPayment` apaga o lançamento correspondente do extrato. **A reconciliação casa por valor + janela de data, nas duas ordens de importação.**
- Transferências próprias (`OWN_TRANSFER`) chegam como `ignored` (não contam como receita).
- **Fatura vira/ano:** `NubankFaturaParser` ancora o ano de fechamento pelo mês do vencimento — fatura que **fecha em dezembro e vence em janeiro** mantém o fechamento no ano anterior (senão a janela de reconciliação erraria).
- RDB (`Aplicação`/`Resgate`) chega classificado como investimento e **marcado para incluir** (alimenta o dashboard). O `parseAndPreview` preserva essa classificação (não roda os classificadores por cima quando `autoClassification=INVESTMENT`).
- Classificação de estabelecimento e aprendizado: ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).
- Pré-preenchimento de apelido no import vem de `merchant_display_names` (ver [transacoes.md](./transacoes.md)).

## Onde mexer

- Novo layout/banco de PDF → novo parser em `service/parser/` + ajuste no `DocumentTypeDetector` e no dispatch de `parseAndPreview`.
- Nova coluna no preview → `ParsedTransactionDTO` + tabela do `preview.component`.
- Regra de inclusão automática → `included`/`needsReview` nos parsers e em `parseAndPreview`.

## Testes relevantes

`TransactionImportServiceTest` (confirm persiste só `included`, usa dados do cliente e não o JSON, persiste `needs_review`/`ignored`/`investmentDirection` na Transaction, pula excluídos; `getPreview` devolve o preview persistido de sessão `PENDING` e recusa sessão não-pendente / sem JSON; `updatePreview` grava as edições numa sessão `PENDING` e round-trips pelo `getPreview`, recusa sessão não-pendente e de outro usuário; **reconciliação por valor nas duas ordens** — fatura apaga o pagamento do extrato quando o valor casa e não apaga quando difere; extrato pula os DTOs `reconciled`; confirm FATURA apaga só os ids aprovados; `reconcile` apaga o pagamento validando posse; `buildReconciliation` sugere o candidato certo via `getPreview`), `NubankExtratoParserTest` (pagamento de fatura vem `ignored`) e `NubankFaturaParserTest` (fixtures reais `extrato.pdf`/`fatura.pdf`; fechamento dez/vencimento jan mantém ano anterior), `import.service.spec` e `preview.component.spec` (autosave: `onEdit` chama `savePreview`; vira no-op após confirm/cancel; **`openRowEditor` escreve o resultado do dialog de volta na linha parseada e autossalva**).
