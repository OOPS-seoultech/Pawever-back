# DB 백업 (NCP Object Storage) (참고)

## 정책 (스크립트 기본값)

- **주기**: 매일 1회 덤프
- **객체 키**: 날짜·시간 포함
- **보관**: 일별 14일, 일요일 주간본은 56일(8주)

## 준비

- 서버: **AWS CLI v2**, **Docker Compose v2**, **gzip**, **jq**(정리 스크립트용), **GNU date**
- `.env`: `DB_*`, `NCP_ACCESS_KEY`, `NCP_SECRET_KEY`, `NCP_BUCKET` (`env.example` 참고)
- NCP 키: 해당 버킷 **업로드·목록·삭제**(오래된 백업 정리) 권한

## 실행

저장소 루트(`compose.yaml` 있는 디렉터리):

```bash
./scripts/backup-db-to-object-storage.sh
```

## 객체 경로

- 일별: `s3://$NCP_BUCKET/backups/pawever-db/daily/YYYY/MM/DD/pawever-HHMM.sql.gz`
- 주간: `s3://$NCP_BUCKET/backups/pawever-db/weekly/YYYY-Www/pawever-HHMM.sql.gz` (일요일에만 추가)

접두사·보관 일수는 스크립트 상단 주석·`env.example` 참고.

## cron 예시 (UTC 03:30)

```cron
30 3 * * * cd /home/USER/Pawever-back && /usr/bin/env bash ./scripts/backup-db-to-object-storage.sh >> /var/log/pawever-db-backup.log 2>&1
```

## 복구

1. 버킷에서 `.sql.gz` 다운로드 후 `gunzip`
2. `mysql` / `mariadb` 클라이언트로 대상 DB에 적용 (운영 반영 전 스테이징에서 검증 권장)

## Compose DB

- `compose.yaml`의 MariaDB는 **`mariadb-data` named volume**으로 데이터 유지
- DB를 비우고 init SQL부터 다시 쓰려면: `docker compose down` → `docker volume rm <프로젝트>_mariadb-data` → `up`

버킷 **라이프사이클**은 콘솔에서 별도 설정 가능. 스크립트는 지정 접두사 하위에서 보관 일수 초과 객체를 삭제한다.
