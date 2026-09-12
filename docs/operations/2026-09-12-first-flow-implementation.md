# 1차 구현 결과와 운영 적용 절차

입금 확인 → 모델링 담당자의 내 작업 → 자료 등록 → 모델링 완료 → 검수 담당자의 자료 열람을 구현했다. 로컬 개발·검증 결과이며 운영 배포 완료를 뜻하지 않는다. 백엔드와 랜딩 모두 `feat/modeling-handoff` 작업 브랜치다. 기준 커밋은 백엔드 `c9af267`, 랜딩 `e99eade`다.

## 사용할 화면

- `/admin/workflow`: 입금·제작 관리. 금액 대조, 결제/제작/담당자 표시, 미배정 해결, 유료 제작 대기·무료 주문의 명시적 작업 배정.
- `/admin/my-work`: 배정된 작업 시작, 고객 사진 열람, 모델링 자료 등록, 완료와 검수 인계. 검수자는 인계된 사진·파일을 열람한다.
- `/admin/accounts`: 기존 초대·비활성화와 작업 역할·기본 담당자 설정. 기존 `/admin/orders`와 주문 상세에도 새 작업 상태와 처리 화면을 연결했다. 검색·필터·페이지는 상세 변경 후 유지한다.

로그인 후 `/api/admin/me`로 현재 계정 상태·권한을 확인한다. 관리자에게는 입금·제작 관리, 제작 담당자에게는 내 작업이 첫 화면이다. OWNER/ADMIN, MODELING/DESIGN_QC 작업 역할을 구분한다. MARKETING/SUPPORT는 이번 제작 권한을 자동으로 받지 않는다. 사람별 권한 예외의 저장 구조와 DENY·만료·필수 데이터 권한 계산을 마련했지만, 전체 권한 ON/OFF 편집 화면은 이번 첫 흐름에 포함하지 않았다.

## 구현과 검증

W01의 기존 기능 기준선을 실행한 뒤 W02~W08의 저장·권한·입금·작업·파일·검수 인계·화면을 연결했다. W09의 자동 검증과 적용 절차를 작성했다. 실제 직원·실제 파일·운영 저장소 검증 및 과거 주문별 전환은 운영 적용 항목으로 남아 있다.

- 백엔드 전체 `gradlew.bat test`: 48개 suite, **362 통과 / 1 제외 / 실패 0**. 제외 1건은 별도 MariaDB 환경 변수가 필요한 마이그레이션 테스트이며 아래 실DB 실행에서 통과했다. 접수·가격·키링·정원·재신청·7일 기한·취소·배송·수령·기존 파기 테스트를 포함한다.
- 격리한 MariaDB 11.4.9의 Flyway + 실제 저장·조회: **13 통과 / 제외 0**. V1~V16 적용 → 10가지 과거 주문 상태 입력 → V17 적용·재적용, 원래 금액/기한/키링 보존, 과거 제작 단계 미추정, 소유자 자동 생성 없음, 실제 트랜잭션의 동시 입금 확인과 검수 인계를 검증했다.
- 랜딩 `pnpm test`: **390 통과**. `pnpm check`, `pnpm build` 통과. 기존 Vite 번들 크기 경고는 남아 있다. 새 관리자 Playwright **4건 통과**(모델러, 입금 불일치·409, 검수 열람, 역할·기본 배정). 기존 신청·플리마켓·사진 등록 Playwright **27건 통과 / 1건 기존 보류**. 보류는 실제 결제 수단 연동 시나리오다.

먼저 실패하는 통합/브라우저 테스트를 확인한 뒤 구현했다. 담당자 범위 밖 주문·사진·파일 접근, 비활성 계정의 기존 JWT, 관리자의 OWNER 초대 우회, 자기 계정 비활성화, 필수 자료 누락, 데이터 권한 DENY, 중복 키의 다른 요청, 늦은 version을 검증했다. S3 삭제 실패 시 파일 메타데이터를 남겨 재시도하는 검사도 포함한다.

