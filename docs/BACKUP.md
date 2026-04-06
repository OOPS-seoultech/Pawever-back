# DB 백업 (NCP Object Storage)

스타트업 기본값: **매일 1회 덤프**, 객체 키에 날짜·시간, **일별 14일 보관**, **일요일 주간본**은 **56일(8주) 보관**.

## 준비

1. 서버에 **AWS CLI v2**, **Docker Compose v2**, **gzip**, **jq**(보관 정리 권장), **GNU date** (리눅스 기본).
2. `.env` 에 `DB_*`, `NCP_ACCESS_KEY`, `NCP_SECRET_KEY`, `NCP_BUCKET` 설정 (`env.example` 참고).
3. NCP 액세스 키에 해당 버킷에 대해 **업로드·목록·삭제**(정리용) 권한이 있어야 합니다.

## 실행

저장소 루트( `compose.yaml` 있는 디렉터리 )에서:

```bash
./scripts/backup-db-to-object-storage.sh
```

## 객체 경로

- 일별: `s3://$NCP_BUCKET/backups/pawever-db/daily/YYYY/MM/DD/pawever-HHMM.sql.gz`
- 주간: `s3://$NCP_BUCKET/backups/pawever-db/weekly/YYYY-Www/pawever-HHMM.sql.gz` (일요일에만 추가 업로드)

환경 변수로 접두사·보관 일수 조정 가능(스크립트 상단 주석 및 `env.example`).

## cron 예시 (UTC 03:30)

```cron
30 3 * * * cd /home/USER/Pawever-back && /usr/bin/env bash ./scripts/backup-db-to-object-storage.sh >> /var/log/pawever-db-backup.log 2>&1
```

## 복구 개요

1. 버킷에서 해당 `.sql.gz` 다운로드 후 `gunzip`.
2. `mysql` / `mariadb` 클라이언트로 빈 DB에 `source` 또는 파이프(운영 반영 전 스테이징에서 검증 권장).

## 참고

- `compose.yaml` 의 MariaDB가 **개발용 tmpfs** 이면 컨테이너 재시작 시 데이터가 날아갈 수 있습니다. **운영**은 별도 볼륨 구성을 권장합니다.
- 버킷에 **라이프사이클**을 콘솔에서 추가로 걸어도 되며, 이 스크립트는 동일 접두사에 대해 **일/주 단위 보관 일수**만큼 오래된 객체를 삭제합니다.
