#!/usr/bin/env bash
# 운영 app 로그 보기: 최근 100줄 + 실시간 (Ctrl+C로 종료)
#   ./scripts/logs.sh
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose -f compose.prod.yaml logs --follow --tail 100 app
