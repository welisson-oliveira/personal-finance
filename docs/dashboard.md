# Dashboard

Visão mensal 50/30/20: receita real, despesas por grupo, investimentos, saldo e **insights do mês**. O período vem do seletor de mês global.

## Backend — `DashboardService.getMonthly(user, year, month)`

Retorna `DashboardResponse`. Cálculos (mês corrente do usuário). **As agregações usam a competência (`COALESCE(competence_date, date)`), não a data da compra** — compras de cartão contam no mês de pagamento da fatura (regime de caixa). Ver [transacoes.md](./transacoes.md#regras-de-domínio).

| Métrica                                              | Cálculo                                                                                                              |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `entradas`                                           | soma de `type=INCOME` **exceto reembolsos** (exclui `ignored` e `reimbursement`) — "Entradas do mês / Receita disponível"                                    |
| `entradasBreakdown`                                  | **"De onde veio o dinheiro"**: entradas do mês agrupadas por **categoria** (`findIncomeWithCategoryInPeriod`, maior→menor, com "Sem categoria"). Receita agora pode ter categoria (ver [transacoes.md](./transacoes.md)); usa o valor bruto (como `entradas`). **Reembolsos ficam de fora** (não são receita). Drill-down expansível sob o card de Entradas |
| `despesasEssenciais` / `despesasNaoEssenciais`       | soma de `type=EXPENSE` por `budget_group` (usa `userShare` quando `shared`, exclui `ignored`), **líquida de reembolsos**: um `INCOME reimbursement=true` da mesma faixa entra como **despesa negativa** (`−amount`) na sua soma                        |
| `totalDespesas`                                      | **todas** as despesas não-ignoradas do mês (`sumAllExpenseByUserIdAndDateBetween`, com `userShare`), **líquida de reembolsos** — não só as com grupo 50/30/20 |
| `despesasSemGrupo`                                   | `totalDespesas − essenciais − naoEssenciais` — despesas fora do 50/30/20 (ex.: pagamento de fatura contabilizado no mês de transição). Card "Outras despesas (sem grupo)" só quando > 0 |
| `aplicado`                                           | **aporte líquido**: `INVESTMENT/CONTRIBUTION` − `INVESTMENT/REDEMPTION` — "Aplicado em investimentos"                |
| `resgatado`                                          | soma de `INVESTMENT/REDEMPTION` — "Resgatado dos investimentos"                                                      |
| `resultado`                                          | entradas − totalDespesas — "Resultado do mês" (performance **isolada** do mês; agora conta toda despesa não-ignorada) |
| `saldoAcumulado`                                     | **Saldo Geral (em conta)**: `openingBalance` (saldo inicial do usuário) **+** `sumAccumulatedBalanceBetween(openingBalanceDate, fim do mês)` = Σ receitas − Σ despesas − aportes + resgates (competência entre a data do saldo inicial e o fim do mês). Semeado pelo saldo inicial para **bater com o valor real da conta**, não só o líquido das transações desde o cadastro. Sem saldo inicial, cai para `1970-01-01` (soma tudo desde zero) |
| `pagamentosFaturaIgnorados`                          | soma dos "Pagamento de fatura" **ainda ignorados** no mês (`sumIgnoredBillPaymentsInPeriod`). Quando > 0, o Dashboard mostra um aviso — sinal do **furo de transição** (um pagamento ignorado que ainda existe = não foi conciliado a nenhuma fatura) |
| `salarioEsperado`                                    | `monthlyNetIncome` do usuário (0 se não configurado) — piso de renda do **salário previsto** (Opção A)              |
| `resultadoPrevisto`                                  | `receitaProjetada − totalDespesas`, onde `receitaProjetada = max(entradas, salarioEsperado)` **só no mês corrente** (`YearMonth.now()`); nos demais meses = `entradas`. Não mascara estouro (piso é o salário, não infinito) |
| `usandoSalarioPrevisto`                              | `true` quando a projeção acrescenta renda além da real (`receitaProjetada > entradas`) — o mês corrente ainda não teve o salário importado. Dispara o selo "previsto" e a troca do `baseLabel` |
| `rendaBase`                                          | base do 50/30/20: **`max(entradas, monthlyNetIncome)` em todo mês** — o salário configurado é **piso** da base, então um mês com renda não importada não colapsa a base e explode os %. Se ambos forem 0, base 0 (percentuais "—") |
| `percentual{Essenciais,NaoEssenciais,Investimentos}` | cada grupo / `rendaBase` (investimentos usa `aplicado` líquido)                                                     |
| `breakdown`                                          | **drill-down do 50/30/20**: `essenciais` e `naoEssenciais` — categorias que compõem cada bucket de despesa (`List<CategoryTotalResponse>`, maior→menor, com bucket "Sem categoria"). Reusa `findExpensesWithCategoryInPeriod` (uma única busca, compartilhada com `insights`) agrupando por `budget_group`→categoria. **`netCategoriesForGroup` abate os reembolsos** (`findReimbursementsWithCategoryInPeriod`) por categoria; categorias que ficam ≤ 0 somem. Investimentos não têm categoria; o detalhe do 20% usa `aportes`/`resgatado`. |
| `insights`                                           | **"Insights do mês"** (substitui os antigos "Destaques"): leitura acionável do mês — ver seção abaixo (`buildInsights`) |

> **Meta em R$ + folga/estouro** são calculados no **frontend** a partir de `rendaBase`: meta = 50%/30%/20% × base; folga/estouro = meta − realizado. Essenciais/não-essenciais são **teto** (acima = estouro); investimentos são **piso** (abaixo = "faltam R$ X para os 20%"). Sem novo backend para isso.

> A Home segue os 4 blocos da visão de produto: **quanto entrou / para onde foi / está seguindo o 50-30-20 / quanto sobrou**.
>
> **Reembolso (contra-lançamento).** Um `INCOME` marcado como `reimbursement` **não** entra em `entradas`/`entradasBreakdown`, é **subtraído** do seu grupo/categoria em `despesasEssenciais`/`despesasNaoEssenciais`/`totalDespesas`/`breakdown`, mas **continua contando** como caixa em `saldoAcumulado` (o dinheiro entrou de fato). Efeito: `resultado` e `saldoAcumulado` sobem o valor do reembolso; a faixa 50/30/20 correspondente cai. Ver [transacoes.md](./transacoes.md).

### Insights do mês — `buildInsights`

Substitui a antiga seção "Destaques" (contadores de supermercado/delivery/assinaturas/PIX) por uma **leitura acionável** do mês. Objeto aninhado `insights` (`DashboardResponse.Insights`), com 6 blocos — cada um é opcional e o front só renderiza o que veio preenchido. Reusa a busca de despesas do mês corrente (`expenses`, já usada pelo `breakdown`) e faz **uma** busca extra dos **3 meses anteriores** (`findExpensesWithCategoryInPeriod` na janela `ym-3 .. ym-1`, bucketizada por competência) para o comparativo e as recorrências.

| Bloco                         | O que traz                                                                                                                                                                          |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `maioresGastos`               | **top 5** gastos individuais do mês (`List<TopExpenseResponse>`, `effectiveAmount` desc) — onde o dinheiro vaza                                                                      |
| comparativo (`totalMesAtual`/`totalMesAnterior`/`variacaoPercentual`/`categoriaQueMaisSubiu…`) | total do mês vs. mês anterior (Δ%; `null` sem base anterior) + a **categoria que mais subiu** em R$ (maior alta absoluta atual − anterior)                                          |
| `recorrentes` + `totalRecorrente` | **assinaturas & recorrentes**: nome normalizado presente no mês corrente **e** em ≥1 dos 3 meses anteriores, **ou** classificado como "Assinatura" (regra `MerchantRule`/categoria). `nova=true` = assinatura vista pela 1ª vez. `totalRecorrente` = comprometido mensal (top 8 por valor) |
| ritmo (`mesCorrente`/`diasDecorridos`/`diasNoMes`/`projecaoFechamento`) | **só no mês corrente**: `projecaoFechamento = totalDespesas × diasNoMes / diasDecorridos` (ritmo de gasto). `null` fora do mês corrente                                              |
| `metasEstouradas`             | metas de orçamento com `remaining < 0` (cruza com `BudgetGoalService.findAll`, reusa o roll-up de subcategorias), maior `percentual` primeiro                                       |
| `pequenosGastos`              | grupos de gasto **pequeno e frequente** (mesmo nome normalizado, ≥3 ocorrências, cada uma ≤ R$50) que somados pesaram — top 5 por total                                             |

> O "nome" de recorrentes/pequenos usa o `normalized_description` (chave de recorrência/merchant, ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md)); a competência bucketiza o histórico por mês.

