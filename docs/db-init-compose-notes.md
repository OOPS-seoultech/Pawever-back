## 작업 개요
`docker compose up` 시점에 DB 초기화/시드 데이터 로딩이 애매하게(Spring Boot `spring.sql.init` 일부 파일만 실행) 되어 있던 흐름을,
**MariaDB 컨테이너 init 스크립트** 기반으로 전환해서 다음을 달성했습니다.

- **`src/main/resources`의 필요한 `.sql` 파일이 실행**되도록 변경
- 컨테이너 내부의 **단일 폴더(`/docker-entrypoint-initdb.d`)에 모아서 실행**되도록 구성
- 개발 단계에서 편하게 쓰도록 **매번 "깨끗한 DB"로 시작**(데이터 영속화 제거)
- 프론트 재현용 **`local_runtime_seed.sql`도 마지막에 함께 로드**

---

## 변경된 실행 흐름
### Docker Compose (`compose.yaml`)
MariaDB 공식 이미지의 초기화 규칙을 사용합니다.

- MariaDB는 데이터 디렉토리(`/var/lib/mysql`)가 **비어있을 때만**
  `/docker-entrypoint-initdb.d` 안의 `*.sql`을 **파일명 정렬 순서대로** 실행합니다.
- DB 데이터는 개발 편의상 `tmpfs`로 두어 **컨테이너가 재생성/재시작되면 항상 초기화**됩니다.

#### 실행 순서(파일명 기준)
`compose.yaml`에서 아래처럼 마운트하여 순서를 고정했습니다.

1. `00_schema.sql`  ← `Pawever.sql`(DDL 스키마)
2. `10_breeds_data.sql`
3. `20_missions_data.sql`
4. `30_funeral_companies_data.sql`
5. `40_data.sql`
6. `50_faq_data.sql`
7. `60_local_runtime_seed.sql`

관련 설정 위치: `compose.yaml`의 `mariadb.volumes` 섹션

---

## Spring Boot 설정 변경
### `application-prod.yaml` (docker compose에서 사용하는 `prod` 프로필)
docker compose 경로에서는 **DB가 MariaDB init 스크립트로 이미 스키마/시드가 구성**되므로,
앱이 다시 SQL을 실행하거나 Hibernate가 스키마를 변경하지 않게 막았습니다.

- **`spring.sql.init.mode: never`**
  - 앱 부팅 시 Spring SQL init으로 seed가 중복 실행되는 것을 방지
- **`spring.jpa.hibernate.ddl-auto: none`**
  - 스키마는 `Pawever.sql`로 생성하므로 Hibernate 자동 DDL을 비활성화

관련 설정 위치: `src/main/resources/application-prod.yaml`

### `application.yaml` (로컬 앱 단독 실행)
로컬에서는 Hibernate가 스키마를 관리하도록 유지하면서(기존 `ddl-auto: update`),
**데이터 seed용 `.sql`들을 포함**하도록 확장했습니다.

> 주의: `Pawever.sql`은 DDL이므로 로컬 Spring SQL init에 포함하면
> "table already exists" 등 충돌 가능성이 커서 제외했습니다.

기본값은 기준 데이터 위주로 두고, 필요하면 `SQL_INIT_DATA_LOCATIONS` 환경변수로 덮어쓸 수 있습니다.

관련 설정 위치: `src/main/resources/application.yaml`의 `spring.sql.init.data-locations`

---

## 개발 단계 "항상 깨끗한 DB" 정책
현재 `compose.yaml`은 아래 정책을 의도합니다.

- **DB 영속화 제거**: `/var/lib/mysql`을 `tmpfs`로 사용
- 결과적으로 `docker compose up` 시점에 DB가 항상 초기 상태로 시작하며,
  MariaDB init 스크립트가 매번 다시 실행됩니다.
- 마지막에 `local_runtime_seed.sql`이 실행되어 로컬 프론트 플로우 재현용 샘플 데이터도 같이 들어갑니다.

---

## 프로덕션 적용 시 반드시 바꿔야 할 사항
현재 구성은 "개발 편의"를 최우선으로 했기 때문에, 프로덕션에는 그대로 적용하면 위험합니다.

### 1) DB 영속화
- **`tmpfs: /var/lib/mysql` 제거**
- 대신 **named volume** 혹은 **외부 관리 DB(NCP RDS 등)** 사용
- 프로덕션에서 DB가 재시작될 때마다 초기화되면 데이터가 모두 유실됩니다.

### 2) 스키마 관리 전략 정리
현재는 `Pawever.sql`(DDL)로 스키마를 만들고 `ddl-auto: none`으로 막는 구조입니다.
프로덕션에서는 아래 중 하나로 통일하는 것을 권장합니다.

- **(권장) Flyway/Liquibase 도입**
- **Hibernate DDL 사용 유지 시** `validate` 또는 `none`처럼 운영 정책을 명확히 고정

### 3) 초기 시드 정책 분리
프로덕션에서는 "매번 전체 seed 재실행"보다
최초 1회 기준 데이터와 운영 중 변경되는 데이터를 분리하는 편이 안전합니다.

### 4) `/docker-entrypoint-initdb.d` 의존성 이해
MariaDB init 스크립트는 **데이터 디렉토리가 비어있을 때만 실행**됩니다.
영속 볼륨을 쓰면 최초 1회만 실행되고, 이후 배포/재시작 시에는 다시 실행되지 않습니다.

### 5) `local_runtime_seed.sql`
이 파일은 **개발용 샘플 데이터**입니다.
프로덕션에서는 제외해야 합니다.

---

## 빠른 확인 방법(개발)
Docker Desktop(또는 Docker daemon)이 켜져 있는 상태에서:

```bash
docker compose down --remove-orphans
docker compose up --build
```

그리고 MariaDB 로그에서 `/docker-entrypoint-initdb.d` 스크립트 실행 흔적과
애플리케이션 기동 여부를 확인하면 됩니다.
