# PAW-EVER Admin and Notion Integration Implementation Plan

> 2026-09-12 개발용 정리본. 다운로드 원본을 보존하고 저장소에 옮겼다. 먼저 [개발 보완서](../../operations/2026-09-12-development-addendum.md)를 읽는다. 기술 보완과 원문 충돌 처리 지침은 보완서를 따른다. 보완서의 Q1~Q3는 아직 답변되지 않았으며 승인된 운영 결정으로 간주하지 않는다.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing PAW-EVER admin into a server-authoritative order, production, shipment, permission, notification, and Notion-synchronization system without rebuilding already working authentication, intake, photo storage, or order-list foundations.

**Architecture:** MariaDB remains the only canonical state. Spring Boot exposes role-filtered work queues and server-computed actions, persists external work through transactional outbox records, and integrates with Aligo and Notion asynchronously. React renders those contracts and keeps the primary path in the unified work queue. Notion stores production artifacts and explicit work requests, never payment, shipping, deletion, or notification authority.

**Tech Stack:** Java 17, Spring Boot 4.0.2, Spring Security, Spring Data JPA, MariaDB, Flyway, AWS SDK S3, Spring RestClient, React 19, TypeScript, Vite 7, Tailwind CSS 4, shadcn/Radix, Vitest, Playwright.

## Global Constraints

- Read `docs/superpowers/specs/2026-09-10-pawever-admin-operations-ux-design.md` and `docs/operations/2026-09-11-pawever-developer-final-handoff.md` before editing code.
- Record each repository's current HEAD and working-tree changes before implementation. The original baseline was back `eb4d912fe0062fdce5e335f90da7b52e86d7f6ad` and landing `26654482925b12ed262e0c1576b0adcbebf49b34`; the 2026-09-12 review baseline is in the addendum. Preserve concurrent work. Do not rebase a dirty working tree as a setup shortcut.
- Run the baseline suites first. Do not attribute an existing failure to new work.
- Preserve user data. Never infer a detailed production stage from the coarse legacy `IN_PRODUCTION` state.
- Keep bank transfer reconciliation manual. Do not add a new payment gateway.
- Reuse existing admin authentication, S3 photo storage, access logging, Aligo client, and responsive admin shell.
- Remove the client transition table after the server `allowedActions` contract is active. Do not maintain two transition models.
- Enforce row, field, and action permissions on the server. UI hiding is convenience only.
- Use private S3 object keys. Never persist public customer-photo URLs in Notion.
- Use outbox and unique idempotency keys for Aligo and Notion work.
- Ship Notion sync, postal result parsing, SLA, and compensation behind independent feature flags that default OFF.
- Make each migration compatible with existing rows at deploy time, but do not keep an indefinite legacy compatibility layer after cutover.
- Commit only files belonging to the task. Do not touch unrelated dirty workspace files.

---

## Task 1: Establish Baselines and Contract Fixtures

**2026-09-12 추가 완료 조건:**

- [ ] 채널별 금액·키링·배송비·입금 기한 스냅샷, 정원 반환, 재신청, PICKUP 제한의 기존 테스트를 기준선에 포함한다. 보완서에 기록한 별도 작업의 7일 기한 변경을 보존한다.
- [ ] H2에서 Flyway가 꺼져 있음을 기록하고, MariaDB/Flyway migration 테스트 실행 방법을 정한다. 기존 로컬 DB를 지우는 명령을 테스트 준비로 사용하지 않는다.
- [ ] 공통 변경 명령의 멱등 키 저장·동시 중복 요청·응답 재사용·키/본문 불일치 검증과 감사 이벤트를 Task 4/5보다 먼저 사용할 기반 작업으로 추가한다. outbox 멱등 키만으로 이 작업을 대신하지 않는다.

**Files:**

- Inspect: `Pawever-back/src/main/resources/db/migration/`
- Inspect: `Pawever-back/src/main/java/com/pawever/backend/admin/`
- Inspect: `Pawever-back/src/main/java/com/pawever/backend/goodssurvey/`
- Inspect: `Pawever-landing/client/src/pages/AdminOrders.tsx`
- Inspect: `Pawever-landing/client/src/pages/AdminOrderPanel.tsx`
- Inspect: `Pawever-landing/client/src/pages/adminOrderStatus.ts`
- Create: `Pawever-back/src/test/resources/contracts/admin-work-queue.json`
- Create: `Pawever-landing/client/src/test/fixtures/adminWorkQueue.ts`

- [ ] In `Pawever-back`, run the baseline:

```bash
./gradlew test
```

- [ ] In `Pawever-landing`, run the baseline:

```bash
pnpm test
pnpm check
pnpm build
```

