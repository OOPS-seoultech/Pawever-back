# 굿즈 랜딩·내부 설문 운영 가이드

## 데이터 흐름

1. `POST /api/public/goods-survey/responses`가 익명 응답 ID와 편집 토큰을 발급합니다.
2. 설문 진행 중 `PATCH /responses/{id}`로 답변, 문항별 활성시간, UTM과 기기 범주를 자동 저장합니다.
3. `POST /responses/{id}/complete`가 캠페인 행을 잠근 상태에서 실제 잔여 수량을 확인하고 15분 예약을 만듭니다.
4. 예약된 응답만 사연, 비공개 사진, 제작·배송 정보를 제출할 수 있습니다. 최종 제출은 `Idempotency-Key`로 중복 처리되지 않습니다.

설문 답변, 사연, 제작·배송 개인정보, 사진 메타데이터는 서로 다른 테이블에 저장합니다. 연락처와 주소는 AES-GCM으로 암호화하고 연락처 중복 확인에는 캠페인별 HMAC blind index만 사용합니다.

## 필수 환경 설정

- `GOODS_SURVEY_S3_BUCKET`: 반려견 제작 사진 전용 AWS S3 비공개 버킷
- 운영 S3 클라이언트는 서울 리전(`ap-northeast-2`)과 기존 AWS IAM 자격증명을 사용합니다.
- 운영용 `ENCRYPTION_SECRET_KEY`, `ENCRYPTION_HASH_KEY`

비공개 S3 버킷에는 `https://www.pawever.kr`의 `PUT` 요청을 허용하는 CORS 규칙이 필요합니다. S3 Block Public Access는 모두 유지하고 CloudFront에는 연결하지 않습니다. 브라우저는 10분짜리 presigned URL로 직접 업로드하고 API는 `HEAD`로 콘텐츠 형식과 용량을 확인합니다.

## 운영 전 확인

- `V2__goods_survey.sql` 적용 후 `goods-2026-07` 캠페인의 기존 확정 인원 `27`, 정원 `100`, 기간을 실제 운영값과 대조합니다.
- 배송 완료 시 `delivery_completed_at`을 기록하고 `delete_after`를 완료일+90일로 설정하는 배송 운영 연동이 필요합니다.
- Meta CAPI를 사용할 때는 신청 레코드의 `conversion_event_id`를 서버 이벤트 ID로 사용하고, 전송 토큰은 백엔드 환경변수에만 둡니다.