**검증 범위의 한계.** 브라우저 검사는 API와 파일 전송을 모의 응답으로 처리한다. 서버 통합 검사는 MariaDB를 실제 사용하지만 S3 저장소 인터페이스는 모의 객체다. 실제 모델 원본과 비공개 버킷의 PUT/HEAD/GET/CORS는 운영 계정으로 별도 확인해야 한다. 전체 Hibernate `validate`는 기존 `funeral_company_images.image_url`의 DB NOT NULL/엔티티 nullable 불일치로 막혔다. 관련 없는 테이블은 변경하지 않았고, 운영과 같은 `ddl-auto: none` + Flyway로 작업 흐름의 실제 CRUD를 통과시켰다.

### 저장·전이 계약

V17은 기존 주문 행에 주문 종결/결제/배송 상태와 제작 단계, version, 수기 확인자·금액을 추가한다. 이번에는 별도 payments 테이블을 만들지 않고 기존 `paid_at`·금액 스냅샷과 같은 주문 aggregate에서 관리한다. 결제 대행사·기존 주문 처리와 중복 결제 원본을 만들지 않기 위한 범위 선택이다. 작업, 파일, 차단 이슈, 감사 이벤트, 명령 재실행 결과는 별도 테이블에 저장한다.

기존 `GoodsOrderStatus`는 호환 조회·기존 업무 전이용으로 남긴다. 기존 전이는 `GoodsSurveyFulfillment`에서 새 결제/종결/배송 사실을 함께 갱신하고, 새 제작 진행은 결제·배송 사실을 덮지 않는다. 등록된 작업의 강제 상태 이동과 일괄 제작 시작/되돌림은 막는다. 기존 `/status`를 통한 `PAYMENT_COMPLETED` 변경은 새 입금 대조 경로로 안내한다. 취소·송장·수령 API는 유지하며 새 종결/제작 상태에도 반영한다.

모든 새 변경 요청은 `Idempotency-Key`와 `version`을 보낸다. 같은 키·같은 본문은 저장된 결과를 재사용하고 다른 본문은 409다. 정상 입금 확인과 작업 생성, 모델링 완료와 검수 생성은 각각 같은 트랜잭션이다. 소규모 운영의 직원 변경은 계정 행을 같은 순서로 잠가 직렬화하고, 주문에는 잠금과 낙관적 version을 함께 사용한다. 명령 응답 스냅샷은 기존 AES converter로 암호화하며 서명 URL은 저장하지 않는다.

### 파일 계약

- 4면도: PNG/JPEG/WebP 이미지 한 파일(합성 4면도). 확장자·MIME·이미지 signature를 대조한다.
- 모델 원본: `.blend`, `.obj`, `.stl`, `.3mf`, `.fbx`, `.glb`, `.gltf`. 출력 파일: `.stl`, `.3mf`. 모델 파일은 안전한 바이너리 Content-Type으로 발급한다. 3D 형상 자체의 해석·출력 가능성 자동 검사는 하지 않는다.
- 각 파일은 1바이트 이상 100MiB 이하. 요청 수명 10분 안에 실제 객체 길이·Content-Type을 확인해야 확정된다. 다운로드 링크는 5분, 발급 시마다 현재 계정·주문 범위를 확인한다. 미확정 파일은 완료 요건으로 세지 않는다. 고객 사진은 최소 3장, 세 종류의 확정 파일이 있어야 모델링을 완료한다.

취소·만료·기존 사진 보관 기한이 지난 주문의 모델 파일은 열람을 막고 기존 일일 파기 일정에서 정리한다. 만료된 미확정 업로드도 정리한다. 기존 배송/수령의 보관 기한 계산을 그대로 사용하며, SHIPPED를 실제 배달 완료로 새로 추정하지 않는다.

## 운영 적용 순서

현재 운영 DB·직원·주문·버킷은 변경하지 않았다. Notion 실연동, 검수 승인/수정 요청과 이후 출력·포장, 새 고객 알림, 휴지통·정산·SLA는 후속 범위다.

