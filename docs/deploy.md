# Deploy — alfa fechada (gratuito)

Guia para subir a aplicação de graça para poucos usuários, usando três serviços de free tier:

| Camada | Serviço | Por quê |
| --- | --- | --- |
| Banco (PostgreSQL) | **Neon** | Persistente, serverless, ~3 GB, escala a zero, não expira |
| Backend (Spring Boot / Docker) | **Koyeb** | 1 instância sempre-livre, resume rápido (~1–5 s) do scale-to-zero |
| Frontend (Angular estático) | **Cloudflare Pages** | Estático grátis, HTTPS automático |

> Alternativa ao Koyeb: **Render** (mesmo esquema, mas cold start de 30–60 s). O Postgres próprio de ambos **não** serve para dados persistentes (Render apaga em 30 dias; Koyeb dá só 5 h/mês de compute) — por isso o banco fica no Neon.

> ⚠️ Free tiers mudam. Confira os limites atuais de cada provedor na hora de subir.

---

## 1. Banco — Neon

1. Crie um projeto em <https://neon.tech> e um database (ex.: `personalfinance`).
2. Copie os dados de conexão: host (`ep-xxx.neon.tech`), database, usuário, senha.
3. Neon exige SSL — usaremos `DB_PARAMS=?sslmode=require`.

## 2. Backend — Koyeb

1. Em <https://koyeb.com>, crie um serviço a partir do repositório GitHub, apontando para o diretório `backend/` (Dockerfile já existe).
2. Porta exposta: **8080**.
3. Variáveis de ambiente:

   | Variável | Valor | Observação |
   | --- | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` | ativa Postgres + Flyway (`validate`) |
   | `DB_HOST` | `ep-xxx.neon.tech` | do Neon |
   | `DB_PORT` | `5432` | |
   | `DB_NAME` | `personalfinance` | |
   | `DB_USER` | *(do Neon)* | |
   | `DB_PASSWORD` | *(do Neon)* | **secret** |
   | `DB_PARAMS` | `?sslmode=require` | obrigatório no Neon |
   | `JWT_SECRET` | *(gere — ver abaixo)* | **secret**, ≥ 256 bits |
   | `CORS_ALLOWED_ORIGINS` | `https://SEU-FRONT.pages.dev` | origem do frontend (passo 3) |
   | `DB_POOL_MAX` | `5` | opcional (padrão 5) |

   Gere um `JWT_SECRET` forte:

   ```bash
   openssl rand -base64 48
   ```

4. Deploy. Na primeira subida o Flyway aplica as migrações no Neon automaticamente.
5. Anote a URL pública do backend (ex.: `https://personal-finance-xxx.koyeb.app`).

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
   - `API_BASE_URL` = a URL do backend do passo 2 (ex.: `https://personal-finance-xxx.koyeb.app`)
4. Deploy. Anote a URL do site (ex.: `https://seu-front.pages.dev`) e coloque-a em `CORS_ALLOWED_ORIGINS` no Koyeb (passo 2) — depois redeploy do backend se necessário.

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
