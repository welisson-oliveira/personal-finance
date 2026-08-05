# Deploy — alfa fechada (gratuito)

Guia para subir a aplicação de graça para poucos usuários, usando três serviços de free tier:

| Camada | Serviço | Por quê |
| --- | --- | --- |
| Banco (PostgreSQL) | **Neon** | Persistente, serverless, ~3 GB, escala a zero, não expira |
| Backend (Spring Boot / Docker) | **Render** | Web Service Docker grátis a partir do repo |
| Frontend (Angular estático) | **Cloudflare Pages** | Estático grátis, HTTPS automático |

> **Koyeb saiu de cena:** foi adquirida pela Mistral (fev/2026) e **encerrou o free tier para novos usuários**. Por isso o backend vai no **Render** (free web service, cold start de 30–60 s após hibernar). O Postgres próprio do Render **expira em 30 dias**, então o banco fica no **Neon** (persistente).
>
> Alternativa sem cold start, mais setup: **Google Cloud Run** (Docker, escala a zero, free tier real) + Neon.

> ⚠️ Free tiers mudam. Confira os limites atuais de cada provedor na hora de subir.

---

## 1. Banco — Neon

1. Crie um projeto em <https://neon.tech> e um database (ex.: `personalfinance`).
2. Copie os dados de conexão: host (`ep-xxx.neon.tech`), database, usuário, senha.
3. Neon exige SSL — usaremos `DB_PARAMS=?sslmode=require`.

## 2. Backend — Render (imagem do ghcr)

