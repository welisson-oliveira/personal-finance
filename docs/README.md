# Documentação por domínio

Esta pasta documenta a aplicação **por feature de negócio**, cruzando backend + frontend de cada domínio num só lugar. O objetivo é permitir implementar uma mudança lendo **só o doc do domínio relevante**, sem reanalisar o código inteiro.

> **Regra para quem for implementar:** antes de mexer num domínio, leia o doc correspondente. Ao mudar o comportamento, **atualize o doc** na mesma PR.

## Índice

| Domínio                     | Doc                                                                | Mexa aqui quando…                                                            |
| --------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| Autenticação e usuários     | [autenticacao-e-usuarios.md](./autenticacao-e-usuarios.md)         | login/registro, JWT, segurança, perfil (salário líquido)                     |
| Importação de PDFs          | [importacao-de-pdfs.md](./importacao-de-pdfs.md)                   | upload/preview de extrato/fatura, parsers Nubank, pipeline de import         |
| Classificação e aprendizado | [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md) | regras de estabelecimento, aliases, normalização, propagação (cross-cutting) |
| Transações                  | [transacoes.md](./transacoes.md)                                   | lista, edição, apelido, exclusão de transações                               |
| Dashboard                   | [dashboard.md](./dashboard.md)                                     | métricas 50/30/20, insights do mês, saldo geral, seletor de mês global       |
| Relatórios                  | [relatorios.md](./relatorios.md)                                   | evolução mensal e gasto por categoria (gráficos)                             |
| Categorias                  | [categorias.md](./categorias.md)                                   | CRUD de categorias, seletor com busca, merchant rules (leitura)              |
| Metas de orçamento          | [metas-de-orcamento.md](./metas-de-orcamento.md)                   | teto de gasto por categoria, sugestão 50/30/20 e acompanhamento mensal       |
| Pessoas conhecidas          | [pessoas-conhecidas.md](./pessoas-conhecidas.md)                   | pessoas de PIX e o tratamento padrão de entrada                              |

**Operação (transversal):** [deploy.md](./deploy.md) — provisionamento gratuito (Neon/Render/Cloudflare), o **ambiente de produção (alfa)** e a **entrega contínua** (candidato por versão, release, deploy da imagem imutável no Render e rollback). Mexa aqui ao mudar qualquer workflow de `.github/workflows/`.

**Produto & guias:** [Visão do Produto](./Personal_Finance_Visao_do_Produto.md) (modelo de domínio simplificado) · [Guia: primeiro acesso](./guia-primeiro-acesso.md) (importar histórico sem saber o saldo inicial).

---

## Visão geral da arquitetura

**Backend** — Spring Boot 3.3.5 / Java 21, arquitetura em camadas:

```
controller/ (@RestController, @AuthenticationPrincipal User)
   → service/ (@Service, @Transactional, regras de negócio)
      → repository/ (Spring Data JPA)
         → model/entity/ (@Entity JPA)
```

DTOs (`dto/request`, `dto/response`) cruzam a fronteira do controller; **entidades nunca vazam para o cliente**. Transversais: `config/` (segurança/JWT/CORS/exceções), `service/parser/` (leitura de PDF). Pacote base: `com.personalfinance`.

**Frontend** — Angular 17 (standalone, sem NgModules) + Angular Material (tema indigo-pink). Bootstrap via `bootstrapApplication(AppComponent, appConfig)`. Todo HTTP é relativo a `/api/`.

## Stack e versões

|          |                                                                                                                                                                               |
| -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Backend  | Java 21, Spring Boot 3.3.5, Spring Data JPA, Spring Security, Flyway, PDFBox 3.0.3, JWT (jjwt 0.12.6), Lombok, MapStruct (presente, mapeamento hoje é manual), Testcontainers |
| Frontend | Angular 17.3, Angular Material/CDK 17.3, RxJS 7.8, TypeScript 5.4                                                                                                             |
| Banco    | PostgreSQL (H2 em modo PostgreSQL nos testes)                                                                                                                                 |