### Resultado do mês × Saldo Geral, e o furo de transição

O regime de caixa faz o **Resultado do mês** oscilar (salário num mês, fatura no mês do pagamento). O **Saldo Geral (acumulado)** suaviza isso mostrando o saldo corrido em conta até o fim do mês — o negativo de um mês aparece coberto pelo acumulado do anterior.

**Saldo inicial (opening balance).** Para o Saldo Geral igualar o **saldo real da conta** (e não apenas o líquido das transações importadas), o usuário informa em Configurações um `openingBalance` numa `openingBalanceDate` de referência (ver [autenticacao-e-usuarios.md](./autenticacao-e-usuarios.md)). O acumulado passa a somar as movimentações **a partir dessa data**, sobre esse saldo base.

**Salário previsto (Opção A).** No **mês corrente**, se a fatura já caiu (dia 8) mas o extrato com o salário ainda não foi importado (dia 31), o Resultado apareceria falsamente no vermelho. Para evitar esse "limbo de caixa", `receitaProjetada = max(entradas, salarioEsperado)` usa o salário configurado como **piso** de renda. O Dashboard mostra o `resultadoPrevisto` com um selo **"previsto"** e a renda real importada até então como sub-linha; o 50/30/20 usa a base projetada. Só vale para o mês corrente e não mascara estouro (o piso é o salário, não infinito). Os campos reais (`entradas`, `resultado`) seguem intactos.

