# 개발 보완서 — 기존 주문을 유지하면서 운영 화면 확장하기

작성일: 2026-09-12. 상태: 기술 보완 반영, 운영 질문 Q1~Q3 미답변.
이 문서는 다운로드 이관서 4종을 현재 코드에 맞게 정리한 개발 지침이다. 새로운 운영 정책을 승인받았다고 표시하지 않는다.

## 1. 적용 범위와 현재 기준

대상은 Pawever-back과 Pawever-landing이다. 원문은 다운로드 폴더에 보존했고, 개발용 사본은 이 저장소에 모았다. 프론트 저장소는 사본을 복제하지 않고 이 문서를 참조한다. [읽는 순서](README.md)를 따른다.

기술 오류·누락은 이 보완서대로 고친다. 기존 정책은 운영 책임자의 최신 결정과 원본 운영 명세를 따른다. 서로 다른 운영 해석이 가능한 Q1~Q3는 문서 우선순위만으로 임의 확정하지 않는다. 아직 답변되지 않은 부분은 해당 운영 반영만 미룬다. 독립적인 테스트·인증·권한·명령 기반 작업은 진행한다.

**코드 기준.** 2026-09-12 최초 대조 시 백엔드 HEAD는 `0d904f9bb8c9c7fcf5b345c0262b6561da2eff3f`, 랜딩 HEAD는 `e99eade7ae7a1c4a4d4a439665b3c532b21b2916`이었다. 문서 정리 완료 직전 백엔드는 별도 작업의 PR #86 병합으로 `c9af267`이 됐고 랜딩은 동일하다. 최신 migration은 V16이다. 구현 시작 시 HEAD와 미커밋 변경을 다시 확인한다.

**입금 기한 변경을 반영한다.** 이전 검토 당시 커밋의 YAML 기본값은 온라인 2,880분/플리마켓 1,440분이었다. 이번 문서 정리 시작 시 두 기본값을 10,080분(7일)으로 바꾸는 별도 코드·테스트 수정이 있었고, 완료 점검 시 PR #86으로 로컬 main에 반영됐음을 확인했다. 이 문서 작업에서 만든 코드 변경이 아니며 운영 배포 여부는 확인하지 않았다. 이전 숫자로 되돌리지 않는다. 구현 시점의 합쳐진 코드와 설정으로 새 주문의 기한을 정하고, 기존 주문의 저장된 paymentExpiresAt은 별도 근거 없이 일괄 연장·단축하지 않는다.

**검증 기록.** 이전 코드 대조에서 관련 백엔드 155개, 프론트 390개 테스트가 통과했다. 이 수치를 별도 7일 변경이나 향후 구현의 테스트 결과로 재사용하지 않는다. 이번 문서 변경의 검증은 원문 대비 차이, 문서 참조, 필수 보완 항목과 코드 변경 비간섭 확인이다.

## 2. 개발자가 반영할 규칙

**기존 접수·판매 보존.** 주문 생성 시 확정된 상품 금액, 할인, 배송비, 키링 여부/금액과 입금 기한을 보존한다. 과거 주문을 현재 가격표로 재계산하지 않는다. 현장 가격 기본값은 18,900원, 키링 추가 기본값은 2,000원이며 환경변수와 주문 스냅샷을 우선한다. PICKUP은 주소·송장을 요구하지 않고 배송비를 붙이지 않는 기존 동작을 유지한다. 신규 허용 채널 확장은 Q1 답변 전 적용하지 않는다.

정원은 캠페인별 제출·주문 상태로 계산한다. 완료된 주문도 점유하며 PAYMENT_PENDING과 CANCEL_FAILED도 계속 점유한다. 기존 PAYMENT_EXPIRED/PAYMENT_FAILED/CANCELED의 자리 반환과 온라인 재신청 허용을 보존한다. 온라인의 같은 캠페인·전화번호 살아 있는 주문 제한, FLEA의 같은 번호 복수 주문 허용을 유지한다. SUBMITTED지만 주문이 없는 기록의 보수적 점유도 유지한다. 새 모델의 환불 대기와 휴지통 이동만으로 자리를 풀지 않는다. 취소 확정/환불 확인의 근거를 보존하고 기존 반환 조건과 같은 결과가 나오는지 테스트한다.

