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
- **Docker 볼륨 `app-logs`** 에 저장됨.
- 컨테이너 내 경로: `/app/logs/`
- `docker compose up` 시 볼륨이 마운트되므로 **재배포해도 로그 유지**.

### 파일 종류 (prod = 핵심만)

| 파일 | 로컬 | prod | 로테이션 |
|------|------|------|----------|
| `app.log` | INFO 이상 | **WARN 이상만** | 20MB/일별, 14일 |
| `error.log` | ERROR만 | ERROR만 | 20MB/일별, 30일 |
| `request.log` | 4xx/5xx·느린 요청(3초+) | 동일 | 20MB/일별, 7일 |

- **콘솔**: prod에서는 WARN 이상만 출력.
- **프레임워크(Spring, Hibernate 등)**: WARN 이상만 기록.

### 서버에서 로그 보기

```bash
# 실시간 보기
docker compose exec app tail -f /app/logs/app.log
docker compose exec app tail -f /app/logs/error.log
docker compose exec app tail -f /app/logs/request.log

# 최근 100줄
docker compose exec app tail -100 /app/logs/app.log

# 볼륨 실제 위치 (호스트)
docker volume inspect pawever-back_app-logs
# Linux에서 보통 /var/lib/docker/volumes/pawever-back_app-logs/_data
```

### 로그 경로 설정
- **compose** 에서 `LOG_PATH=/app/logs` 로 고정하고, 해당 경로를 볼륨으로 마운트.
- 호스트 특정 경로(예: `/var/log/pawever`)에 두고 싶다면 compose 에서  
  `volumes: - /var/log/pawever:/app/logs` 로 바꾸면 됨.

---

## 3. 적용한 개선 사항

1. **로그 영속화**  
   compose 에 `app-logs` 볼륨 추가 → 재배포해도 로그 유지.
2. **LOG_PATH 고정**  
   컨테이너 내 `LOG_PATH=/app/logs` 로 통일.
3. **핵심만 기록 (prod)**  
   - Spring Boot: `app.log` = WARN 이상, `error.log` = ERROR만, `request.log` = 에러/느린 요청만.  
   - GitHub Actions: 성공 시 요약만 출력, 실패 시 마지막 50줄만 출력.