- [ ] Record failures, if any, in the implementation task notes before changing code.
- [ ] Confirm the latest Flyway version. At the stated baseline it is `V16`; renumber the following migrations if main advanced.
- [ ] Add a backend JSON contract fixture containing one active color-mapping row with separate statuses, one blocking issue, one allowed action, assignee, due date, and version.
- [ ] Add the equivalent typed frontend fixture.
- [ ] Assert the fixture contains no guardian name, phone, address, payment detail, or photo URL for a PRODUCTION user.

Expected work-row contract:

```json
{
  "orderNumber": "PE-2026-000139",
  "petName": "타지",
  "orderStatus": "ACTIVE",
  "paymentStatus": "CONFIRMED",
  "productionStage": "COLOR_MAPPING",
  "shipmentStatus": "NOT_READY",
  "assignee": { "id": 3, "name": "박나혜" },
  "dueAt": null,
  "blockingIssues": [],
  "allowedActions": ["START_TASK", "REPORT_ISSUE", "OPEN_DETAIL"],
  "version": 7
}
```

- [ ] Commit:

```bash
git add src/test/resources/contracts
git commit -m "test: pin admin work queue contract"
```

Commit the frontend fixture in its repository with `test: pin admin work queue fixture`.

## Task 2: Split the Lifecycle State Model

**2026-09-12 추가 완료 조건:**

- [ ] GoodsSurveyService, GoodsOrderService, GoodsSurveyRetentionService, 두 survey repository와 payment 경로까지 상태 사용처를 조사한다.
- [ ] 완료 주문은 정원을 계속 차지하고, 실패·만료·취소의 반환 조건과 CANCEL_FAILED의 점유를 보존한다. 온라인 재신청/번호 제한과 FLEA 중복 신청을 같은 새 규칙으로 검증한다.
- [ ] 기존 주문 금액과 paymentExpiresAt을 재계산하지 않는다. 장소 없는 PICKUP은 Q1 답변 전 임의 보정하지 않는다.
- [ ] 미확인 행 보고와 차단 처리가 준비되기 전 새 lifecycle을 운영에 활성화하지 않는다. Task 4의 이슈 스키마를 Task 2의 backfill이 먼저 참조하지 않도록 적용 순서를 조정한다.

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V17__split_goods_order_lifecycle.sql`
- Create: `Pawever-back/src/main/java/com/pawever/backend/goodssurvey/entity/GoodsOrderLifecycleStatus.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/goodssurvey/entity/GoodsPaymentStatus.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/entity/ProductionStage.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/shipment/entity/ShipmentStatus.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/shipment/entity/PickupLocation.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/goodssurvey/entity/GoodsSurveyFulfillment.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/dto/AdminOrderSummary.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/dto/AdminOrderDetail.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/goodssurvey/GoodsLifecycleMigrationTest.java`

- [ ] Write a failing migration integration test for every legacy `GoodsOrderStatus` mapping.
- [ ] Define the exact enums:

```java
public enum GoodsOrderLifecycleStatus { ACTIVE, COMPLETED, CANCELED, EXPIRED }
public enum GoodsPaymentStatus {
    PENDING, CONFIRMED, NOT_REQUIRED, REFUND_PENDING, REFUNDED, FAILED, EXPIRED
}
public enum ProductionStage {
    BLOCKED, MODELING_QUEUE, MODELING, MODEL_REVIEW, COLOR_MAPPING,
    PLATE_PREPARATION, PRINT_QUEUE, PRINTING, POST_PROCESSING, QC, PACKING, COMPLETE
}
public enum ShipmentStatus {
    NOT_READY, AWAITING_POST_OFFICE_RESULT, ACCEPTED, DELIVERED,
    READY_FOR_PICKUP, PICKED_UP, EXCEPTION
}
public enum PickupLocation { SANGSANG_HALL, DAVINCI_HALL }
```

- [ ] Add lifecycle columns and an optimistic `version` to the fulfillment/order aggregate. Keep nullable values only during this migration.
- [ ] Backfill deterministic rows using the mapping in the developer handoff.
- [ ] Follow the addendum's migration rules and Q2. Prove detailed stages from evidence; emit unresolved legacy rows in the dry-run report. BLOCKED plus a migration-review issue is the proposed treatment, not an approved production backfill while Q2 is unanswered.
- [ ] Add a migration report query under a SQL comment showing unresolved row count by legacy status.
- [ ] Make new lifecycle columns non-null after the backfill in the same migration when safe.
- [ ] Update order summary and detail DTOs to return all four statuses and version.
- [ ] Keep legacy status reads only until Task 11 switches every caller; mark them as removal targets in code with a link to this task number and a fixed removal milestone.
- [ ] Run:

```bash
./gradlew test --tests '*GoodsLifecycleMigrationTest'
./gradlew test --tests '*AdminOrderServiceTest'
```

- [ ] Commit:

```bash
git add src/main/resources/db/migration/V17__split_goods_order_lifecycle.sql src/main/java src/test/java
git commit -m "feat: split order lifecycle states"
```

## Task 3: Expand Staff Roles and Server Permissions

**2026-09-12 추가 파일과 완료 조건:**

- [ ] SecurityConfig.java, AdminAuthenticationFilter.java, 관리자 세션/권한 조회와 관련 보안 테스트를 수정 대상에 추가한다.
- [ ] /api/admin/**와 /api/production/**에서 활성 staff만 인증하고 앱 회원 토큰은 거절한다. OWNER/MARKETING/SUPPORT도 업무별 허용 범위로 진입할 수 있다.
- [ ] 두 staff 경로에 PATCH와 Idempotency-Key를 포함한 CORS preflight를 검증한다. 공개 접수·앱 인증 경계는 보존한다.
- [ ] 역할 변경·정지·비활성화는 다음 요청부터 적용한다. 자기 권한 변경 금지, 마지막 OWNER 보호, 행 범위, 필수 데이터 권한을 검증한다.

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V18__create_staff_permissions.sql`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/AdminRole.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/WorkRole.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/PermissionKey.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/PermissionOverrideEffect.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/AdminAccountWorkRole.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/RolePermission.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/AccountPermissionOverride.java`
- Create: corresponding repositories under `admin/repository/`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/service/EffectivePermissionService.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/security/AdminPrincipal.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/service/AdminAccountService.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/controller/AdminAccountController.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/admin/service/EffectivePermissionServiceTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/admin/service/AdminFieldExposureTest.java`

