# 미리 살펴보기 진행 상태 API

## 개요

미리 살펴보기의 진행 상태를 저장·복원하는 API입니다.
클라이언트가 **전체 상태 스냅샷**을 PUT으로 올리면 서버가 저장하고, GET으로 다시 내려받아 화면을 복원합니다.
`progressPercent`는 서버가 계산해서 내려주므로 클라이언트가 직접 계산할 필요 없습니다.

---

## 진척도 규칙

각 하위단계는 **독립적으로 누적**됩니다. 한 번 완료 처리된 단계는 취소되지 않습니다.

| 메인 단계 | 하위단계 | 트리거 조건 | 진척도 |
|---|---|---|---|
| **1. 이별 방법 선택** | 1 | '이 방식을 고려해요' 버튼 클릭 | +20% |
| **2. 안치 준비** | 1 | 1단계 '다음으로' 버튼 클릭 | +3% |
| | 2 | 2단계 '다음으로' 버튼 클릭 | +3% |
| | 3 | 3단계 '다음으로' 버튼 클릭 | +3% |
| | 4 | 4단계 '다음으로' 버튼 클릭 | +3% |
| | 5 | 5단계 '다음으로' 버튼 클릭 | +3% |
| | 6 | 6단계 페이지 열기 | +3% |
| | 7 | 6단계 '완료' 버튼 클릭 | +2% |
| **3. 행정처리** | 1 | 1단계 '완료 하기' 버튼 클릭 | +5% |
| | 2 | 2단계 '완료 하기' 버튼 클릭 | +7% |
| | 3 | 3단계 '완료 하기' 버튼 클릭 | +6% |
| | 4 | 4단계 '완료 하기' 버튼 클릭 | +7% |
| | 5 | 5단계 '완료 하기' 버튼 클릭 | +5% |
| **4. 물건정리** | 1 | 방법 하나 체크 후 '진행 완료' 버튼 클릭 | +10% |
| **5. 지원사업** | 1 | '서울시~' 토글 열고 확인 완료 | +5% |
| | 2 | '청년~' 토글 열고 확인 완료 | +5% |
| | 3 | '전국민~' 토글 열고 확인 완료 | +5% |
| | 4 | '서울심리~' 토글 열고 확인 완료 | +3% |
| | 5 | 위 토글 모두 연 뒤 '위 내용을 모두 확인했습니다' 버튼 클릭 | +2% |

**합계: 100%**

---

## 필드 → 하위단계 번호 매핑

| 필드 | 대상 | 유효 범위 |
|---|---|---|
| `completedMainSteps` | 이별방법(1), 물건정리(4) 완료 여부 | 1, 4 |
| `restingCompletedSubStepNumbers` | 안치준비 완료 하위단계 | 1 ~ 7 |
| `administrationCompletedSubStepNumbers` | 행정처리 완료 하위단계 | 1 ~ 5 |
| `belongingsSelectedOptionNumbers` | 물건정리 선택한 옵션 번호 | 1 ~ 4 |
| `supportCompletedSubStepNumbers` | 지원사업 완료 하위단계 | 1 ~ 5 |

> 이별방법(메인1) 완료 → `completedMainSteps: [1]`
> 물건정리(메인4) 완료 → `completedMainSteps: [1, 4]`
> 행정처리 4단계 완료 → `administrationCompletedSubStepNumbers: [..., 4]`

---

## 엔드포인트

### 진행 상태 조회

```
GET /api/pets/{petId}/farewell-preview-progress
Authorization: Bearer {token}
```

**Response**

```json
{
  "code": "OK",
  "data": {
    "lifecycleStatus": "BEFORE_FAREWELL",
    "progressPercent": 26,
    "hasCompletedGuide": true,
    "currentStep": 2,
    "enteredSteps": [1, 2],
    "completedMainSteps": [1],
    "restingCompletedSubStepNumbers": [1, 2],
    "administrationCompletedSubStepNumbers": [],
    "belongingsSelectedOptionNumbers": [],
    "supportCompletedSubStepNumbers": [],
    "isOwnerWritable": true,
    "updatedAt": "2025-04-14T12:00:00"
  }
}
```

---

### 진행 상태 저장 (오너 전용)

```
PUT /api/pets/{petId}/farewell-preview-progress
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body** — 변경된 필드만이 아닌 **전체 스냅샷**을 전송합니다.

```json
{
  "hasCompletedGuide": true,
  "currentStep": 3,
  "enteredSteps": [1, 2, 3],
  "completedMainSteps": [1],
  "restingCompletedSubStepNumbers": [1, 2, 3],
  "administrationCompletedSubStepNumbers": [1],
  "belongingsSelectedOptionNumbers": [],
  "supportCompletedSubStepNumbers": []
}
```

**Response** — 조회 응답과 동일한 구조 (서버가 계산한 `progressPercent` 포함)

---

## 필드 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `lifecycleStatus` | string | `BEFORE_FAREWELL` / `AFTER_FAREWELL` |
| `progressPercent` | int | 서버 계산 진척도 (0~100) |
| `hasCompletedGuide` | boolean | 초기 안내 완료 여부 |
| `currentStep` | int | 현재 보고 있는 메인 단계 (1~5) |
| `enteredSteps` | int[] | 진입한 메인 단계 번호 목록 |
| `completedMainSteps` | int[] | 완료된 메인 단계 번호 목록 |
| `restingCompletedSubStepNumbers` | int[] | 안치준비 완료 하위단계 번호 목록 |
| `administrationCompletedSubStepNumbers` | int[] | 행정처리 완료 하위단계 번호 목록 |
| `belongingsSelectedOptionNumbers` | int[] | 물건정리 선택 옵션 번호 목록 |
| `supportCompletedSubStepNumbers` | int[] | 지원사업 완료 하위단계 번호 목록 |
| `isOwnerWritable` | boolean | 오너 여부 (false면 PUT 불가) |
| `updatedAt` | datetime | 마지막 저장 시각 |

---

## 주의사항

- **PUT은 오너만** 가능합니다. 오너가 아닌 사용자가 호출하면 `403` 에러가 반환됩니다.
- 각 번호 목록은 **서버에서 중복 제거 및 유효 범위 필터링**이 적용됩니다.
- 하위단계 번호는 완료 순서와 무관하게 **집합으로 관리**됩니다. 이미 포함된 번호를 다시 보내도 중복 추가되지 않습니다.
- `AFTER_FAREWELL` 상태에서는 메인 단계 1, 2가 비활성화됩니다 (서버에서 자동 제거).