## Profiles (backend)

| Profile        | Banco                              | Flyway       | DDL           |
| -------------- | ---------------------------------- | ------------ | ------------- |
| `dev` (padrão) | PostgreSQL `localhost:5432`        | habilitado   | `validate`    |
| `prod`         | PostgreSQL (env vars obrigatórias) | habilitado   | `validate`    |
| `test`         | H2 em memória (modo PostgreSQL)    | desabilitado | `create-drop` |

Env vars: `SPRING_PROFILES_ACTIVE`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
Migrations em `backend/src/main/resources/db/migration/` (V1…V23; a `V21` adiciona a coluna `reimbursement` em `transactions` e `merchant_rules` — ver o glossário; a `V22` adiciona o feedback de anomalia; a `V23` corrige a normalização das merchant rules). **Versionamento/deploy:** `backend/pom.xml` é a fonte única da versão — cada PR para `develop` sobe a versão (ver [deploy.md](./deploy.md)). `jpa.open-in-view: false` — os services carregam as associações necessárias antes de retornar.

## Comandos

```bash
# Backend (dir backend/)
mvn spring-boot:run              # dev (requer postgres)
mvn test                         # testes
mvn test -Dtest=DashboardServiceTest#metodo
mvn spotless:apply               # formatar Java (rodar antes de commitar)
mvn spotless:check

# Frontend (dir frontend/)
npm start                        # dev server :4200 (proxy /api -> :8080)
npm test                         # Karma/Jasmine
npm run build
npm run format                   # Prettier (rodar antes de commitar)
npm run format:check
npm run lint

# Docker (raiz)
docker compose up -d postgres    # só o banco, para dev do backend
docker compose up -d             # stack completa
docker compose -f docker-compose.test.yml up -d   # banco de teste (:5433)
```

## Convenções

- **Backend:** DTO na fronteira do controller (nunca entidade); `@AuthenticationPrincipal User` em todo endpoint autenticado; ownership sempre validado no service (`AccessDeniedException` → 403); Lombok em entidades/DTOs; annotationProcessorPaths com **Lombok antes de MapStruct**.
- **Tratamento de erro (`GlobalExceptionHandler`):** validação de `@Valid` (`MethodArgumentNotValidException`/`ConstraintViolationException`) e body malformado → **400**; `IllegalArgumentException` → 400; `AccessDeniedException` → 403; autenticação → 401; `IllegalStateException` e violação de integridade referencial (`DataIntegrityViolationException`) → **409**; resto → 500. Todo endpoint devolve o mesmo envelope `{error, message, status, timestamp}`.
- **Frontend:** componentes **standalone** com `imports` explícito; estado global compartilhado via **signals** (`AuthService.currentUser`, `PeriodService.state`) persistidos em `localStorage`; telas que dependem do mês usam `effect(() => { period.period(); load(); })`; diálogos retornam resultado via `dialogRef.close(...)` e a lista faz a chamada de serviço no `afterClosed()`.
- **Formatação antes de commitar** (evita CI vermelho): `npm run format` + `format:check` (front) e `mvn spotless:apply` + `spotless:check` (back).
- **Git Flow:** feature branches saem de `develop` e voltam via PR para `develop`; nunca abrir PR de feature para `main`.

## Componentes/diretivas compartilhados (frontend `src/app/shared/`)

| Peça                      | Seletor               | Uso                                                                                                                    |
| ------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `CategorySelectComponent` | `app-category-select` | select de categoria com busca no painel + ícone/cor; two-way `[(value)]`. Usado em editar transação e resolver revisão |
| `CurrencyMaskDirective`   | `[appCurrencyMask]`   | máscara de moeda pt-BR (ControlValueAccessor). Usado em editar transação e configurações                               |
| `ConfirmDialogComponent`  | `app-confirm-dialog`  | diálogo de confirmação (`{message}` → boolean). Usado em upload, lista de transações, categorias                       |
| `AutofocusDirective`      | `[appAutofocus]`      | foca o host ao renderizar (`ngAfterViewInit`). Usado nos inputs inline (ex.: apelido) que o `autofocus` nativo ignora  |

