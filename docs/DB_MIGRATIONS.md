# DB 마이그레이션 (Flyway)

스키마는 **Flyway**로 관리한다. 수동 `ALTER` 대신 **버전 마이그레이션 파일**을 추가하면 배포 시 자동 적용된다.

## 위치·규칙
- 마이그레이션 파일: `src/main/resources/db/migration/`
- 이름: `V{버전}__{설명}.sql` (예: `V2__add_pet_color.sql`) — 버전은 순차 증가, **한번 적용된 파일은 수정 금지**(불변).
- `V1__baseline.sql` = 현재 운영 스키마 스냅샷(`Pawever.sql` 복사본).

## baseline 안전장치
- `spring.flyway.baseline-on-migrate: true`, `baseline-version: 1`
- 이미 스키마가 있는 DB(운영 등)는 **V1을 실행하지 않고 기준선으로 마킹**만 한다 → 기존 스키마 무변경.
- 빈 DB(신규 환경)에서만 V1이 실행되어 스키마를 생성한다.
- 따라서 **Flyway 도입 첫 배포는 스키마를 바꾸지 않는다**(flyway 이력 테이블 생성 + baseline 마킹만).

## 새 스키마 변경 방법
1. `V2__설명.sql` 파일을 만들어 DDL 작성 (예: `ALTER TABLE pets ADD COLUMN color VARCHAR(20);`).
2. **`Pawever.sql`(=V1 baseline)은 더 이상 수정하지 않는다.** 새 변경은 항상 새 V 파일로.
3. 관련 JPA 엔티티도 함께 수정.
4. 배포 → Flyway가 순서대로 자동 적용.

## 프로파일별 설정
| 프로파일 | ddl-auto | Flyway |
|---|---|---|
| 기본(dev) | `validate` (엔티티↔스키마 정합성 검증) | on (baseline) |
| prod | `none` (Flyway가 스키마 소유) | on (baseline) |
| test | `create-drop` (H2) | **off** |

## 배포 전 로컬 검증 (권장)
로컬 `docker compose`에 MariaDB가 있으므로, 배포 전 한 번 확인한다:

```bash
docker compose down -v      # 깨끗한 상태로 (주의: 로컬 DB 데이터 삭제)
docker compose up --build
# 로그에 Flyway 초기화/baseline 메시지가 뜨고 앱이 정상 기동하면 OK
```

> 운영 반영은 `main` 머지 → GitHub Actions 배포로 이뤄진다. 첫 배포는 baseline 마킹만 하므로 스키마 변경이 없다.
