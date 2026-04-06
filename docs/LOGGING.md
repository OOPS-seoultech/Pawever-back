# 로그 저장 및 관리

## 1. GitHub Actions 로그

### 저장 위치
- **GitHub 서버**에 저장됨 (NCP 서버 아님).
- 저장소 → **Actions** 탭 → 워크플로 실행 선택 → 각 job/step 로그 확인.

### 보관 기간
- 기본 **90일** (저장소 설정에서 변경 가능).
- **Settings → Actions → General → Artifact and log retention** 에서 일수 조정.

### 확인 방법
- Actions 탭에서 실행별로 **View workflow run** → 각 step 클릭해 로그 확인.
- 실패 시 해당 step 로그에 에러 메시지 출력.

### 핵심만 남기기 (적용됨)
- **성공 시**: 빌드 전체 출력은 서버 `/tmp/deploy.log`에만 남기고, Actions 로그에는 **"Deploy 성공" + `docker compose ps`** 만 출력.
- **실패 시**: 마지막 50줄만 Actions 로그에 출력해 원인 파악 가능.
- 민감 정보는 **Secrets** 사용으로 로그에 노출되지 않음.

---

## 2. Spring Boot(앱) 로그 (NCP 서버)

### 저장 위치 (배포 환경)
- **Docker named volume이 아니라 서버(호스트) 폴더**에 저장됨.
- **compose 파일이 있는 디렉터리 기준 `./logs`** → 예: `~/Pawever-back/logs`
- 컨테이너 안에서는 동일 내용이 `/app/logs/` 로 보임(바인드 마운트). 이미지를 지우고 다시 띄워도 **호스트의 `./logs`는 그대로**라 로그가 유지됨.

### 파일 종류

- **지금 붙고 있는 파일**: `logs/app.log`, `logs/error.log`, `logs/request.log` (실시간 tail 대상).
- **롤(분리)된 과거본**: `logs/archive/날짜/` 아래 `app.*.log`, `error.*.log`, `request.*.log` — 날짜가 바뀌거나 한 파일이 **20MB**를 넘기면 넘어감. 오래된 날짜 폴더는 **보관 일수** 지나면 자동 삭제(`app` 14일, `error` 30일, `request` 7일).

| 파일 | 용도 |
|------|------|
| `app.log` | 앱·일반 로그 |
| `error.log` | ERROR만 |
| `request.log` | 4xx/5xx·느린 요청(3초+) |

- prod 레벨·JSON 포맷 등 세부 정책은 `logback-spring.xml` 의 `prod` 프로파일을 보면 됨.

### 서버에서 로그 보기

```bash
cd ~/Pawever-back   # compose 있는 경로

# 호스트에서 바로 보기(가장 단순)
tail -f logs/app.log
tail -f logs/error.log
tail -f logs/request.log

# 컨테이너 경로로 보기(동일 파일)
docker compose exec app tail -f /app/logs/app.log
```

### 로그 경로 설정
- **compose**: `./logs:/app/logs`, 환경 변수 `LOG_PATH=/app/logs` (앱이 쓰는 컨테이너 내 경로).
- 다른 호스트 경로를 쓰려면 compose 의 `volumes` 를 `- /원하는/경로:/app/logs` 로 바꾸면 됨.

---

## 3. 적용한 개선 사항

1. **로그 영속화**  
   호스트 `./logs` 바인드 마운트 → Docker 볼륨 없이 서버 폴더에 보관, 재배포 후에도 동일 경로에서 확인 가능.
2. **LOG_PATH 고정**  
   컨테이너 내 `LOG_PATH=/app/logs` 로 통일.
3. **핵심만 기록 (prod)**  
   - Spring Boot: `logback-spring.xml` 및 요청 필터 정책 참고.  
   - GitHub Actions: 성공 시 요약만 출력, 실패 시 마지막 50줄만 출력.