**Aviso de cobertura.** Quando `resultado < 0` mas `saldoAcumulado >= 0`, o Dashboard mostra uma nota discreta: o déficit isolado do mês está **coberto pelo Saldo Geral** acumulado — o caso da fatura de R$10 mil paga com o windfall do mês anterior.

No **mês de início do uso** há um "furo": o "Pagamento de fatura" do extrato entra `ignored=true` (para não duplicar com os itens da fatura), mas se a fatura daquele período nunca foi importada, aquela saída real não conta em lugar nenhum → Resultado/Saldo inflados. O tratamento é **enxuto** (sem status novo): o Dashboard **avisa** quando há `pagamentosFaturaIgnorados > 0`, e na tela de Transações o usuário **des-ignora** o pagamento ("Contabilizar neste mês"). Como `resultado`/`saldoAcumulado` contam **toda** despesa não-ignorada, des-ignorar já faz a saída entrar — sem precisar de grupo 50/30/20 (ela aparece em "Outras despesas (sem grupo)"). Ver [transacoes.md](./transacoes.md).

Queries em `TransactionRepository` (`sumIncomeByUserIdAndDateBetween`, `sumInvestmentByDirectionAndDateBetween`, `sumExpenseByBudgetGroupAndDateBetween`, `findExpensesWithCategoryInPeriod`) — todas excluem `ignored`.

### Endpoint — `DashboardController`

- `GET /api/dashboard/monthly?year=&month=` → `DashboardResponse`.

## Frontend

- **`feature/dashboard`** (`app-dashboard`) — injeta `DashboardService` + `PeriodService`.
  - **Recarrega ao mudar o mês global:** `effect(() => { period.period(); load(); })`.
  - `load()` → `dashboard.service.getMonthly(year, month)`. Helpers: `fmt()` (BRL), `clamp()` (barra 50/30/20 limitada a 100), `hasBase()`, `baseLabel()` ("renda do mês" / "salário configurado" / **"salário previsto"** quando `usandoSalarioPrevisto`).
  - **Salário previsto:** quando `usandoSalarioPrevisto`, o card "Resultado do mês" mostra `resultadoPrevisto` com o selo **"previsto"** (`.previsto-badge`) e a linha "Real importado até agora" (`.metric-sub`); o realce negativo passa a seguir o valor projetado.
  - **Aviso de cobertura:** `@if (resultado < 0 && saldoAcumulado >= 0)` → nota `.coverage-notice` de que o déficit do mês está coberto pelo Saldo Geral.
  - Quando não há base de renda, os percentuais mostram "—" com aviso; percentual > 100% em vermelho.
  - **50/30/20 preciso:** por bucket mostra a **meta em R$** (`meta(fração)`) e a **folga/estouro** (`folga(realizado, fração)`); cada bucket **expande** (`toggle('ess'|'nao'|'inv')`) para listar as categorias que o compõem (do `breakdown`) com **% do bucket** (`bucketPct(total, bucketTotal)`). O bucket de investimentos expande para aportes/resgates brutos.