모집 70은 FLEA 캠페인의 기존 생성값이다. 모든 캠페인의 정원이나 이미 변경된 운영 값을 70으로 덮지 않는다. 캠페인 제출 잠금, 모집 스위치와 채널 구분을 유지한다. 상태 전환 대상에는 관리자 코드뿐 아니라 GoodsSurveyService, GoodsOrderService, GoodsSurveyRetentionService, 관련 repository, payment 및 내부 운영 API를 포함한다.

**공통 명령·감사 기반을 먼저 만든다.** 주문/계정 등 변경 명령은 권한 검사, 현재 version, 멱등 키와 요청 본문 동일성, 감사 이벤트를 함께 처리한다. 같은 키의 같은 요청은 같은 결과를 재사용하고, 다른 본문 재사용은 충돌로 처리하며, 동시 중복 요청이 두 작업을 만들지 않게 한다. 응답 재사용 전에도 현재 호출자의 권한을 검사한다. 멱등 저장 범위·키 수명·실패/진행 중 응답을 테스트 계약으로 정한 뒤 구현한다. 도메인 변경과 outbox 저장은 같은 트랜잭션에, 외부 발송은 그 밖에 둔다. 공통 기반은 Task 4/5가 사용하기 전에 준비하며 notification 고유 키만으로 대신하지 않는다.

