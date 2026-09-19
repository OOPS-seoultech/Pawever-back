# 제작비 지급 원장

제작 정산은 실제 발송 접수 또는 직접 수령 포장 완료 때 주문당 한 번 적립된다. V26은
이 적립 기록을 소급 변경하지 않고 모든 기존 항목을 `UNPAID`로 시작하게 하며, 실제
은행 이체는 별도 지급 묶음으로 남긴다.

- `GET /api/admin/compensation/summary`: OWNER는 전체, 실무자는 본인 항목만 본다.
- `POST /api/admin/compensation/payout-batches`: OWNER가 한 담당자의 미지급 항목을 1~100건
  고정해 세전·공제·실지급액을 `PREPARED`로 남긴다. 같은 항목의 중복 선택은 차단한다.
- `POST /api/admin/compensation/payout-batches/{id}/mark-paid`: 실제 은행 이체 뒤 확인 번호나
  증빙 메모를 남겨 `PAID`로 기록한다. 자동 송금은 하지 않으며 완료된 묶음은 다시 처리할 수 없다.

`production_payout_items.settlement_id`의 유일 제약과 정산 행 잠금으로 동시에 두 묶음에 같은
항목을 넣지 못하게 한다. 지급 준비와 이체 완료는 workflow audit에도 남는다. 부분 지급은
미지급 항목 일부만 선택해 별도 묶음으로 처리한다.