- **Seletor de mês global** — `layout/month-selector` (`app-month-selector`) + `PeriodService` (`core/services/`): estado `signal<{year,month}>` persistido em `localStorage` (`selected_period`); `monthString()`, `label()`, `prev()`/`next()`, `isCurrentMonth()`/`goToCurrent()` (botão "mês atual"). É o **único controle** que dispara os `effect()` do dashboard e da lista de transações.

## Fluxo ponta-a-ponta

Usuário troca o mês na toolbar → `PeriodService.set()` → `effect()` do dashboard recarrega → `getMonthly` recalcula. O salário líquido (Configurações, ver [autenticacao-e-usuarios.md](./autenticacao-e-usuarios.md)) entra como `rendaBase` quando o mês não tem renda registrada.

## Onde mexer

- Nova métrica → `DashboardService.getMonthly` (+ query no repo se preciso), `DashboardResponse`, `dashboard.component`.
- Mudar a base do 50/30/20 → lógica de `rendaBase` em `getMonthly`; meta em R$/folga são derivadas no `dashboard.component` (`meta`/`folga`).
- Drill-down do 50/30/20 → `buildBudgetBreakdown`/`categoriesForGroup` (backend) + `DashboardResponse.Breakdown` + template (`toggle`/`bucketPct`).
- Saldo Geral → `sumAccumulatedBalanceBetween` (query `CASE` com sinal) + `openingBalance`/`openingBalanceDate` do usuário + `saldoAcumulado` + card `accumulated`.
- Salário previsto (Opção A) → lógica `mesCorrente`/`receitaProjetada` em `getMonthly` + `salarioEsperado`/`resultadoPrevisto`/`usandoSalarioPrevisto` + selo "previsto" no card Resultado.
- Aviso de cobertura → `@if (resultado < 0 && saldoAcumulado >= 0)` no template (`.coverage-notice`).
- Furo de transição → `sumIgnoredBillPaymentsInPeriod` + `pagamentosFaturaIgnorados` + aviso; `sumAllExpenseByUserIdAndDateBetween` alimenta `totalDespesas`/`resultado`/`despesasSemGrupo`.
- Netting de reembolso → as somas de despesa no repo (`sumExpenseByBudgetGroup…`/`sumExpenseByCategory…`/`sumAllExpense…`) já incluem `INCOME reimbursement=true` como `−amount`; o drill-down abate via `netCategoriesForGroup` + `findReimbursementsWithCategoryInPeriod`. `sumIncome…` e `saldoAcumulado` **não** mudam (entrada exclui reembolso; caixa inclui).
- Insights do mês → `buildInsights` (backend) + `DashboardResponse.Insights` (+ records `RecurringItem`/`GoalExceeded`/`SmallExpenseGroup`) + `dashboard.model.ts` + a grade `.insights-grid` no template. Cruzamento com metas via `BudgetGoalService.findAll`; recorrências/pequenos via `normalized_description` + histórico dos 3 meses anteriores.

## Testes relevantes

`DashboardServiceTest` (entradas/totais/resultado, percentuais, aporte líquido = aporte − resgate, renda zero, precedência/fallback do salário líquido, breakdown do 50/30/20 por categoria maior→menor com "Sem categoria", **saldoAcumulado + despesasSemGrupo + pagamentosFaturaIgnorados**, **netting de reembolso** (`DashboardControllerTest.monthly_nets_reimbursement_against_its_category_and_group`: reembolso fora de `entradas`, abatido da faixa/categoria, somando no `saldoAcumulado`), **salário previsto no mês corrente** (`usandoSalarioPrevisto`, `resultadoPrevisto`, base projetada) **e não-projeção em mês passado**, **saldoAcumulado semeado pelo `openingBalance`**, **insights**: maiores gastos ordenados, comparativo + categoria que mais subiu, recorrentes + assinatura nova, projeção do mês corrente e ausência dela em mês passado, metas estouradas, pequenos gastos agrupados).