**인증 경로를 함께 확장한다.** SecurityConfig와 AdminAuthenticationFilter를 수정 대상에 포함한다. /api/admin/** 및 /api/production/**는 활성 staff 인증과 업무 권한을 요구하고 앱 회원 토큰을 인정하지 않는다. OWNER/MARKETING/SUPPORT 역할을 경로에서 일괄 거절하지 않되, 허용 행·필드·행동은 역할별로 제한한다. 두 staff 경로의 CORS에는 사용하는 PATCH 메서드와 Idempotency-Key 헤더를 포함한다. 기존 공개 접수·앱 인증과의 경계도 회귀 검증한다.

Notion webhook만 일반 사용자 로그인 요구와 분리한다. 초기 검증 토큰 수신과 운영 이벤트의 raw body 서명 검증 절차를 구분하며, 검증되지 않은 이벤트는 업무 큐에 넣지 않는다. 로그인 요구를 제거하는 것만으로 webhook 구현을 끝내지 않는다.

**권한 이름을 한 곳에서 관리한다.** [원본 명세 §5.4](../superpowers/specs/2026-09-10-pawever-admin-operations-ux-design.md)의 permission 목록을 기본으로 한다. 최종 이관서에서 중복되던 이름은 아래처럼 통일한다.

`VIEW_ORDER_CORE → VIEW_ORDER_BASIC`, `VIEW_GUARDIAN_NAME → VIEW_CUSTOMER_IDENTITY`, `VIEW_PHONE → VIEW_CUSTOMER_CONTACT`, `VIEW_ADDRESS → VIEW_CUSTOMER_ADDRESS`, `VIEW_PET_PHOTOS → VIEW_CUSTOMER_PHOTOS`, `DOWNLOAD_PET_PHOTOS → DOWNLOAD_CUSTOMER_PHOTOS`, `VIEW_MARKETING_EXPORT → EXPORT_MARKETING_CONTACTS`.

`ASSIGN_TASK → ASSIGN_WORK`, `EXPORT_POSTAL_FILE → PACK_AND_EXPORT_SHIPMENTS`, `IMPORT_POSTAL_RESULT → IMPORT_SHIPMENT_RESULTS`, `ROLLBACK_ORDER → OVERRIDE_WORKFLOW`, `TRASH_ORDER/RESTORE_ORDER → MANAGE_TRASH`, `PURGE_ORDER → PERMANENT_PURGE`, `MANAGE_STAFF → MANAGE_ACCOUNTS`, `MANAGE_PERMISSIONS → MANAGE_ACCOUNT_PERMISSIONS`, `MANAGE_SETTINGS → MANAGE_OPERATION_SETTINGS`. 파일 재다운로드에도 권한을 다시 검사하며 이름 통일을 이유로 상태를 다시 변경하지 않는다.

원본에 동일한 의미의 키가 없는 `VIEW_PET_NAME`, `VIEW_MARKETING_CONSENT`, `VIEW_TECHNICAL_ERRORS`, `EDIT_ORDER`, `CHANGE_PRODUCTION_STAGE`, `MANAGE_FILAMENT`, `COMPLETE_QC`, `PACK_ORDER`, `MANAGE_NOTION_SYNC`는 이관서에 이미 있던 추가 요구로 남긴다. 각 키의 필드·정상 행동·역할 기본값을 하나의 서버 목록에 명시한다. 이름을 등록했다고 모든 제작자에게 자동 부여하지 않는다. 특히 CHANGE_PRODUCTION_STAGE는 강제 롤백 권한으로 대체하지 않으며, 담당 작업과 단계별 완료 권한 검증을 우회하는 포괄 권한으로 사용하지 않는다.

VIEW_ROLE_QUEUE/VIEW_ALL_ORDERS의 행 범위, 필드 노출, 행동의 필수 데이터 권한을 독립적으로 검사한다. OWNER 기본값에도 자기 권한 변경 금지와 마지막 활성 OWNER 보호를 적용한다. 권한 변경·계정 정지 후 다음 요청은 기존 JWT의 오래된 role을 신뢰하지 않는다. 세션 무효화와 실제 계정 상태/권한 재조회도 구현한다. 휴지통 역할은 Q3 답변을 따로 기록한다.

**기존 상태 이전.** 원본과 이관서의 매핑표는 서로 다르므로 자동 실행용 SQL로 복사하지 않는다. dry run에서 기존 상태·유료/무료 근거·배송/환불 근거·새 값·확인 사유를 주문별로 출력한다. 확정 가능한 PAYMENT_PENDING, PAYMENT_COMPLETED, LEGACY_FREE, PAYMENT_EXPIRED, PAYMENT_FAILED는 원본의 네 상태 축을 모두 정의해 테스트한다. 무료 주문을 CONFIRMED로 바꾸지 않는다.

IN_PRODUCTION은 실제 제작 자료로 확인한 단계만 배정한다. 확인 불가 행을 BLOCKED와 확인 이슈로 두자는 제안은 Q2 답변 대기다. SHIPPED는 발송 사실만으로 DELIVERED/COMPLETED라고 추정하지 않고 기본 매핑을 ACTIVE/COMPLETE/ACCEPTED로 유지하되 결제 근거와 송장 데이터를 검증한다. 실제 배달 완료 증거가 있으면 별도 확인 후 반영한다. PICKED_UP은 수령 완료 사실을 유지하되 장소는 Q1을 따른다. CANCELED의 환불 완료/결제 없음, CANCEL_FAILED의 환불 대기 여부는 저장된 근거와 대조하며 알 수 없는 값은 보고한다. 자료 부족 주문에 고객 알림이나 새 제작 작업을 자동 생성하지 않는다.

**마이그레이션 적용.** [저장소 Flyway 규칙](../DB_MIGRATIONS.md)에 따라 적용된 SQL은 수정하지 않는다. Task 5의 payment DDL은 V17 최초 적용 전에 완성하거나 새 미사용 버전으로 만든다. 그에 따라 이후 예시 번호를 조정한다. V25 제거도 예약된 절대 번호가 아니다. 이전 코드와 모든 상태 사용처의 종료가 확인된 배포에서만 제거 migration을 포함한다. feature flag는 이미 실행되는 Flyway DDL을 막지 않는다. Task 2 backfill이 Task 4에서 뒤늦게 생기는 이슈 테이블을 먼저 참조하지 않도록 의존 순서를 정한다.

H2/create-drop 기본 테스트는 Flyway를 검증하지 않는다. 격리된 MariaDB에서 V16 데이터부터 새 migration 적용과 결과를 검증한다. 익명화 운영 스냅샷 dry run과 기존 접수·만료·보관 회귀도 실행한다. 테스트 준비를 이유로 사용 중인 DB나 볼륨을 삭제하지 않는다. 새 휴지통은 기존 사진 파기·거래 기록 분리 보관과 연결한다.

**Notion 요청 처리.** 내부 계정과 Notion 사용자 ID의 명시적 연결, 실제 명령 요청자 귀속, 명령 식별 기준과 기대 version, 재전송 중복 방지 및 소비 절차를 구현 전에 문서/테스트로 고정한다. 담당자 ID만으로 요청자를 판단하지 않는다. 미연결·비활성 사용자와 귀속 불명확·복수 편집·봇 편집은 임의 사용자 권한으로 실행하지 않는다. 최신 페이지를 다시 읽는 동안 새로운 요청이 생기는 경우까지 고려하며, 이전 처리 결과 쓰기/요청 초기화가 새 요청을 지우지 않는 절차를 검증한다. 이 계약이 완성되기 전 명령 실행을 켜지 않는다. 이는 외부 ID 부재로 내부 개발 전체를 미루라는 뜻이 아니다.

보기 생성·수정 API는 존재한다. 필요한 설정과 연결 권한을 확인한 뒤 자동화 범위를 정하고 불가능한 항목만 수동으로 남긴다. 요청 큐는 연결/워크스페이스 제한과 429/529의 Retry-After를 처리한다. 근거: [보기 생성](https://developers.notion.com/reference/create-view), [보기 수정](https://developers.notion.com/reference/update-a-view), [요청 제한](https://developers.notion.com/reference/request-limits), [webhook 이벤트](https://developers.notion.com/reference/webhooks-events-delivery).

**진행 순서.** Task 1의 기준선·접수 회귀와 공통 명령 기반 → Task 3의 인증·권한 → Task 2의 migration dry run과 Task 4/5의 상태·작업·입금 계약 연결 순서로 의존성을 맞춘다. Task 번호가 운영 배포 횟수는 아니다. 운영 전환은 필요한 스키마·명령·이슈·화면이 함께 준비된 뒤 실시한다. Task 7의 감사 항목 중 공통 기반에 필요한 부분은 앞당기고, 실제 롤백/휴지통 UX는 원래 단계에서 연결한다. 커밋 예시의 광범위한 git add 대신 이번 작업 파일만 명시해 기존 사용자 변경이 섞이지 않게 한다.

## 3. 종무님 확인 사항과 준비 자료

**Q1 — 직접 수령.** 기존 직접 수령 주문은 어디에서 받기로 했는가? 주문별로 상상관/다빈치관/기타/아직 미정인 근거를 확인해야 한다. 앞으로 건물 수령을 플리마켓 주문에만 열지, 온라인 주문에도 열지 답변이 필요하다. 현재 채널 제한을 먼저 유지하며, 기존 장소를 자동 채우지 않는다. 담당: 이종무. 상태: 미답변.

**Q2 — 제작 중인 주문.** 현재 ‘제작 중’이지만 실제 단계를 확인할 수 없는 주문은 담당자가 확인할 때까지 새 시스템의 자동 배정·다음 단계 처리를 멈추는 방식으로 진행할지 확인한다. 확인 담당자는 이종무와 제작 담당자가 정한다. 이는 현장에서 진행 중인 물리 작업을 중단하라는 뜻이 아니다. 담당: 이종무 및 제작 담당자. 상태: 미답변.

**Q3 — 삭제 복구.** 삭제한 주문을 복구하는 권한을 이종무(OWNER)만 가질지, 권한을 받은 다른 관리자(ADMIN)도 가질지 확인한다. 두 이관 문서의 내용이 다르다. 새 복구 기능에 대한 권한 부여만 답변을 기다리고, 일반 계정·권한 기반은 진행한다. 담당: 이종무. 상태: 미답변.

외부 자료는 필요한 기능의 실제 연결 전에 받는다. 우체국 결과 파일은 파서 확정/반영 활성화 전에, 승인 템플릿은 제작 시작 알림 실제 발송 전에, 실제 이메일/발신 설정은 운영 초대 전에, Notion ID·공유·비밀 설정은 실연동 전에 필요하다. 공휴일 기준과 제작 보조 자료는 각각 SLA/정산을 켜기 전에 확정한다. 미수령 팀 역할 playbook은 원문을 요청하며 내용을 임의로 작성해 수령한 것처럼 표시하지 않는다.

답변은 Q 번호, 답변 내용, 답변자, 날짜, 근거를 이 문서에 추가한다. 이 정리본 작성 자체를 질문의 승인으로 간주하지 않는다. 대외 메시지는 작성하거나 전송하지 않았다.