O Render **não builda do repositório**: ele puxa a imagem pronta que o CI publica no ghcr (ver [5. Entrega contínua](#5-entrega-contínua--rc-release-deploy-e-rollback)). O deploy é disparado pelo workflow `Deploy`, não pelo Render.

1. Em <https://render.com>, **New → Web Service → Deploy an existing image from a registry**.
   - **Image URL:** `ghcr.io/<owner>/personal-finance-backend:latest`
   - **Registry Credential:** crie uma com username = seu usuário do GitHub e password = um **PAT classic** com escopo `read:packages` (a imagem é privada). Um serviço já ligado a um repositório **não** vira image-based no lugar — crie um novo.
   - **Instance Type:** Free
   - **Health Check Path:** `/api/actuator/health` (gateia o corte de tráfego — ver rollback)
   - **Auto-Deploy:** **OFF** (o workflow `Deploy` é o único gatilho)
2. **Porta:** não precisa configurar — o Render injeta `PORT` e a app já lê `${PORT:8080}` (`application.yml`). Em dev/docker segue 8080.
3. Pegue o **Service ID** (`srv-...`, na URL do serviço) e uma **API Key** (Account Settings → API Keys) e cadastre nos secrets do repo (Settings → Secrets and variables → Actions): `RENDER_SERVICE_ID` e `RENDER_API_KEY`.
4. Variáveis de ambiente (Environment):

   | Variável | Valor | Observação |
   | --- | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` | ativa Postgres + Flyway (`validate`) |
   | `DB_HOST` | `ep-xxx.neon.tech` | **host direto do Neon (sem `-pooler`)** |
   | `DB_PORT` | `5432` | |
   | `DB_NAME` | `neondb` | do Neon |
   | `DB_USER` | `neondb_owner` | do Neon |
   | `DB_PASSWORD` | *(do Neon)* | **secret** |
   | `DB_PARAMS` | `?sslmode=require` | obrigatório no Neon (só isto — nada de `channel_binding`) |
   | `JWT_SECRET` | *(gere — ver abaixo)* | **secret**, ≥ 256 bits |
   | `CORS_ALLOWED_ORIGINS` | `https://SEU-FRONT.pages.dev` | origem do frontend (passo 3) |
   | `DB_POOL_MAX` | `5` | opcional (padrão 5) |

   > **Neon:** use o host **direto** (sem `-pooler`). O pooler é PgBouncer em modo *transaction* e quebra os prepared statements do driver JDBC. Se precisar mesmo do pooler, use `DB_PARAMS=?sslmode=require&prepareThreshold=0`.

   Gere um `JWT_SECRET` forte:

   ```bash
   openssl rand -base64 48
   ```

5. O primeiro deploy vem do pipeline (ver [Bootstrap](#bootstrap-ovo-e-galinha)). Na primeira subida o Flyway aplica as migrações no Neon automaticamente.
6. Anote a URL pública do backend (ex.: `https://personal-finance-xxx.onrender.com`).

> **Memória:** o free tier tem 512 MB. O `Dockerfile` já limita a JVM (`-XX:MaxRAMPercentage=70 -XX:+UseSerialGC`). Se ver `OutOfMemory` no boot, reduza ainda mais ou suba de plano.

## 3. Frontend — Cloudflare Pages

1. Em <https://pages.cloudflare.com>, conecte o repositório.
2. Configuração de build:
   - **Root directory:** `frontend`
   - **Build command:**
     ```bash
     npm ci && npm run build && printf '{"apiBaseUrl":"%s"}' "$API_BASE_URL" > dist/personal-finance-frontend/browser/assets/config.json
     ```
   - **Build output directory:** `dist/personal-finance-frontend/browser`
3. Variável de ambiente do build:
   - `API_BASE_URL` = a URL do backend do passo 2 (ex.: `https://personal-finance-xxx.onrender.com`)
4. Deploy. Anote a URL do site (ex.: `https://seu-front.pages.dev`) e coloque-a em `CORS_ALLOWED_ORIGINS` no Render (passo 2) — depois redeploy do backend se necessário.

### Como o frontend acha o backend

Em runtime o app lê `assets/config.json` (`ConfigService` + `apiUrlInterceptor`). Se `apiBaseUrl` estiver vazio, as chamadas `/api/...` ficam **relativas** (funciona no `npm start` com o `proxy.conf.json` e num deploy same-host com o Nginx). No Pages, o comando de build injeta a URL do backend em `config.json`, e o interceptor prefixa as chamadas — por isso o backend precisa liberar o CORS da origem do Pages.

## 4. Pós-deploy — importante

- **Backup do banco** (são dados financeiros): rode `pg_dump` periodicamente ou use o **branching/point-in-time** do Neon.
  ```bash
  pg_dump "postgresql://USER:PASS@ep-xxx.neon.tech/personalfinance?sslmode=require" > backup_$(date +%F).sql
  ```
- **Cold start:** no free tier o backend hiberna após inatividade; a primeira requisição depois disso demora alguns segundos. Aceitável para alfa fechada.
- **Escopo:** os parsers de importação cobrem **apenas Nubank** (extrato + fatura). Deixe isso claro para quem for testar.

## 5. Entrega contínua — RC, release, deploy e rollback

Como o código sai de `develop` e chega em produção, automatizado em `.github/workflows/`. **Ao mexer em qualquer workflow, atualize esta seção na mesma PR.**

```
feature/* ──PR──▶ develop ──(push)──▶ [CI verde] ──▶ RC  v<versão>-rc.<run>  (pre-release, NÃO faz deploy)
                     │
                release/*  ──PR──▶ main ──(merge)──▶ [Deploy]
                                                       ├─ build + push  ghcr.io/<owner>/personal-finance-backend:<versão> (+ :latest)
                                                       ├─ Render deploy dessa imagem via API ──▶ espera status "live"
                                                       │     ├─ live  → publica Release v<versão>
                                                       │     └─ falhou/timeout → run vermelho, SEM Release; versão anterior fica no ar
                                                       └─ health check /api/actuator/health gateia o corte de tráfego
```

- **Fonte da versão:** `backend/pom.xml` (`<version>`, sem `-SNAPSHOT`). Única fonte — dita a tag do RC, da imagem e da Release. Lida com `python3` (`xml.etree`), **não** com `xmllint` (o runner `ubuntu-latest` não traz mais o `libxml2-utils`).
- **Git Flow:** feature sai de `develop` e volta pra `develop`; produção só recebe via `release/* → main`. Nunca abra PR de feature para `main`.

### Workflows

| Workflow          | Arquivo        | Dispara em                                          | O que faz |
| ----------------- | -------------- | --------------------------------------------------- | --------- |
| CI                | `ci.yml`       | push/PR em `develop` e `main`                       | Spotless, testes (back), lint/format/test/build (front), build das imagens Docker (sem push). No push em `develop`, o job final **`release-candidate`** (gateado por `needs`) publica a pre-release `v<versão>-rc.<run>` — **só com o CI verde** |
| Deploy            | `deploy.yml`   | PR **fechado+mergeado** em `main`, head `release/*` | Build+push da imagem do backend no ghcr, deploy no Render via API, espera `live`, publica a Release |
| Rollback          | `rollback.yml` | manual (`workflow_dispatch`, input `version`)       | Redeploya um tag imutável anterior no Render |

A lógica "dispara deploy no Render + faz poll até `live`" fica em **`.github/scripts/render-deploy.sh`**, compartilhada por `deploy.yml` e `rollback.yml` (recebe `RENDER_API_KEY`, `RENDER_SERVICE_ID`, `IMAGE`; sai ≠ 0 se falhar ou estourar 20 min).

### Artefato: imagem imutável no ghcr

O deploy publica `ghcr.io/<owner>/personal-finance-backend` com a tag da **versão imutável** (ex. `0.0.1`) + `latest`. A imagem testada no CI é a **mesma** promovida — o Render puxa por tag, sem rebuild. Por isso o rollback é confiável: cada versão é um artefato guardado byte a byte. **Só o backend** passa por este pipeline (o frontend vai no Cloudflare Pages, seção 3).

### Rollback — duas camadas

1. **Automático (nativo do Render).** Com **Health Check Path = `/api/actuator/health`**, um deploy que não passa no health check **nunca recebe tráfego** — a versão anterior fica no ar (zero-downtime).
2. **Manual.** Para bug que passa no health check mas quebra em uso: **Actions → Rollback → Run workflow**, informe a versão anterior (ex. `0.0.1`). Redeploya aquele tag imutável, sem rebuild.

> ⚠️ **Migrations (Flyway) são o limite do rollback.** Ele volta o **código**, não o **schema** já migrado. Regra: **toda migration de release deve ser backward-compatible** (aditiva; nada de `DROP`/`RENAME` destrutivo que a versão anterior não entenda).

### Bootstrap (ovo e galinha)

O Render precisa de uma imagem existente para criar o serviço, mas a imagem só nasce no primeiro push. Ordem:

1. Fazer a imagem existir no ghcr: mergear um `release/* → main` (o build/push roda **antes** do passo do Render, então a imagem é publicada mesmo que o Render falhe por falta de config) — ou buildar/pushar manualmente uma vez.
2. Criar o serviço no Render (seção 2) e pegar o `RENDER_SERVICE_ID`.
3. Preencher os secrets `RENDER_API_KEY` e `RENDER_SERVICE_ID`.
4. Re-run do job `Deploy` (ou próximo release) → fica `live` e publica a Release.

### Verificar um deploy

- **Actions** → run `Deploy` verde, com `Deploy … is live` no log.
- **Packages** (ghcr) → `personal-finance-backend` com a tag da versão e `latest`.
- **Releases** → `v<versão>` publicada.
- `GET https://<backend>.onrender.com/api/actuator/health` → `{"status":"UP"}`.

## Rodando local (sem deploy)

```bash
cp .env.example .env         # ajuste as variáveis
docker compose up -d         # Postgres + backend + frontend (Nginx)
# frontend em http://localhost, backend em :8080
```

No modo local o `config.json` fica com `apiBaseUrl` vazio e o Nginx faz o proxy de `/api` — nada de CORS.
