# 보유 기간과 자동 파기 (참고)

> 개인정보 처리방침에 고지한 기간을 코드가 지키게 하는 장치다.
> 기간을 바꾸려면 **방침 문구와 이 설정을 같이** 바꿔야 한다. 한쪽만 바꾸면
> 고지한 내용과 실제가 어긋난다.

## 파기 대상과 기준

| 대상                           | 기간         | 세는 기준                 | 설정                           |
| ------------------------------ | ------------ | ------------------------- | ------------------------------ |
| 제작용 사진, 상세주소          | 90일         | **배송 완료를 표시한 날** | `personal-data-retention-days` |
| 안내 이메일                    | 365일 / 즉시 | 동의한 날 / 수신거부 접수 | `notice-retention-days`        |
| 설문 응답·사연, 공개 동의 사진 | 730일 (2년)  | 수집한 날                 | `survey-retention-days`        |
| 계약·결제·공급 기록            | 1825일 (5년) | **주문한 날**             | `contract-retention-days`      |
| 결제하지 않은 주문의 사진      | 30분         | **주문한 날**             | `payment-window-minutes`       |

- 공개에 동의한 사진은 90일에 지우지 않는다. 제작이 아니라 공개가 목적이라
  설문과 같은 2년을 따르고, 그때 함께 지운다.
- **90일에 신청 정보를 통째로 지우지 않는다.** 유료 판매는 전자상거래법이
  대금 결제와 재화 공급 기록을 5년 보존하도록 한다. 90일에는 사진과 상세주소만
  지우고, 주문·결제와 어디로 보냈는지는 남긴다.
- 설문 2년 파기도 **법정 보존 기간이 남은 계약 기록은 건드리지 않는다.** 응답을
  지우면 그 기록이 어느 주문의 것인지 잃으므로 응답도 함께 남긴다. 5년이 지나면
  계약 기록 파기가 둘 다 정리한다.
- 결제하지 않은 채 30분이 지난 주문은 **사진까지 그 자리에서 지운다.** 계약이
  성립하지 않아 반려견 사진을 들고 있을 근거가 없다. 공개 동의를 받았더라도
  지운다 — 그 동의는 굿즈를 만드는 것을 전제로 받은 것이다. 다시 사려면 새
  주문으로 처음부터 신청한다.
- 계약 기록은 배송이 아니라 **주문 시점**부터 센다. 배송 표시를 놓친 건도 법정
  기간만큼 남아야 하고, 법도 거래 시점을 기준으로 삼는다.

## 실행

`GoodsSurveyRetentionScheduler` 가 **한국 시각 새벽 4시 15분**에 하루 한 번 돈다.

```yaml
survey:
  goods:
    purge-cron: ${GOODS_SURVEY_PURGE_CRON:0 15 4 * * *}
```

다섯 갈래를 따로 부른다. 한 갈래가 실패해도 나머지는 그날 처리되고, 실패한
갈래는 대상이 그대로 남아 다음 회차에 다시 잡힌다.

한 회차에 갈래마다 최대 200건을 처리한다. 밀린 물량이 많아도 며칠이면
따라잡는다.

## 배송 완료를 표시해야 90일이 시작된다

**표시하지 않으면 배송 정보가 계속 남는다.** 기간을 셀 기준일이 없기 때문이다.
발송을 마치면 건마다 아래를 호출한다.

```bash
curl -X POST \
  -H "X-Survey-Export-Token: $GOODS_SURVEY_EXPORT_TOKEN" \
  https://api.pawever.kr/api/internal/goods-survey/fulfillments/{responseId}/delivery-completed
```

응답의 `deleteAfter` 가 파기 예정일이다. 이미 표시한 건을 다시 불러도 날짜는
밀리지 않는다. 밀리면 고지한 기간보다 오래 갖고 있게 된다.

## 입금 확인

계좌 이체를 눈으로 확인하고 찍는다. 표시가 없으면 누가 냈는지 스프레드시트로
따로 관리하게 되고, 그 스프레드시트가 또 하나의 개인정보 보관처가 된다.

```bash
curl -X POST \
  -H "X-Survey-Export-Token: $GOODS_SURVEY_EXPORT_TOKEN" \
  https://api.pawever.kr/api/internal/goods-survey/fulfillments/{responseId}/paid
```

청구할 금액은 신청 정보의 `appliedPriceKrw` 에 들어 있다. 설문에 답하고 온
사람은 할인가, 건너뛰고 바로 신청한 사람은 정가다. 제출하면 둘 다 SUBMITTED 가
되어 나중에는 구분할 수 없으므로 제출 시점에 확정해 남긴다.

## 안내 이메일 수신거부

```bash
curl -X POST \
  -H "X-Survey-Export-Token: $GOODS_SURVEY_EXPORT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email":"someone@example.com"}' \
  https://api.pawever.kr/api/internal/goods-survey/notice-subscriptions/unsubscribe
```

표시만 남고 주소는 다음 파기 때 지워진다. 그 사이에 발송을 준비하는 쪽에서
거부한 주소를 걸러낼 수 있다. 등록되지 않은 주소도 같은 응답을 준다 —
어떤 주소가 등록돼 있는지 확인하는 통로로 쓰이면 안 된다.

## 지금 한 번 돌리기

정기 작업을 기다리지 않고 확인할 때 쓴다. 도는 내용은 정기 작업과 같다.

```bash
curl -X POST \
  -H "X-Survey-Export-Token: $GOODS_SURVEY_EXPORT_TOKEN" \
  https://api.pawever.kr/api/internal/goods-survey/retention/purge
```

## 탈퇴 시 파기

`PetService.clearPetScopedState` 가 미션 행을 지우기 전에 **미션 사진과 음성
녹음 파일을 스토리지에서 먼저 지운다.** 행이 사라지면 어떤 파일이 누구
것이었는지 찾을 길이 없어, 지울 수 있는 마지막 시점이 거기다.

## 순서 — 파일 먼저, 행은 나중

파일을 지우고 나서 행을 지운다. 반대로 하면 지우다 실패했을 때 어느 파일을
지워야 하는지 알 방법이 없어져 저장소에 영영 남는다. 이 순서면 실패해도
같은 행이 다음 회차에 다시 잡혀 이어서 지운다. 저장소 삭제는 이미 없는 키를
지워도 성공으로 본다.