## Glossário de domínio

- **`type`** (`TransactionType`, eixo único): `INCOME` (entrada/receita) · `EXPENSE` (despesa) · `INVESTMENT` (investimento). Substituiu o antigo trio `type`+`income_type`+`budget_group`.
- **`budget_group`** (só em `EXPENSE`, regra 50/30/20): `ESSENTIAL` (50%) · `NON_ESSENTIAL` (30%).
- **`investment_direction`** (só em `INVESTMENT`): `CONTRIBUTION` (aporte) · `REDEMPTION` (resgate). O 20% do dashboard usa o **líquido** (aportes − resgates).
- **`ignored`** (boolean, qualquer tipo): fora de **todos** os cálculos (ex.: transferência entre contas próprias). Substituiu o antigo `income_type=OWN_TRANSFER`.
- **`needs_review`** (boolean): importada mas sem classificação confiável — aparece com o selo "Revisar" na lista de transações e é resolvida **inline** (a antiga fila de revisão deixou de existir).
- **`reimbursement`** (boolean, só faz sentido em `INCOME`): marca uma entrada como **reembolso / contra-lançamento** — não é receita de verdade, é dinheiro que **abate um gasto** (ex.: o colega de casa devolvendo a parte dele da conta de luz). Um reembolso carrega **categoria + `budget_group`** como se fosse uma despesa e, em todas as visões de orçamento (Dashboard 50/30/20, Relatório "Onde vai seu dinheiro", Metas), é **subtraído** (despesa negativa) da sua categoria/faixa. Fica **fora** dos totais de entradas/renda-base, mas **ainda conta como dinheiro que entrou** no Saldo Acumulado (é caixa real). Aprendido por `MerchantRule` (ver [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md)): marcar um reembolso e propagar ensina a regra a marcar as próximas importações do mesmo recebedor.
- **`source`**: `MANUAL` | `EXTRATO` | `FATURA`.
- **Nome efetivo (effective-name):** `normalizedDescription` quando existe, senão `description`. É a chave de junção para propagação de classificação, histórico de receita, propagação de apelido e resolução de revisão (`TransactionRepository.findByUserIdAndEffectiveName`).
- **Regra por tipo (UI e backend):** `EXPENSE` carrega `budget_group` + categoria; `INVESTMENT` carrega `investment_direction`; `INCOME` normal não carrega nenhum dos dois — **exceto** quando marcado como `reimbursement`, aí carrega categoria + `budget_group` (a faixa do gasto que ele abate). Editar uma transação propaga tipo/categoria/grupo/direção/ignored/reembolso para as de mesmo nome efetivo (ver [transacoes.md](./transacoes.md)).
- **Sinal do extrato = verdade para entrada × saída:** crédito (dinheiro entra) é `INCOME`/`INVESTMENT REDEMPTION`; débito (dinheiro sai) é `EXPENSE`/`INVESTMENT CONTRIBUTION`. Uma regra de merchant aprendida pode **refinar** o tipo dentro da mesma direção, mas **nunca inverter** o sinal (uma receita não vira despesa) — ver a "trava por sinal" em [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md).
- **Insights do mês** (Dashboard): leitura acionável do mês (maiores gastos, comparativo, recorrentes, ritmo, metas estouradas, pequenos gastos). Substituiu os antigos "Destaques". Ver [dashboard.md](./dashboard.md).
- **Sugestão de metas (50/30/20):** motor determinístico que propõe metas por categoria a partir do histórico e as encaixa nos tetos das faixas. Ver [metas-de-orcamento.md](./metas-de-orcamento.md).
