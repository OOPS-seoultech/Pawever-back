# 실제 필라멘트 등록과 부위별 지정

완료 경계는 검수 승인 후 COLOR_MAPPING → 부위별 실제 스풀 선택·저장 → 지정 완료 → 같은 DESIGN_QC 담당자의 PLATE_PREPARATION이다. 다음 단계인 PrintBatch 구성·슬롯 매핑·출력 파일 등록·프린터 실행은 후속 개발 범위다.

## 요구사항 출처

- [최종 이관서 §9.3](2026-09-11-pawever-developer-final-handoff.md): 부위별 색상과 실제 filament ID를 연결하며 단순 색상명만으로 완료하지 않는다.
- [운영 명세 §5.4·7·8.3·17.3](../superpowers/specs/2026-09-10-pawever-admin-operations-ux-design.md): VIEW_FILAMENT/MAP_FILAMENT, COLOR_MAPPING 완료 경계, 사람이 결정하는 부위별 매핑, MariaDB 원본 재고.
- [개발 보완서](2026-09-12-development-addendum.md): 이관서의 MANAGE_FILAMENT를 별도 권한으로 관리한다.

사용자가 확인한 원본은 Downloads/성욱님_개발자_최종이관_2026-09-11/2026-09-11-pawever-developer-final-handoff.md §9.3(304–310행), 같은 폴더의 운영 명세 §8.3(442행부터)다. 위 링크는 저장소에 보관한 사본이다.

## 이번 구현의 규칙

스풀 ID는 실물 한 개의 고유 식별자이며 영문 대문자·숫자·점·밑줄·하이픈 1–64자로 정규화한다. 등록 후 ID는 바꾸거나 삭제하지 않으며 사용 가능 여부로 관리한다. 색상명·재질·마감·잔량(g)은 필수, 제조사·구입처·가격은 선택 입력이다. 가격은 0–100,000,000원, 잔량은 0–1,000,000g의 정수다. 재고 수량은 수동 기록이며 색상 지정 시 예약·차감하지 않는다. 선택 가능 여부는 사용 가능 설정으로 판단한다.

고정 부위 목록이 원문에 없으므로 작업자가 1–64개의 부위 이름(60자 이내)을 입력한다. 전체 출력 부위를 입력하도록 안내하고, 부위별로 실제 등록된 사용 가능 스풀 ID를 요구한다. 부위 이름은 Unicode NFKC·공백·대소문자를 정규화해 중복을 거절한다. 여러 부위에 같은 스풀을 사용할 수 있다.

OWNER/ADMIN은 조회·등록·수정 권한을, DESIGN_QC는 조회·배정된 주문의 매핑 권한을, PRINT_FINISHING은 조회 권한을 기본으로 가진다. 그 외 제작자에게 재고 수정 권한을 자동 부여하지 않는다. 개인 DENY가 우선하며 주문 기본 정보·고객 사진·제작 파일·필라멘트 조회 권한 없이는 매핑할 수 없다. 완료 이후 변경은 이 API로 허용하지 않는다.

카탈로그 변경과 매핑 명령은 기존 계정 잠금·주문 잠금·version·멱등 키 체계를 공유한다. 재시도는 같은 결과를 반환하고 동시 완료는 다음 작업을 하나만 만든다. 매핑 저장마다 선택한 스풀 ID·색상·재질·마감을 복사한다. 완료 기록은 이후 카탈로그 수정·사용 중지에도 유지하며, 변경 전후와 실행자는 감사 이력에 기록한다. 완료된 회차는 덮어쓰지 않는다.

## API와 화면

- `GET /api/admin/filaments`, `POST /api/admin/filaments`, `POST /api/admin/filaments/{id}`: 조회·등록·수정. 모든 변경은 Idempotency-Key, 수정은 version 필수.
- `POST /api/production/tasks/{taskId}/filament-mappings`: `{version, complete, mappings:[{partName,filamentId}]}`. complete=false는 저장, true는 현재 내용을 확정하며 PLATE_PREPARATION 생성.
- `/admin/filaments`에서 재고를 관리하고 `/admin/my-work`의 주문 상세에서 부위를 지정한다. 저장한 내용은 재열람 가능하고, 사용 중지된 선택은 다시 선택하도록 표시한다. 네트워크 재시도 시 같은 멱등 키를 유지한다.

## 검증과 배포 진행

- [x] 미구현 API 4개 실패 및 미구현 필라멘트 화면 실패 확인.
- [x] 카탈로그·부위별 저장·완료·권한·중복 요청과 화면 구현.
- [x] 최대 부위 수와 권한 회수, 실제 MariaDB 이전·재적용·동시 요청 확인.
- [ ] 전체 회귀 검사, DB 백업, 백엔드·프론트 운영 배포 및 읽기 점검.

실제 재고·고객 주문·팀원 역할은 테스트로 등록하거나 변경하지 않는다. 운영 배포 결과와 첫 사용 전 필요한 실제 재고 입력은 workspace의 배포 기록에 별도로 남긴다.

V19는 filaments/order_filament_mappings 테이블을 추가하고 workflow_audit_events의 before_value/after_value를 LONGTEXT로 확장한다. 64개 부위의 변경 기록이 기존 500자 제한으로 실패하는 것을 재현한 뒤 확장했다. 기존 감사 값과 주문·검수·작업·계정 배정은 보존한다.

배포 전 검사: 백엔드 전체 374개 중 373개 통과(실제 DB 환경 조건부 1개 제외), 실제 MariaDB에서 작업 API 21개와 V16→V19 이전·재적용 검사 1개 통과. 프론트 타입 검사·빌드, 단위 390개, E2E 86개 통과(기존 제외 1개). legacyTest는 NO-SOURCE다. 배포 후 상태는 workspace의 2026-09-12-filament-mapping-deployment.md에 기록한다.
