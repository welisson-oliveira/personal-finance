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

## 2. Backend — Render

1. Em <https://render.com>, **New → Web Service**, conecte o repositório GitHub.
2. Configuração:
   - **Root Directory:** `backend`
   - **Runtime/Language:** Docker (detecta o `Dockerfile` automaticamente)
   - **Instance Type:** Free
   - **Health Check Path:** `/api/actuator/health`
3. **Porta:** não precisa configurar — o Render injeta `PORT` e a app já lê `${PORT:8080}` (`application.yml`). Em dev/docker segue 8080.
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

5. Deploy. Na primeira subida o Flyway aplica as migrações no Neon automaticamente.
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

## Rodando local (sem deploy)

```bash
cp .env.example .env         # ajuste as variáveis
docker compose up -d         # Postgres + backend + frontend (Nginx)
# frontend em http://localhost, backend em :8080
```

No modo local o `config.json` fica com `apiBaseUrl` vazio e o Nginx faz o proxy de `/api` — nada de CORS.
