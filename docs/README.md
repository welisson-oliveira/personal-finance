# Documentação por domínio

Esta pasta documenta a aplicação **por feature de negócio**, cruzando backend + frontend de cada domínio num só lugar. O objetivo é permitir implementar uma mudança lendo **só o doc do domínio relevante**, sem reanalisar o código inteiro.

> **Regra para quem for implementar:** antes de mexer num domínio, leia o doc correspondente. Ao mudar o comportamento, **atualize o doc** na mesma PR.

## Índice

| Domínio                     | Doc                                                                | Mexa aqui quando…                                                            |
| --------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| Autenticação e usuários     | [autenticacao-e-usuarios.md](./autenticacao-e-usuarios.md)         | login/registro, JWT, segurança, perfil (salário líquido)                     |
| Importação de PDFs          | [importacao-de-pdfs.md](./importacao-de-pdfs.md)                   | upload/preview de extrato/fatura, parsers Nubank, pipeline de import         |
| Classificação e aprendizado | [classificacao-e-aprendizado.md](./classificacao-e-aprendizado.md) | regras de estabelecimento, aliases, normalização, propagação (cross-cutting) |
| Fila de revisão             | [fila-de-revisao.md](./fila-de-revisao.md)                         | itens pendentes de classificação e aprendizado                               |
| Transações                  | [transacoes.md](./transacoes.md)                                   | lista, edição, apelido, exclusão de transações                               |
| Dashboard                   | [dashboard.md](./dashboard.md)                                     | métricas 50/30/20, destaques, seletor de mês global                          |
| Relatórios                  | [relatorios.md](./relatorios.md)                                   | evolução mensal e gasto por categoria (gráficos)                             |
| Categorias                  | [categorias.md](./categorias.md)                                   | CRUD de categorias, seletor com busca, merchant rules (leitura)              |
| Metas de orçamento          | [metas-de-orcamento.md](./metas-de-orcamento.md)                   | teto de gasto por categoria e acompanhamento mensal                          |
| Pessoas conhecidas          | [pessoas-conhecidas.md](./pessoas-conhecidas.md)                   | pessoas de PIX e o tratamento padrão de entrada                              |

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
Migrations em `backend/src/main/resources/db/migration/` (V1…V11). `jpa.open-in-view: false` — os services carregam as associações necessárias antes de retornar.

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

## Glossário de domínio

- **`type`** (`TransactionType`, único enum): `INCOME` | `EXPENSE`.
- **`income_type`** (String, só em receitas): `INCOME` (receita real) · `REIMBURSEMENT` (reembolso de gasto — contado à parte, nunca vira receita) · `OWN_TRANSFER` (transferência entre contas próprias — **excluída em todo lugar**) · `INVESTMENT` (resgate de aplicação → "resgatado" do dashboard). Em `KnownPerson` há também `ALWAYS_REVIEW`.
- **`budget_group`** (regra 50/30/20, só em despesas): `ESSENTIAL` (50%) · `NON_ESSENTIAL` (30%) · `INVESTMENT` (20% — aporte). Atenção: **`investido`** do dashboard vem de `budget_group=INVESTMENT`; **`resgatado`** vem de `income_type=INVESTMENT` (eixos diferentes de propósito).
- **`source`**: `MANUAL` | `EXTRATO` | `FATURA`.
- **Nome efetivo (effective-name):** `normalizedDescription` quando existe, senão `description`. É a chave de junção para propagação de classificação, histórico de receita, propagação de apelido e resolução de revisão (`TransactionRepository.findByUserIdAndEffectiveName`).
- **Regra Receita × Despesa (recorrente no UI e no backend):** receita carrega `income_type` e **não** tem `budget_group`/categoria; despesa carrega `budget_group`/categoria e **não** tem `income_type`.
