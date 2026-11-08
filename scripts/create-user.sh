#!/usr/bin/env bash
# 운영 사용자 생성 (회원가입 기능이 없어서 DB에 직접 추가)
#   사용법: ./scripts/create-user.sh <username> <nickname>
#   compose.prod.yaml의 mariadb, caddy 컨테이너가 실행 중이어야 한다.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f compose.prod.yaml)

if [ $# -ne 2 ]; then
  echo "Usage: $0 <username> <nickname>" >&2
  exit 1
fi
USERNAME=$1
NICKNAME=$2

# 입력값이 SQL에 들어가므로 허용 문자를 제한한다
if [[ ! $USERNAME =~ ^[A-Za-z0-9_]{3,50}$ ]]; then
  echo "username must be 3-50 characters: letters, digits, or _" >&2
  exit 1
fi
if [[ $NICKNAME == *"'"* || $NICKNAME == *\\* || ${#NICKNAME} -gt 50 ]]; then
  echo "nickname must be at most 50 characters and cannot contain ' or \\" >&2
  exit 1
fi

# 비밀번호는 화면에 표시하지 않고 두 번 입력받는다
read -rsp "Password: " PASSWORD; echo
read -rsp "Confirm password: " PASSWORD_CONFIRM; echo
if [ "$PASSWORD" != "$PASSWORD_CONFIRM" ]; then
  echo "Passwords do not match" >&2
  exit 1
fi
if [ ${#PASSWORD} -lt 8 ]; then
  echo "Password must be at least 8 characters" >&2
  exit 1
fi

# bcrypt 해시 (Spring BCryptPasswordEncoder와 호환). 비밀번호는 표준입력으로 넘겨 명령줄에 남기지 않는다
HASH=$(printf '%s\n' "$PASSWORD" \
  | "${COMPOSE[@]}" exec -T caddy caddy hash-password --algorithm bcrypt --bcrypt-cost 10)
if [[ ! $HASH =~ ^\$2a\$10\$.{53}$ ]]; then
  echo "Failed to hash password: $HASH" >&2
  exit 1
fi

# DB 계정은 mariadb 컨테이너의 환경변수(.env에서 전달된 값)를 사용한다
"${COMPOSE[@]}" exec -T mariadb sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" DEMO' <<SQL
INSERT INTO users (username, password_hash, nickname) VALUES ('$USERNAME', '$HASH', '$NICKNAME');
SQL

echo "Created user: $USERNAME ($NICKNAME)"
