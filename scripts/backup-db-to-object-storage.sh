#!/usr/bin/env bash
# MariaDB 덤프 → gzip → NCP Object Storage(S3 호환) 업로드 + 보관 정리
# 스타트업 기본값: 일별 14일, 주간(일요일) 56일(8주), 객체 키에 타임스탬프
#
# 필요: docker, docker compose(v2), aws-cli v2, gzip, GNU date, jq(보관 정리 시)
#
# cron 예 (UTC 3:30, 프로젝트 경로 맞출 것):
#   30 3 * * * cd /home/ubuntu/Pawever-back && /usr/bin/env bash ./scripts/backup-db-to-object-storage.sh >> /var/log/pawever-db-backup.log 2>&1
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/.env"
  set +a
fi

: "${NCP_S3_ENDPOINT:=https://kr.object.ncloudstorage.com}"
: "${BACKUP_S3_PREFIX:=backups/pawever-db}"
: "${BACKUP_RETAIN_DAILY_DAYS:=14}"
: "${BACKUP_RETAIN_WEEKLY_DAYS:=56}"
: "${COMPOSE_SERVICE_DB:=mariadb}"
: "${AWS_DEFAULT_REGION:=kr-standard}"

export AWS_ACCESS_KEY_ID="${NCP_ACCESS_KEY:-}"
export AWS_SECRET_ACCESS_KEY="${NCP_SECRET_KEY:-}"

require_nonempty() {
  local name="$1" val="$2"
  if [[ -z "$val" ]]; then
    echo "error: missing required env: $name" >&2
    exit 1
  fi
}

require_nonempty "NCP_BUCKET" "${NCP_BUCKET:-}"
require_nonempty "NCP_ACCESS_KEY" "${NCP_ACCESS_KEY:-}"
require_nonempty "NCP_SECRET_KEY" "${NCP_SECRET_KEY:-}"
require_nonempty "DB_NAME" "${DB_NAME:-}"
require_nonempty "DB_USER" "${DB_USER:-}"
require_nonempty "DB_PASSWORD" "${DB_PASSWORD:-}"

log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

if ! date -u -d "@0" '+%Y' >/dev/null 2>&1; then
  log "error: GNU date 가 필요합니다 (서버 리눅스 cron 환경)."
  exit 1
fi

if [[ -z "$(docker compose ps -q "$COMPOSE_SERVICE_DB" 2>/dev/null)" ]]; then
  log "error: compose 서비스 '${COMPOSE_SERVICE_DB}' 가 실행 중이 아닙니다."
  exit 1
fi

tmp=""
tmp_gz=""
cleanup() {
  [[ -n "${tmp:-}" && -f "$tmp" ]] && rm -f "$tmp"
  [[ -n "${tmp_gz:-}" && -f "$tmp_gz" ]] && rm -f "$tmp_gz"
}
trap cleanup EXIT

tmp="$(mktemp)"
tmp_gz="${tmp}.gz"

log "dump 시작: db=${DB_NAME}"
docker compose exec -T \
  -e "MYSQL_PWD=${DB_PASSWORD}" \
  "$COMPOSE_SERVICE_DB" \
  mariadb-dump \
  --single-transaction \
  --routines \
  --events \
  -u "$DB_USER" \
  "$DB_NAME" >"$tmp"

gzip -c "$tmp" >"$tmp_gz"
size="$(wc -c <"$tmp_gz" | tr -d ' ')"
log "dump 완료 (압축 크기 ${size} bytes)"

ymd="$(date -u '+%Y/%m/%d')"
hm="$(date -u '+%H%M')"
daily_key="${BACKUP_S3_PREFIX}/daily/${ymd}/pawever-${hm}.sql.gz"
daily_uri="s3://${NCP_BUCKET}/${daily_key}"

log "업로드 (일별): ${daily_uri}"
aws s3 cp "$tmp_gz" "$daily_uri" \
  --endpoint-url "$NCP_S3_ENDPOINT" \
  --region "$AWS_DEFAULT_REGION" \
  --no-progress

dow="$(date -u '+%u')"
if [[ "$dow" == "7" ]]; then
  week="$(date -u '+%G-W%V')"
  weekly_key="${BACKUP_S3_PREFIX}/weekly/${week}/pawever-${hm}.sql.gz"
  weekly_uri="s3://${NCP_BUCKET}/${weekly_key}"
  log "업로드 (주간): ${weekly_uri}"
  aws s3 cp "$tmp_gz" "$weekly_uri" \
    --endpoint-url "$NCP_S3_ENDPOINT" \
    --region "$AWS_DEFAULT_REGION" \
    --no-progress
fi

prune_if_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    log "warn: jq 가 없어 보관 정리는 건너뜁니다. (sudo apt install jq)"
    return 0
  fi

  local prefix_rel="$1"
  local days_old="$2"
  local cutoff_iso
  cutoff_iso="$(date -u -d "${days_old} days ago" '+%Y-%m-%dT%H:%M:%S.000Z')"

  local json
  json="$(aws s3api list-objects-v2 \
    --bucket "$NCP_BUCKET" \
    --prefix "$prefix_rel" \
    --endpoint-url "$NCP_S3_ENDPOINT" \
    --region "$AWS_DEFAULT_REGION" \
    --output json)"

  mapfile -t keys < <(echo "$json" | jq -r --arg c "$cutoff_iso" '
    .Contents // [] | .[] | select(.LastModified < $c) | .Key')

  if [[ "${#keys[@]}" -eq 0 ]]; then
    log "정리: ${prefix_rel} 아래 삭제 대상 없음 (${days_old}일 이전)"
    return 0
  fi

  local k n=0
  for k in "${keys[@]}"; do
    [[ -z "$k" ]] && continue
    aws s3api delete-object \
      --bucket "$NCP_BUCKET" \
      --key "$k" \
      --endpoint-url "$NCP_S3_ENDPOINT" \
      --region "$AWS_DEFAULT_REGION" >/dev/null
    ((++n)) || true
  done
  log "정리 완료: ${prefix_rel} 에서 ${n}개 객체 삭제 (${days_old}일 이전)"
}

prune_if_jq "${BACKUP_S3_PREFIX}/daily/" "$BACKUP_RETAIN_DAILY_DAYS"
prune_if_jq "${BACKUP_S3_PREFIX}/weekly/" "$BACKUP_RETAIN_WEEKLY_DAYS"

log "백업 종료"
