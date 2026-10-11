#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo 'Copy .env.example to .env and configure local MySQL credentials first.' >&2
  exit 1
fi
# .env is a trusted local shell-compatible file, never committed to Git.
set -a
source .env
set +a
docker compose up -d --wait mysql
exec ./mvnw "${@}" spring-boot:run -Dspring-boot.run.profiles=dev
