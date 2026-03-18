## 작업 개요
`docker compose up` 시점에 DB 초기화/시드 데이터 로딩이 애매하게(Spring Boot `spring.sql.init` 일부 파일만 실행) 되어 있던 흐름을,
**MariaDB 컨테이너 init 스크립트** 기반으로 전환해서 다음을 달성했습니다.

- **`src/main/resources`의 모든 `.sql` 파일이 실행**되도록 변경
- 컨테이너 내부의 **단일 폴더(`/docker-entrypoint-initdb.d`)에 모아서 실행**되도록 구성
- 개발 단계에서 편하게 쓰도록 **매번 “깨끗한 DB”로 시작**(데이터 영속화 제거)

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
**데이터 seed용 `.sql`들을 모두 포함**하도록 확장했습니다.

> 주의: `Pawever.sql`은 DDL이므로 로컬 Spring SQL init에 포함하면
> “table already exists” 등 충돌 가능성이 커서 제외했습니다.

관련 설정 위치: `src/main/resources/application.yaml`의 `spring.sql.init.data-locations`

---

## 개발 단계 “항상 깨끗한 DB” 정책
현재 `compose.yaml`은 아래 정책을 의도합니다.

- **DB 영속화 제거**: `/var/lib/mysql`을 `tmpfs`로 사용
- 결과적으로 `docker compose up` 시점에 DB가 항상 초기 상태로 시작하며,
  MariaDB init 스크립트가 매번 다시 실행됩니다.

---

## 프로덕션 적용 시 반드시 바꿔야 할 사항(To-do)
현재 구성은 “개발 편의”를 최우선으로 했기 때문에, 프로덕션에는 그대로 적용하면 위험합니다.

### 1) DB 영속화(필수)
- **`tmpfs: /var/lib/mysql` 제거**
- 대신 **named volume** 혹은 **외부 관리 DB(NCP RDS 등)** 사용
- 프로덕션에서 DB가 재시작될 때마다 초기화되면 데이터가 모두 유실됩니다.

### 2) 스키마 관리 전략 정리(필수)
현재는 `Pawever.sql`(DDL)로 스키마를 만들고 `ddl-auto: none`으로 막는 구조입니다.
프로덕션에서는 아래 중 하나로 통일하는 것을 권장합니다.

- **(권장) Flyway/Liquibase 도입**
  - 스키마 변경을 마이그레이션으로 추적/롤백 가능
  - 배포 시점마다 의도치 않은 스키마 드리프트 방지
- **Hibernate DDL 사용 유지 시**
  - `ddl-auto` 운영 정책(보통 `validate` 또는 `none`)을 명확히 하고,
    스키마 변경은 마이그레이션으로만 처리하는 방향이 안전합니다.

### 3) 초기 시드(seed) 정책 분리(필수)
프로덕션에서는 “매번 전체 seed 재실행”이 아니라 보통 다음이 필요합니다.

- **최초 1회만 들어가야 하는 데이터**(기준 데이터/리스트업 데이터)
  - ex) `breeds_data.sql`, `missions_data.sql`, `funeral_companies_data.sql`, `faq_data.sql`
- **운영 중 변경될 수 있는 데이터**
  - SQL seed로 강제 업데이트하면 운영 데이터 덮어쓰기 위험

운영에서는 아래 옵션을 고려하세요.

- seed를 Flyway/Liquibase migration으로 편입(버전 관리)
- 또는 애플리케이션 레벨에서 “존재하면 스킵/업데이트 정책”을 명확히 정의

### 4) `/docker-entrypoint-initdb.d` 의존성 이해(중요)
MariaDB init 스크립트는 **데이터 디렉토리가 비어있을 때만 실행**됩니다.
프로덕션에서 영속 볼륨을 쓰면:

- 최초 1회만 init SQL이 실행되고
- 이후 배포/재시작 시에는 **절대 다시 실행되지 않습니다**

즉, 운영 변경(스키마/seed)은 init 스크립트로 관리하면 안 되고,
마이그레이션 도구(Flyway/Liquibase)로 관리하는 쪽이 안전합니다.

### 5) `.env`/비밀키 노출 주의(필수)
`compose`가 `.env`를 읽는 구조이므로,
프로덕션에서는 저장소에 비밀값이 커밋되지 않도록(Secret Manager 사용 등) 점검이 필요합니다.

---

## 빠른 확인 방법(개발)
Docker Desktop(또는 Docker daemon)이 켜져 있는 상태에서:

```bash
docker compose down --remove-orphans
docker compose up --build
```

그리고 MariaDB 로그에서 `/docker-entrypoint-initdb.d` 스크립트 실행 흔적을 확인하면 됩니다.

