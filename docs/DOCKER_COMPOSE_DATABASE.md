# Docker Compose + MariaDB

`compose.yaml`으로 띄울 때 DB가 어떻게 만들어지고, Spring 앱 설정과 어떻게 맞물리는지 정리한다.

## MariaDB 컨테이너

| 항목 | 내용 |
|------|------|
| 데이터 저장 | **`mariadb-data` named volume** → 컨테이너의 `/var/lib/mysql` |
| Init SQL | 데이터 디렉터리가 **비어 있을 때만** `/docker-entrypoint-initdb.d/*.sql` 실행 (파일명 정렬 순) |
| 이후 기동 | 볼륨에 데이터가 있으면 init SQL은 **다시 실행되지 않음** |

## Init SQL 순서 (`compose.yaml` 마운트)

| 순서 | 컨테이너 경로 | 리소스 파일 |
|------|----------------|-------------|
| 1 | `00_schema.sql` | `Pawever.sql` |
| 2 | `10_breeds_data.sql` | `breeds_data.sql` |
| 3 | `20_missions_data.sql` | `missions_data.sql` |
| 4 | `30_funeral_companies_data.sql` | `funeral_companies_data.sql` |
| 5 | `40_data.sql` | `data.sql` |
| 6 | `50_faq_data.sql` | `faq_data.sql` |
| 7 | `60_local_runtime_seed.sql` | `local_runtime_seed.sql` (샘플·재현용, **첫 init 때만**) |

## Spring Boot

### `prod` — Compose의 `app` 서비스

`SPRING_PROFILES_ACTIVE=prod` → `application-prod.yaml`

- **`spring.sql.init.mode: never`** — 스키마/시드는 MariaDB entrypoint에서만. 앱 기동 시 SQL init 중복 방지.
- **`spring.jpa.hibernate.ddl-auto: none`** — DDL은 `Pawever.sql` 등 init SQL에 맡김.
- **`spring.datasource.url`** — JDBC URL에 호스트 `mariadb`(서비스명), DB명 `DB_NAME`.

### 기본 프로필 — 로컬에서 JAR/IDE만 실행

`application.yaml`

- **`spring.jpa.hibernate.ddl-auto: update`** — 스키마는 Hibernate.
- **`spring.sql.init.mode: always`** + **`data-locations`** — 시드 SQL만 (`breeds_data` ~ `faq_data` 등). `Pawever.sql`(전체 DDL)은 넣지 않음.
- **`defer-datasource-initialization: true`** — 스키마 생성 후 데이터 로드.

## DB를 처음부터 다시 만들기

볼륨을 지우면 다음 기동 시 디렉터리가 비어 init이 한 번 더 돈다.

```bash
docker compose down
docker volume ls    # 예: pawever-backend_mariadb-data
docker volume rm <프로젝트명>_mariadb-data
docker compose up -d --build
```

## 관련 파일

- `compose.yaml`
- `src/main/resources/application-prod.yaml`
- `src/main/resources/application.yaml`
- `src/main/resources/Pawever.sql`, `*_data.sql`, `local_runtime_seed.sql`

## 운영 시 참고

- 외부 DB(RDS 등)로 옮기면 연결 정보·마이그레이션 전략을 별도로 맞춘다.
- 스키마 변경을 장기적으로는 Flyway/Liquibase 같은 도구로 통일하는 편이 안전하다.
