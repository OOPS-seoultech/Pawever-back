# 관리자 판매가 및 매출

- 고객 신청 가격·기존 청구액·입금 기록·알림 발송은 변경하지 않는다.
- 관리용 주문 판매가는 기본 18,900원. `sale_price_krw`가 null이면 기본값을 사용한다.
- 주문 상세에서 ‘직접 입력’을 켜고 0~100,000,000원 정수 입력 후 저장. 끄고 저장하면 기본값으로 복원한다.
- 배송비를 제외한 주문 총 판매가이다. 여러 마리가 포함된 주문도 주문별 총액을 직접 수정한다.
- 수정 권한: VIEW_ORDER_BASIC + VIEW_ALL_ORDERS + VIEW_PAYMENT + MANAGE_OPERATION_SETTINGS.
- 저장은 기존 Idempotency-Key·주문 버전 잠금·감사 이력을 사용한다. 고객 알림/Telegram/입금 재확인 요청을 만들지 않는다.
- POST `/api/admin/orders/{number}/sale-price`: `{version,custom:true,salePriceKrw:24000}` 또는 `{version,custom:false}`.
- GET `/api/admin/workflow/sales-summary`: 누적·이번 달 합계, 유료 확인 주문 수, 최근 12개월 월별 합계.
- 입금 확인일을 Asia/Seoul로 변환해 월별 집계한다. 무료·미입금·취소·환불·환불 대기·휴지통 제외. 관리자 완료한 유료 주문은 포함.
- 실제 입금액 합계가 아닌 현재 관리용 판매가 기준 합계다. 판매가 수정 시 과거 해당 월 합계도 갱신한다.
- V29는 nullable INT 열 하나만 추가하며 기존 주문의 금액과 상태는 갱신하지 않는다.