- [ ] Write failing tests proving:
  - OWNER has the specified defaults; DENY precedence and non-overridable safety rules are tested separately. The last active OWNER cannot lose essential recovery/account permissions.
  - work-role permissions are additive.
  - account DENY overrides role ALLOW.
  - an expired override is ignored.
  - MARKETING sees only consented contacts.
  - SUPPORT receives technical IDs and errors but no PII by default.
- [ ] Replace `AdminRole` values with `OWNER`, `ADMIN`, `PRODUCTION`, `MARKETING`, `SUPPORT`.
- [ ] Add multiple work roles per account: `MODELING`, `DESIGN_QC`, `PRINT_FINISHING`, `PACKING_SHIPPING`.
- [ ] Use the canonical keys in the original operations specification §5.4 and the addendum's explicit extensions. Keep role defaults, row scope, field exposure, and action prerequisites consistent. Do not seed the handoff's superseded aliases.
- [ ] Store override effect, reason, creator, created time, and optional expiry.
- [ ] Return effective permissions from `GET /api/admin/staff/{accountId}/effective-permissions`.
- [ ] Add role and permission mutation endpoints guarded by `MANAGE_ACCOUNTS` and `MANAGE_ACCOUNT_PERMISSIONS`.
- [ ] Preserve the existing email invitation and password flow. Confirm invitation token hash, single-use behavior, and 24-hour expiry remain enforced.
- [ ] Replace DTO creation that relies on only `ADMIN/PRODUCTION` with `EffectivePermissionService` field filtering.
- [ ] Log every role and override change.
- [ ] Run:

```bash
./gradlew test --tests '*EffectivePermissionServiceTest' --tests '*AdminFieldExposureTest' --tests '*AdminAccountServiceTest'
```

- [ ] Commit:

```bash
git add src/main/resources/db/migration/V18__create_staff_permissions.sql src/main/java src/test/java
git commit -m "feat: add staff roles and permission overrides"
```

## Task 4: Add Production Tasks, Issues, and Server-Computed Actions

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V19__create_production_tasks_and_issues.sql`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/entity/ProductionTask.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/entity/ProductionTaskStatus.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/entity/ProductionArtifact.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/issue/entity/OrderIssue.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/issue/entity/IssueType.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/issue/entity/IssueStatus.java`
- Create: repositories in `production/repository/` and `issue/repository/`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/service/ProductionWorkflowService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/service/AllowedActionService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/controller/ProductionTaskController.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/dto/AdminAllowedAction.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/service/AdminOrderService.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/AdminOrderView.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/production/service/ProductionWorkflowServiceTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/admin/service/AllowedActionServiceTest.java`

- [ ] Write failing tests for payment-gated model-task creation, exactly-once next-task creation, missing-artifact blocking, assignment, and partial bulk results.
- [ ] Persist one task per `order + stage + attempt`, with status `WAITING`, `IN_PROGRESS`, `COMPLETED`, or `BLOCKED`.
- [ ] Require the artifacts appropriate to each transition:
  - MODELING complete: multiview plus model source plus STL/3MF.
  - COLOR_MAPPING complete: at least one body-part mapping to an active filament.
  - PLATE_PREPARATION complete: PrintBatch membership and 3MF/G-code.
  - QC complete: checklist and per-order pass result.
- [ ] Create the next task in the same transaction as completion and guard it with a unique constraint.
- [ ] Insert migration-review issues for unresolved legacy rows from Task 2.
- [ ] Compute `allowedActions` from lifecycle, open blocking issues, task ownership, effective permissions, and resource version.
- [ ] Expand work queues to `PAYMENT_CHECK`, `PRODUCTION_QUEUE`, `MY_TASKS`, `IN_PRODUCTION`, `PACKING_SHIPPING`, `PICKUP`, `DONE`, `CANCELED_EXPIRED`, `PROBLEM`, `TRASH`.
- [ ] Default-sort blocking issues, overdue, at-risk, due date, and oldest submission.
- [ ] Return row-level `allowedActions`; never expose an action based only on system role.
- [ ] Run:

```bash
./gradlew test --tests '*ProductionWorkflowServiceTest' --tests '*AllowedActionServiceTest' --tests '*AdminOrderServiceTest'
```

- [ ] Commit:

```bash
git add src/main/resources/db/migration/V19__create_production_tasks_and_issues.sql src/main/java src/test/java
git commit -m "feat: add production work queues"
```

## Task 5: Implement Manual Payment Confirmation

**Files:**

- Create: `Pawever-back/src/main/java/com/pawever/backend/payment/entity/GoodsPayment.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/payment/repository/GoodsPaymentRepository.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/payment/service/ManualPaymentService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/payment/controller/AdminPaymentController.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/payment/dto/ConfirmPaymentRequest.java`
- Create: a new, unused Flyway migration for payment schema if V17 has already been applied. Finalize payment DDL within V17 only before its first application anywhere; never modify an applied migration. Renumber later examples as needed.
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/service/AdminOrderService.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/payment/service/ManualPaymentServiceTest.java`

