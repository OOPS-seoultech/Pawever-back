# Pawever Backend API 가이드

## 실행

[Docker Desktop](https://www.docker.com/products/docker-desktop/) 설치 후:

```bash
git clone <repo-url>
cd pawever-backend
docker compose up --build
```

`Started PaweverBackendApplication` 로그가 보이면 준비 완료.

## 접속

| 항목 | URL |
|------|-----|
| **Swagger UI** | http://localhost:28080/swagger-ui/index.html |
| **API Base** | http://localhost:28080 |

## 인증 방법

1. Swagger UI에서 `POST /api/auth/dev-login` 실행:
   ```json
   { "name": "테스트", "nickname": "tester" }
   ```
2. 응답의 `accessToken` 복사
3. Swagger 상단 **Authorize** 클릭 → 토큰 값만 붙여넣기 → Authorize

이후 모든 API 테스트 가능.

## 종료

```bash
# Ctrl+C 또는
docker compose down

# DB 초기화 포함
docker compose down -v
```
