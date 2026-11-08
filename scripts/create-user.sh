#!/usr/bin/env bash
# 운영 사용자 생성 (회원가입 기능이 없어서 DB에 직접 추가)
#   사용법: ./scripts/create-user.sh <username> <nickname>
#   compose.prod.yaml의 mariadb, caddy 컨테이너가 실행 중이어야 한다.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f compose.prod.yaml)

if [ $# -ne 2 ]; then
  echo "사용법: $0 <username> <nickname>" >&2
  exit 1
fi
USERNAME=$1
NICKNAME=$2

# 입력값이 SQL에 들어가므로 허용 문자를 제한한다
if [[ ! $USERNAME =~ ^[A-Za-z0-9_]{3,50}$ ]]; then
  echo "username은 영문·숫자·_ 3~50자여야 합니다" >&2
  exit 1
fi
if [[ $NICKNAME == *"'"* || $NICKNAME == *\\* || ${#NICKNAME} -gt 50 ]]; then
  echo "nickname은 50자 이하이고 ' 와 \\ 를 쓸 수 없습니다" >&2
  exit 1
fi

# 비밀번호는 화면에 표시하지 않고 두 번 입력받는다
read -rsp "비밀번호: " PASSWORD; echo
read -rsp "비밀번호 확인: " PASSWORD_CONFIRM; echo
if [ "$PASSWORD" != "$PASSWORD_CONFIRM" ]; then
  echo "비밀번호가 일치하지 않습니다" >&2
  exit 1
fi
if [ ${#PASSWORD} -lt 8 ]; then
  echo "비밀번호는 8자 이상이어야 합니다" >&2
  exit 1
fi

# bcrypt 해시 (Spring BCryptPasswordEncoder와 호환). 비밀번호는 표준입력으로 넘겨 명령줄에 남기지 않는다
HASH=$(printf '%s\n' "$PASSWORD" \
  | "${COMPOSE[@]}" exec -T caddy caddy hash-password --algorithm bcrypt --bcrypt-cost 10)
if [[ ! $HASH =~ ^\$2a\$10\$.{53}$ ]]; then
  echo "비밀번호 해시 생성에 실패했습니다: $HASH" >&2
  exit 1
fi

# DB 계정은 mariadb 컨테이너의 환경변수(.env에서 전달된 값)를 사용한다
"${COMPOSE[@]}" exec -T mariadb sh -c 'mariadb -u"$MARIADB_USER" -p"$MARIADB_PASSWORD" DEMO' <<SQL
INSERT INTO users (username, password_hash, nickname) VALUES ('$USERNAME', '$HASH', '$NICKNAME');
SQL

echo "생성 완료: $USERNAME ($NICKNAME)"
