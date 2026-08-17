# DB 백업 (오브젝트 스토리지) (참고)

> 백업은 앱 이미지 스토리지와 **버킷을 분리한다**. 자격증명·리전만 재사용한다.
> 현재 운영값은 **AWS S3(리전 `ap-northeast-2`, `NCP_S3_ENDPOINT` 미설정)**. `NCP_S3_ENDPOINT` 를 지정하면 NCP 등 S3 호환 스토리지로 전환된다.
>
> **이미지 버킷(`NCP_BUCKET`)에 덤프를 두면 안 된다.** 그 버킷에는 이미지 서빙을 위해
> 버킷 전체에 공개 읽기 정책이 걸려 있어, 키를 아는 사람은 누구나 회원 정보를 통째로
> 내려받을 수 있다. 2026-08 에 실제로 36일간 노출된 적이 있어 스크립트에 같은 버킷이면
> 실행을 멈추는 가드를 넣어 두었다.

## 정책 (스크립트 기본값)

- **주기**: 매일 1회 덤프
- **객체 키**: 날짜·시간 포함
- **보관**: 일별 14일, 일요일 주간본은 56일(8주)

## 준비

- 서버: **AWS CLI v2**, **Docker Compose v2**, **gzip**, **jq**(정리 스크립트용), **GNU date**
- `.env`: `DB_*`, `NCP_ACCESS_KEY`, `NCP_SECRET_KEY`, `BACKUP_S3_BUCKET` (`env.example` 참고)
  — 자격증명은 앱 스토리지와 같은 키를 쓰되, 버킷은 백업 전용 **비공개** 버킷을 지정한다
- 백업 버킷: 퍼블릭 액세스 차단 4개 항목을 모두 켜 둔다
- 자격증명: 해당 버킷 **업로드·목록·삭제**(오래된 백업 정리) 권한
- 리전/엔드포인트: 미설정 시 AWS S3 `ap-northeast-2`. NCP면 `NCP_S3_ENDPOINT`(예: `https://kr.object.ncloudstorage.com`)·`NCP_REGION` 지정

## 실행

저장소 루트(`compose.yaml` 있는 디렉터리):

```bash
./scripts/backup-db-to-object-storage.sh
```

## 객체 경로

- 일별: `s3://$BACKUP_S3_BUCKET/backups/pawever-db/daily/YYYY/MM/DD/pawever-HHMM.sql.gz`
- 주간: `s3://$BACKUP_S3_BUCKET/backups/pawever-db/weekly/YYYY-Www/pawever-HHMM.sql.gz` (일요일에만 추가)

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
