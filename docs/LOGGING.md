# 로그 저장 및 확인 (참고)

## 1. GitHub Actions

| 항목 | 내용 |
|------|------|
| **저장 위치** | GitHub 인프라 (NCP 서버 아님). 저장소 **Actions** 탭 → 워크플로 실행 → job/step 로그 |
| **보관** | 기본 **90일** — **Settings → Actions → General → Artifact and log retention** 에서 변경 |

### 배포 워크플로 로그 정책

- **성공**: 서버에는 `/tmp/deploy.log` 등에 전체 로그가 남을 수 있고, Actions 쪽에는 요약(예: 성공 메시지, `docker compose ps`) 위주.
- **실패**: 원인 파악을 위해 마지막 수십 줄만 Actions 로그에 출력하는 식으로 제한하는 경우가 많음.
- 시크릿 값은 **Secrets**로 주입해 로그에 직접 노출하지 않는다.

---

## 2. Spring Boot 앱 로그 (NCP 등 배포 서버)

| 항목 | 내용 |
|------|------|
| **저장 위치** | compose 파일이 있는 디렉터리 기준 **`./logs`** (호스트). 컨테이너 내부는 **`/app/logs`** 와 동일 내용(바인드 마운트). 이미지를 바꿔도 호스트 `./logs`는 유지된다. |
| **경로 설정** | `compose.yaml`: `./logs:/app/logs`, 환경 변수 `LOG_PATH=/app/logs` |
| **다른 경로** | compose의 `volumes`를 `- /원하는/경로:/app/logs` 로 바꾸면 된다. |

### 파일 종류

- **현재 쓰는 파일**: `logs/app.log`, `logs/error.log`, `logs/request.log`
- **롤·아카이브**: `logs/archive/날짜/` 아래 — 날짜 변경 또는 단일 파일 **20MB** 초과 시 분리. 보관 일수 초과 시 삭제(앱/`logback-spring.xml` 정책 기준, 예: app 14일, error 30일, request 7일).

| 파일 | 용도 |
|------|------|
| `app.log` | 일반·앱 로그 |
| `error.log` | ERROR 수준 |
| `request.log` | 4xx/5xx, 느린 요청(예: 3초 초과) |

레벨·JSON 포맷 등 세부는 **`logback-spring.xml`** 의 `prod` 프로필을 보면 된다.

### 서버에서 확인

```bash
cd <compose.yaml 이 있는 경로>
tail -f logs/app.log
tail -f logs/error.log
tail -f logs/request.log

# 동일 파일을 컨테이너에서
docker compose exec app tail -f /app/logs/app.log
```
