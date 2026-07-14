# Testes de caixa-preta (Postman / Newman)

Coleção de testes **end-to-end de caixa-preta** da API do Personal Finance: sobe um usuário novo a cada execução, captura o JWT e exercita **todos os domínios** (auth, perfil, categorias, transações, dashboard, relatórios, metas, pessoas conhecidas, fila de revisão, merchant rules, importação) com asserções positivas **e** negativas (401/403/400/404/409).

Não usa mock nem banco de dentro do teste — bate na API HTTP de verdade. Por isso **precisa do backend rodando**.

## Arquivos

| Arquivo | O quê |
| --- | --- |
| `personal-finance.postman_collection.json` | A coleção com ~40 requests e asserções (`pm.test`). |
| `personal-finance.postman_environment.json` | Environment com `base_url` (default `http://localhost:8080`). |

## Pré-requisito: subir a aplicação

```bash
# 1) Banco
docker compose up -d postgres

# 2) Backend (a partir de backend/)
cd backend && mvn spring-boot:run
# API em http://localhost:8080
```

> A coleção só depende do **backend** (porta 8080). O frontend não é necessário.

## Rodar no Postman (UI)

1. **Import** → selecione os dois arquivos (`*_collection.json` e `*_environment.json`).
2. No canto superior direito, selecione o environment **"Personal Finance — Local"**.
3. **Collection Runner** → selecione a coleção → **Run**. Rode **na ordem** (o fluxo é sequencial: cada pasta depende de variáveis salvas pela anterior).

## Rodar via linha de comando (Newman / CI)

```bash
# instalar uma vez
npm install -g newman

# executar
newman run postman/personal-finance.postman_collection.json \
  -e postman/personal-finance.postman_environment.json

# apontando para outro host
newman run postman/personal-finance.postman_collection.json \
  --env-var base_url=https://minha-api.exemplo.com

# relatório JUnit (para CI)
newman run postman/personal-finance.postman_collection.json \
  -e postman/personal-finance.postman_environment.json \
  -r cli,junit --reporter-junit-export newman-report.xml
```

Saída de sucesso: todas as asserções verdes e **exit code 0** (Newman falha o processo se qualquer `pm.test` quebrar — ideal para pipeline).

## Como o fluxo funciona

- Um **pre-request script no nível da coleção** gera, uma vez por execução, um e-mail único (`qa_<timestamp>@example.com`), a senha e as datas do mês corrente (`pf_year`, `pf_month`, `pf_month_str`, `pf_date`). Assim cada run é isolado e repetível.
- O **register** salva `pf_token` (JWT); a autenticação `Bearer {{pf_token}}` é herdada por toda a coleção. Os requests de auth e os testes de "não autenticado" sobrescrevem para `No Auth`.
- IDs criados (categoria, transações, meta, pessoa) são salvos em variáveis e reutilizados nos updates/deletes; a pasta **10. Cleanup** apaga o que sobra.

## Cobertura

| Pasta | Cobre |
| --- | --- |
| 0. Auth & Profile | register (201 + duplicado 400 + senha curta 400), login (200 + senha errada 401), `/users/me` (sem token 401, com token 200), salário (200 + negativo 400) |
| 1. Categories | listar (globais), criar (201 + nome vazio 400), atualizar |
| 2. Transactions | criar despesa/receita (201 + amount 0 → 400), listar por mês, filtro por tipo, atualizar, patch de apelido, excluir |
| 3. Dashboard | `/dashboard/monthly` (agregados 50/30/20 + destaques) |
| 4. Reports | evolução mensal (6 pontos + clamp 24), gasto por categoria (ordenação) |
| 5. Budget Goals | criar (201 + amount 0 → 400), listar, atualizar, excluir |
| 6. Known Persons | criar (201 + relationship inválido 400), listar, atualizar, desativar |
| 7. Revisão inline | lista `?needsReview=true` (Page; toda linha tem `needsReview=true`) |
| 8. Merchant Rules | listar regras globais semeadas |
| 9. Import | histórico (array), retomar sessão inexistente (400/404) |
| 10. Cleanup | remove transação e categoria criadas |

## Resultado esperado

Rodando contra o backend **real** (perfil `dev` com Flyway → categorias e merchant rules já semeadas), a suíte fica **100% verde** (78 asserções).

> Se você subir o backend com o perfil `test` (H2 em memória, **Flyway desabilitado**), 2 asserções da pasta *1. Categories* falham — elas checam as **categorias globais semeadas**, que só existem quando o Flyway roda. Isso é limitação do ambiente H2, não da API.

Esta suíte já pegou dois defeitos reais (corrigidos no `GlobalExceptionHandler`): erros de **validação** retornavam `500` em vez de `400`, e excluir uma categoria referenciada por uma merchant rule retornava `500` em vez de `409`. É exatamente o tipo de regressão que esses testes de caixa-preta protegem.

## Observações

- A importação de PDF (`POST /api/import/parse`) é **multipart com arquivo binário** e não está no fluxo automatizado (depende de um PDF real). Os testes de import cobrem histórico e o endpoint de retomar preview via caminho negativo. Para testar o upload manualmente, crie um request `POST {{base_url}}/api/import/parse` com body `form-data`, campo `file` do tipo *File*, apontando para `backend/src/test/resources/**/extrato.pdf`.