- [ ] Write tests proving only `CONFIRM_PAYMENT` can confirm, the expected amount and depositor snapshot remain unchanged, a second confirmation is idempotent, and confirmation creates exactly one modeling task.
- [ ] Store expected depositor name, expected amount, confirmed amount, confirmed by, confirmed at, and memo.
- [ ] Implement `POST /api/admin/orders/{orderNumber}/payments/confirm` with `Idempotency-Key` and version.
- [ ] Return `PAYMENT_MISMATCH` rather than silently confirming when the entered amount differs.
- [ ] Keep bank lookup human-operated; do not add scraping or a bank API.
- [ ] Search all `TossPaymentsClient` references. Remove it only from the manual bank-transfer admin path. Do not delete unrelated active payment use without evidence.
- [ ] Run:

```bash
./gradlew test --tests '*ManualPaymentServiceTest' --tests '*AdminOrderServiceTest'
```

- [ ] Commit with `feat: add manual payment confirmation`.

## Task 6: Add Filaments, Print Batches, Artifacts, and Compensation

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V20__create_print_operations.sql`
- Create: entities and repositories under `production/` for `Filament`, `OrderFilamentMapping`, `PrintBatch`, `PrintBatchItem`, `WorkCompensation`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/service/PrintBatchService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/production/service/QualityControlService.java`
- Create: controllers and DTOs under `production/controller/` and `production/dto/`
- Test: `Pawever-back/src/test/java/com/pawever/backend/production/service/PrintBatchServiceTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/production/service/QualityControlServiceTest.java`

- [ ] Write failing tests for active-filament mapping, one order in one active batch, batch start, per-item partial failure, QC pass, and unique compensation.
- [ ] Store a stable filament ID, material, brand, color, available grams, reorder grams, measurement date, and active flag.
- [ ] Require a body part plus actual filament ID for each mapping; do not accept free-text color as completion.
- [ ] Store PrintBatch state `COMPOSING`, `READY`, `PRINTING`, `SUCCEEDED`, `PARTIALLY_FAILED`, `FAILED`.
- [ ] Starting a batch moves the batch and connected ready items atomically.
- [ ] Completing a batch accepts per-item result and creates issues only for failed items.
- [ ] Add S3-backed artifacts with kind, object key, version, uploader, order, task, and optional batch.
- [ ] Reuse existing S3 infrastructure and authorization patterns; do not add a second storage client.
- [ ] Generate a 3,000 KRW compensation only when the feature is enabled, the worker is eligible, and QC passes. Protect with a unique key.
- [ ] Reprints do not automatically create a second compensation.
- [ ] Run targeted tests and then `./gradlew test`.
- [ ] Commit with `feat: add print batch and qc operations`.

## Task 7: Add Operation Settings, SLA, Rollback Preflight, and Trash

**2026-09-12 추가 완료 조건:**

- [ ] 기존 계약 자료 보존과 사진/배송 자료 정리 경로를 재사용한다. 휴지통 파기가 분리 보관 대상 기록을 지우지 않는지 검증한다.
- [ ] soft delete는 정원 반환 조건이 아니다. 실제 취소·환불 상태와 별도로 검증한다. Q3 답변 전 복구 역할을 운영에 부여하지 않는다.

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V21__create_operation_settings_and_audit.sql`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/OperationSettings.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/entity/AuditEvent.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/service/OperationSettingsService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/service/OrderTransitionService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/service/TrashService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/admin/controller/AdminSettingsController.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/admin/controller/AdminOrderController.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/admin/service/OperationSettingsServiceTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/admin/service/OrderTransitionServiceTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/admin/service/TrashServiceTest.java`