1. DB와 관련 비공개 객체의 복구 수단을 확보한다. 운영 담당자가 첫 OWNER로 둘 기존 활성 ADMIN, 모델링/검수 계정, 실제 모델 샘플을 확인한다. 이름이나 ID를 코드에 고정하지 않는다.
2. 직원 변경 요청을 잠시 중지한 상태에서 V17을 적용하고 아래 읽기 전용 조회로 이전 결과를 확인한다. 백엔드와 랜딩을 같은 전환 창에 적용한다. 구버전의 enum·수정 API와 신버전을 동시에 운영하지 않는다.
3. 기존 활성 관리자 계정으로 작업 역할을 지정하고 모델링·검수 기본 담당자를 선택한다. 첫 OWNER 승격은 아래 별도 템플릿을 확인한 계정에만 적용한다. 신규 테스트 주문 한 건을 세 계정에서 처리해 실제 S3 업로드·확인·열람을 확인한 뒤 직원 업무를 재개한다.

```sql
-- 모두 읽기 전용. 운영자가 결과를 확인한다.
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT status, lifecycle_order_status, lifecycle_payment_status, lifecycle_shipment_status,
       production_stage, COUNT(*) AS orders
FROM goods_survey_fulfillments
GROUP BY status, lifecycle_order_status, lifecycle_payment_status, lifecycle_shipment_status, production_stage;
SELECT order_number, status FROM goods_survey_fulfillments
WHERE status='IN_PRODUCTION' AND production_stage IS NULL;
SELECT id, name, role, status FROM admin_accounts ORDER BY id;
SELECT id, modeling, review, version FROM workflow_settings;
```

과거 제작 중 주문은 `requiresMigrationReview=true`로 보류된다. 단계 근거가 없으면 자동 모델링 배정하지 않는다. 이미 결제된 제작 대기/무료 주문도 관리자가 명시적으로 배정한다. 운영 주문별 실제 전환 대상 보고는 운영 DB의 위 조회와 담당자 확인 후 작성한다.

첫 OWNER 템플릿은 [modeling-handoff-first-owner.sql](modeling-handoff-first-owner.sql)이다. 기본 ID는 NULL이고 마지막 문장은 ROLLBACK이다. 사용자에게 확인된 계정 ID와 적용 결과를 검토한 후에만 운영자가 COMMIT으로 바꾼다. 운영에서는 실행하지 않았으며, 로컬 테스트 DB에서 NULL ID + ROLLBACK으로 변경 0건을 확인했다.

**복구.** 이 구현에는 상태를 되돌리는 OFF 스위치가 없다. 새 역할이나 작업 상태를 저장한 뒤 구버전만 재배포하면 호환되지 않는다. 문제가 생기면 직원 변경 요청을 중지하고 DB·객체 상태를 보존한 채 수정 배포한다. 전환 전으로 돌아가야 한다면 전환 이후 변경을 대조한 다음 같은 시점의 DB·필요 객체와 백엔드/랜딩을 함께 복구한다. V17 테이블 삭제나 Flyway repair를 일반적인 복구 절차로 사용하지 않는다.

### 실DB 검사 재실행

미리 생성한 격리 로컬 MariaDB에만 실행하도록 URL을 제한했다. 운영 DB를 테스트 대상으로 지정하지 않는다.

```powershell
$env:WORKFLOW_JDBC_URL='jdbc:mariadb://127.0.0.1:33317/workflow_validation'
$env:WORKFLOW_DB_USER='<local test user>'
$env:WORKFLOW_DB_PASSWORD='<local test password>'
.\gradlew.bat test --tests '*WorkflowIntegrationTest' --tests '*WorkflowMigrationTest' --tests '*WorkflowRetentionTest' --tests '*ArtifactFormatsTest'
```

마이그레이션 검사는 `workflow_validation_migration_*`라는 별도 테스트 DB를 생성해 기존 상태를 재현한다. 다른 DB를 지우거나 Flyway clean을 호출하지 않는다.
