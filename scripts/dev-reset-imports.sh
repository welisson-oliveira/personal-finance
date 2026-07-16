#!/usr/bin/env bash
# Reset de importações em ambiente de desenvolvimento.
# Apaga transações, sessões de importação e fila de revisão.
# Preserva categorias, merchant rules, metas e configurações de usuário.
#
# Uso: ./scripts/dev-reset-imports.sh [--confirm]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/dev-reset-imports.sql"

# Lê .env se existir
ENV_FILE="$SCRIPT_DIR/../.env"
if [[ -f "$ENV_FILE" ]]; then
  export $(grep -v '^#' "$ENV_FILE" | xargs)
fi

DB_CONTAINER="${DB_CONTAINER:-personal-finance-db}"
DB_NAME="${DB_NAME:-personal_finance}"
DB_USER="${DB_USER:-postgres}"

echo "========================================"
echo "  RESET DE IMPORTAÇÕES — DEV"
echo "========================================"
echo "  Banco : $DB_NAME (container: $DB_CONTAINER)"
echo "  O que será REMOVIDO:"
echo "    - transactions"
echo "    - import_sessions"
echo "    - review_queue"
echo "    - opening_balance dos usuários"
echo ""
echo "  O que será PRESERVADO:"
echo "    - categories, merchant_rules"
echo "    - merchant_display_names, budget_goals"
echo "    - known_persons, users"
echo "========================================"

if [[ "${1:-}" != "--confirm" ]]; then
  read -p "Confirmar reset? (s/N) " REPLY
  echo
  if [[ ! "$REPLY" =~ ^[Ss]$ ]]; then
    echo "Cancelado."
    exit 0
  fi
fi

docker cp "$SQL_FILE" "$DB_CONTAINER:/tmp/reset.sql"
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -f /tmp/reset.sql

echo ""
echo "Reset concluído. Reconfigure o saldo inicial em Configurações antes de importar."