- [ ] Seed SLA OFF, 7 business days, apply-to-existing false; compensation OFF and 3,000 KRW; current default assignees.
- [ ] Write tests proving settings changes affect only new tasks unless an explicit bulk assignment occurs.
- [ ] Implement holiday-aware business-day calculation behind SLA enabled state. If the holiday source is not decided, test weekends and keep production SLA OFF.
- [ ] Implement transition preflight returning sent notifications, dependent tasks, batches, artifacts, and version.
- [ ] Require an explicit second transition call with the same target and fresh version.
- [ ] Add soft-delete fields and exclude them from every default repository query.
- [ ] Resolve addendum Q3 before assigning production trash/restore access. Implement restore preflight for order-number conflicts and already-purged artifacts, with the selected permission policy enforced server-side.
- [ ] Add a scheduled purge at 30 days and retention handling at 90 days after delivery or case closure.
- [ ] Audit payment, status, assignment, setting, permission, export/import, rollback, trash/restore/purge, and notification resend.
- [ ] Run targeted tests and full backend suite.
- [ ] Commit with `feat: add safe operations controls`.

## Task 8: Add Notification Outbox and Approved AlimTalk Mapping

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V22__create_outbox_and_notifications.sql`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notification/entity/OutboxEvent.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notification/entity/NotificationDispatch.java`
- Create: repositories under `notification/repository/`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notification/alimtalk/AlimTalkTemplate.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notification/alimtalk/AlimTalkVariableMapper.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notification/service/OutboxDispatcher.java`
- Modify: `Pawever-back/src/main/java/com/pawever/backend/notification/sms/SmsClient.java`
- Modify or replace after cutover: `Pawever-back/src/main/java/com/pawever/backend/notification/sms/GoodsOrderSmsListener.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/notification/service/NotificationDispatchServiceTest.java`

- [ ] Write tests for every approved template variable mapping, missing variable rejection, idempotent dispatch, retry, and issue creation after terminal failure.
- [ ] Store the exact approved template code, recipient snapshot, variables JSON, provider request ID, attempts, sent time, and idempotency key.
- [ ] Map lifecycle events exactly as specified in the developer handoff.
- [ ] Ensure bank-transfer receipt sends the payment-request template, not both payment request and generic application-complete.
- [ ] Keep the new production-start template disabled until an approved code is configured.
- [ ] Domain transactions insert outbox rows only; the dispatcher calls Aligo after commit.
- [ ] Notification failure never rolls back order state.
- [ ] Redact phone, address, tokens, and URLs from logs and Telegram.
- [ ] Run targeted and full tests.
- [ ] Commit with `feat: make customer notifications idempotent`.

## Task 9: Implement Pickup and Registered-Mail Export/Import

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V23__create_shipment_batches.sql`
- Create: shipment entities and repositories for `Shipment`, `ShipmentExportBatch`, `ShipmentExportItem`, `ShipmentImport`, `ShipmentImportRow`
- Create: `Pawever-back/src/main/java/com/pawever/backend/shipment/service/PostalExportService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/shipment/service/PostalImportService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/shipment/service/PickupService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/shipment/controller/AdminShipmentController.java`
- Add a maintained XLSX dependency only after confirming no installed dependency can write XLSX with explicit text cell types.
- Test: `Pawever-back/src/test/java/com/pawever/backend/shipment/service/PostalExportServiceTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/shipment/service/PostalImportServiceTest.java`
- Test resource: `Pawever-back/src/test/resources/postal/returned-sample.xlsx`

- [ ] Write an export test that opens the workbook and asserts:
  - one sheet named `준등기`;
  - no header;
  - exactly six columns;
  - postal code and phone are string cells;
  - normalized 11-digit phone;
  - first row is data.
- [ ] Validate all selected orders before generating the batch. A validation failure changes no state.
- [ ] Persist normalized six-field row snapshots and fingerprints outside the XLSX.
- [ ] Return a stable batch ID and allow the same file bytes to be downloaded again.
- [ ] Guard export creation with an idempotency key and row locks.
- [ ] Accept the actual post-office result file as multipart. Until an actual sample is supplied, keep import apply OFF and test the agreed assumed layout only.
- [ ] Compare normalized content, not row order.
- [ ] Return `MATCHED`, `MISSING`, `EXTRA`, `CHANGED`, `AMBIGUOUS`, `DUPLICATE_TRACKING`, `INVALID_TRACKING`, `ALREADY_APPLIED`.
- [ ] Allow matched rows to apply separately from error rows. Never roll back applied rows during reprocessing.
- [ ] Queue shipment AlimTalk only after a 13-digit tracking number is applied.
- [ ] Require pickup location for PICKUP and separate SANGSANG_HALL and DAVINCI_HALL bulk operations.
- [ ] Run targeted and full tests.
- [ ] Commit with `feat: add postal batch and pickup flows`.

