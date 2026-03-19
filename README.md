# Pawever Backend

펫로스 통합 지원 서비스 **Pawever**의 백엔드 API 서버입니다.

## ⚒️ 기술 스택

- **Java 17** · **Spring Boot 4.0**
- **Spring Data JPA** · **MariaDB**
- **Spring Security** · **JWT** (jjwt)
- **SpringDoc OpenAPI** (Swagger UI)
- **NCP Object Storage** (S3 호환) · CDN
- **Gradle** · **Docker / Docker Compose**
<br><br>

## 🧬 ERD
<img width="1920" height="1147" alt="Pawever-ERD" src="https://github.com/user-attachments/assets/b3db9096-5f95-42ed-b945-312bdf1a7c21" />
<br><br>

## 💎 주요 기능

| 도메인 | 설명 |
|--------|------|
| **Auth** | 카카오/네이버 소셜 로그인, JWT 발급, 개발용 로그인 |
| **User** | 회원 정보, 온보딩, 추천 경로(referral) |
| **Pet** | 반려동물 등록(품종/동물종), 초대 코드, 생애 단계(BEFORE/AFTER_FAREWELL) |
| **Sharing** | 반려동물 공유(초대 코드 발급/참여) |
| **Mission** | 발자국 남기기(추억 남기기) 미션 목록·완료, 홈 진행률 요약 |
| **Checklist** | 이별준비 체크리스트 진행률, 항목 토글 |
| **Memorial** | 추모 댓글, 댓글 신고 |
| **Funeral** | 장례 업체 목록/상세, 리뷰 및 이미지 |
| **Review** | 서비스 리뷰(ServiceReview) |

<br>

## 📝 사전 요구 사항

- **JDK 17**
- **Docker & Docker Compose** (로컬 DB 또는 전체 앱 실행 시)
- (선택) **MariaDB** 10.x+ (Docker 없이 로컬 DB 사용 시)
<br><br>

## 🏛️ 프로젝트 구조

```
src/main/java/com/pawever/backend/
├── PaweverBackendApplication.java
├── auth/          # 소셜 로그인, JWT
├── user/           # 회원
├── pet/            # 반려동물, 품종/동물종
├── sharing/        # 반려동물 공유(초대)
├── mission/        # 발자국 남기기 미션
├── checklist/      # 이별준비 체크리스트
├── memorial/       # 추모 댓글, 신고
├── funeral/        # 장례 업체, 리뷰
├── review/         # 서비스 리뷰
└── global/         # 공통 응답, 보안, 예외, 설정
```

- **DB 스키마**: `src/main/resources/Pawever.sql`
- **시드 데이터**: `breeds_data.sql`, `missions_data.sql`, `funeral_companies_data.sql`, `data.sql`, `faq_data.sql`
- **설정**: `application.yaml`, `application-prod.yaml` (Docker/Prod용)
<br><br>

## 📖 API 문서

실행 후 다음 주소에서 확인할 수 있습니다.
- **Swagger UI**: `/swagger-ui.html`
<br><br>

## 🚀 배포

- **main** 브랜치 push 시 GitHub Actions로 NCP 서버에 SSH 배포됩니다.
- 워크플로: `.github/workflows/deploy.yml`
- 서버에서 `docker compose`로 앱·DB를 빌드 및 실행합니다.