## Task 10: Implement Notion Link, Outbox Sync, and Signed Webhook

**2026-09-12 추가 완료 조건:**

- [ ] SecurityConfig에서 webhook의 일반 로그인 요구를 분리하고, 전용 raw-body 서명 검증과 초기 검증 토큰 수신 절차를 테스트한다. 잘못된 서명은 큐에 넣지 않는다.
- [ ] Notion 사용자와 내부 활성 계정의 매핑 및 명령 요청자 귀속을 설계한다. 담당자를 요청자로 간주하지 않는다. 식별 불명확·복수 편집·봇 편집의 경우 임의 사용자로 실행하지 않는다.
- [ ] 명령 식별자, 기대 버전, 재전송 키와 요청 소비 절차를 정한다. 처리 중 새 요청이 들어왔을 때 과거 요청의 초기화가 새 요청을 지우지 않는 테스트를 먼저 작성한다.
- [ ] 429/529 Retry-After와 연결/워크스페이스 제한을 처리한다. 보기 생성·수정 API는 실제 필요한 기능과 권한을 확인해 사용한다.

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V24__create_notion_sync.sql`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/config/NotionProperties.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/client/NotionClient.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/entity/NotionPageLink.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/entity/NotionSyncJob.java`
- Create: repositories under `notion/repository/`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/service/NotionSyncService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/service/NotionChangeRequestService.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/controller/NotionWebhookController.java`
- Create: `Pawever-back/src/main/java/com/pawever/backend/notion/controller/AdminNotionController.java`
- Modify: `Pawever-back/src/main/resources/application.yaml`
- Modify: `Pawever-back/.env.example`
- Test: `Pawever-back/src/test/java/com/pawever/backend/notion/NotionWebhookControllerTest.java`
- Test: `Pawever-back/src/test/java/com/pawever/backend/notion/NotionSyncServiceTest.java`

- [ ] Add `notion.sync-enabled=false` and empty connection/data-source settings.
- [ ] Use Spring `RestClient`; do not add a Notion SDK unless RestClient demonstrably increases total complexity.
- [ ] Send `Notion-Version: 2026-03-11` on every request.
- [ ] Implement only required calls: retrieve page, retrieve/query data source, update page properties, and create a page in a data source.
- [ ] Handle 429 using `Retry-After`; bound retries and persist the next attempt time.
- [ ] Accept the one-time verification token flow and store the token only through secret configuration.
- [ ] Verify `X-Notion-Signature` over the raw request body with HMAC-SHA256 and constant-time comparison.
- [ ] Return quickly after recording the event; fetch the latest page asynchronously.
- [ ] Process a command only when `변경 요청` is not `없음`, the linked admin task exists, the admin version matches, the actor has permission, and the transition is allowed.
- [ ] On success, update admin state once, write an audit event, clear the request, and mirror the new version/result.
- [ ] On stale version, write `NOTION_SYNC_CONFLICT`; on API/retry exhaustion, write `NOTION_SYNC_ERROR`.
- [ ] Ignore server-authored mirror-only changes because they have no explicit change request.
- [ ] Expose health counts and retry actions to OWNER/SUPPORT without exposing PII.
- [ ] Write tests for invalid signature, duplicate event, out-of-order event, server mirror loop, stale version, 429, and Notion downtime not blocking core order work.
- [ ] Run:

```bash
./gradlew test --tests '*Notion*'
./gradlew test
```

- [ ] Commit with `feat: integrate notion production workspace`.

## Task 11: Switch the Frontend to Server Contracts

**Files:**

- Modify: `Pawever-landing/client/src/lib/adminApi.ts`
- Modify: `Pawever-landing/client/src/lib/adminApi.test.ts`
- Modify: `Pawever-landing/client/src/pages/AdminOrders.tsx`
- Modify: `Pawever-landing/client/src/pages/AdminOrderPanel.tsx`
- Delete after callers move: `Pawever-landing/client/src/pages/adminOrderStatus.ts`
- Modify or delete: `Pawever-landing/client/src/pages/adminOrderStatus.test.ts`
- Create: `Pawever-landing/client/src/lib/adminContracts.ts`
- Create: `Pawever-landing/client/src/lib/adminPermissions.ts`

- [ ] Change TypeScript types to separate order, payment, production, and shipment statuses.
- [ ] Add typed `allowedActions`, `blockingIssues`, `assignee`, `dueAt`, `version`, and permission keys.
- [ ] Add client methods for dashboard, work queues, tasks, preflight, assignment, settings, shipping batches, trash, and Notion health.
- [ ] Require `Idempotency-Key` and version for mutations. Generate one key per user action and reuse it only when retrying that action.
- [ ] Render controls only from server `allowedActions` and effective permissions.
- [ ] Replace the local `MANUAL_TRANSITIONS`, `PRODUCTION_SETTABLE`, and duplicated `ADMIN_ORDER_VIEWS` with labels and server-returned view metadata.
- [ ] Delete `adminOrderStatus.ts` once `rg` proves no caller remains.
- [ ] Replace `window.confirm` for rollback, cancel, delete, and bulk operations with accessible application modals.
- [ ] Tests must prove an omitted allowed action produces no button and a 409 refreshes the row without losing filters.
- [ ] Run:

```bash
pnpm test -- client/src/lib/adminApi.test.ts
pnpm test
pnpm check
pnpm build
```

- [ ] Commit with `refactor: use server admin action contracts`.

## Task 12: Build Role-Focused Work Queues and Admin Dashboard

**Files:**

- Modify: `Pawever-landing/client/src/pages/AdminOrders.tsx`
- Modify: `Pawever-landing/client/src/pages/AdminOrderPanel.tsx`
- Modify: `Pawever-landing/client/src/components/AdminShell.tsx`
- Create: `Pawever-landing/client/src/pages/AdminDashboard.tsx`
- Create: `Pawever-landing/client/src/pages/AdminMyWork.tsx`
- Create: `Pawever-landing/client/src/components/admin/WorkQueueTable.tsx`
- Create: `Pawever-landing/client/src/components/admin/PrimaryRowAction.tsx`
- Create: `Pawever-landing/client/src/components/admin/IssueSummary.tsx`
- Test: `Pawever-landing/client/src/pages/AdminMyWork.test.tsx`
- Test: `Pawever-landing/client/src/pages/AdminDashboard.test.tsx`

- [ ] Make `내 작업` the default route for non-OWNER staff and dashboard the default for OWNER.
- [ ] Keep one unified row component and change columns/actions by server view metadata; do not fork five separate order tables.
- [ ] Show current stage, blocking issue, assignee, due date, and one primary action in each row.
- [ ] Keep normal start/complete/report actions inline. Use the detail panel for files, history, notes, and exception editing.
- [ ] Preserve route, view, filters, query, page, scroll, and selection when opening and closing detail.
- [ ] Add bulk selection with server-returned eligibility and explicit skipped-order results.
- [ ] Make every dashboard card a filtered link into the same work queue.
- [ ] Show SLA and compensation as `꺼짐` when disabled; never render misleading zeroes.
- [ ] Add loading skeleton, actionable empty state, error retry, keyboard focus restoration, and live result announcements.
- [ ] Verify 320 px width and 200% zoom; critical actions must remain reachable.
- [ ] Run tests, check, and build.
- [ ] Commit with `feat: add role focused admin work queues`.

## Task 13: Build Safe Admin Operations, Shipping, and Staff Settings

**Files:**

- Create: `Pawever-landing/client/src/components/admin/TransitionPreflightDialog.tsx`
- Create: `Pawever-landing/client/src/components/admin/TrashDialog.tsx`
- Create: `Pawever-landing/client/src/pages/AdminTrash.tsx`
- Create: `Pawever-landing/client/src/pages/AdminShipping.tsx`
- Create: `Pawever-landing/client/src/components/admin/PostalImportComparisonDialog.tsx`
- Modify: `Pawever-landing/client/src/pages/AdminAccounts.tsx`
- Create: `Pawever-landing/client/src/pages/AdminOperationSettings.tsx`
- Create: `Pawever-landing/client/src/pages/AdminIntegrationHealth.tsx`
- Test: corresponding Vitest files and `Pawever-landing/e2e/admin-operations.spec.ts`

- [ ] Implement preflight dialog with sent template code, sent time, affected batch/artifact counts, and explicit `이전 단계로 이동` button.
- [ ] Implement trash and restore. Show purge date and irrecoverable artifact warning.
- [ ] Implement permission editor with role defaults, account overrides, DENY precedence, reason, expiry, and effective-permission preview.
- [ ] Implement SLA, default-assignee, and compensation settings. Show that changes affect new work only.
- [ ] Implement packaging selection and `선택 주문 포장 완료 및 우체국 파일 받기` as one action.
- [ ] Start browser download only after the server returns the stable export batch.
- [ ] Implement comparison counts and separate tabs for each postal result class.
- [ ] Allow row correction/reprocessing without undoing applied rows.
- [ ] Separate pickup lists by SANGSANG_HALL and DAVINCI_HALL.
- [ ] Implement Notion health with pending/error/conflict counts and authorized retry.
- [ ] All modals must use native or existing shadcn dialog semantics, Escape close where safe, focus trap/restore, descriptive labels, and consequence-specific buttons.
- [ ] Run:

```bash
pnpm test
pnpm check
pnpm build
pnpm test:e2e -- e2e/admin-operations.spec.ts
```

- [ ] Commit with `feat: add safe admin operation tools`.

## Task 14: Cut Over Data and Enable Integrations Safely

**2026-09-12 추가 완료 조건:**

- [ ] V25는 예약 번호 예시다. 실제 미사용 번호를 선택하고, 구버전 종료 및 모든 상태 사용처 전환이 끝난 배포에서만 제거 migration을 포함한다. feature flag OFF는 Flyway DDL 적용을 막지 않는다.
- [ ] Q1~Q3 중 해당 배포에 영향을 주는 결정과 미확인 행 처리를 기록한다. 원본 입력, 검토자, 검토 결과를 남기며 이름/사진만 보고 주문번호를 추정하지 않는다.
- [ ] 정원·재신청·입금 만료·사진 보관·고객 알림과 관리자 전체 흐름을 MariaDB 전환 전후에 비교한다. 예전 테스트 통과 기록으로 새 migration을 검증했다고 주장하지 않는다.

**Files:**

- Create: `Pawever-back/src/main/resources/db/migration/V25__remove_legacy_goods_status.sql`
- Create: `Pawever-back/src/test/java/com/pawever/backend/migration/LegacyStatusRemovalTest.java`
- Modify: deployment environment configuration outside Git for real secrets
- Update: implementation checklist in this plan

- [ ] Run an anonymized migration dry run against a recent production snapshot.
- [ ] Export unresolved legacy orders and reconcile them manually; do not enable production actions for unresolved rows.
- [ ] Have the Notion GPT execute `docs/operations/2026-09-11-notion-gpt-change-request.md` and return data source/property IDs.
- [ ] Share all four original Notion data sources with the connection; linked views alone are insufficient.
- [ ] Store secrets in AWS secret management and deploy with all new feature flags OFF.
- [ ] Verify account invitations for the five named team members using real emails.
- [ ] Smoke-test each role and confirm forbidden PII fields are absent from network responses.
- [ ] Enable the new server lifecycle and frontend work queue.
- [ ] Remove old coarse-status write endpoints and the legacy enum only after `rg` and tests prove no caller remains.
- [ ] Run the `V25` removal test and full backend suite.
- [ ] Import one real postal sample in preview-only mode; approve parser mapping before enabling apply.
- [ ] Configure the approved production-start AlimTalk code, send to a test recipient, and verify exact variables before enabling.
- [ ] Enable Notion sync for a two-order pilot, then all new orders.
- [ ] Compare Admin and Notion daily for seven days. Resolve every error/conflict and confirm no duplicate notification.
- [ ] Switch Notion operating mode from `TRANSITION` to `ADMIN_CANONICAL`.
- [ ] Enable SLA only when the owner chooses; default remains OFF.
- [ ] Enable compensation only after a production assistant starts; default remains OFF.
- [ ] Run final verification:

```bash
./gradlew test
pnpm test
pnpm check
pnpm build
pnpm test:e2e
```

- [ ] Review `git diff --check` in each repository.
- [ ] Commit backend removal with `refactor: remove legacy order status path` and the final frontend cleanup separately.

## Final Acceptance Checklist

- [ ] One admin work queue covers every active order exactly once per view rule.
- [ ] Every worker lands on their own actionable tasks.
- [ ] Normal work requires no detail-panel round trip.
- [ ] Payment, production, shipment, issue, and order termination are separate states.
- [ ] Manual payment confirmation is the active goods payment path.
- [ ] Server permissions control every sensitive field and action.
- [ ] The owner can toggle each person’s exposure and action permission.
- [ ] Current print/finish default is Park Na-hye; future default changes affect only new work.
- [ ] Postal XLSX is headerless, six-column, and preserves text postal codes and phones.
- [ ] Postal result imports are content-matched, partially applicable, and idempotent.
- [ ] Pickup requires Sangsang Hall or Da Vinci Hall and is processed by location.
- [ ] Rollback warns about prior customer notifications.
- [ ] Trash is owner-only and recoverable for 30 days.
- [ ] Customer notifications are outbox-driven, approved-template-only, and idempotent.
- [ ] Notion contains no guardian PII and cannot directly mutate canonical payment, shipping, deletion, or notification state.
- [ ] Notion webhook signature, retry, ordering, and loop prevention tests pass.
- [ ] All feature flags remain OFF until their external sample or approval gate is satisfied.
- [ ] Backend and frontend full suites pass from clean checkouts.

## Plan Self-Review

아래는 원 작성자의 자체 검토 기록이다. 이 정리본의 구현 완료나 2026-09-12 이후 변경의 검증 결과가 아니다.

- No unfinished marker, placeholder branch, placeholder file path, or invented external API contract remains.
- The only angle-bracket values are secret placeholders in documentation and are never committed as values.
- Current repository paths and commands were checked against the stated main commits.
- Each domain decision in the final handoff has a task and acceptance check.
- External dependencies are explicit: real postal sample, approved production-start template, staff emails, Notion IDs/secrets, and holiday source.
- The plan removes duplicated client state rules and avoids a long-lived compatibility layer.
