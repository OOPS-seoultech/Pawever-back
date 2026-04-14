# 긴급 대처 모드 API

## 개요

반려동물이 위급한 상황일 때 긴급 대처 모드를 활성화하고, 안치 준비 7단계와 장례업체 완료 여부를 저장·복원하는 API입니다.

---

## 준비물 번호 매핑 (안치 준비 2단계)

미리 살펴보기에서 안치 준비 2단계에 체크한 준비물이 긴급 대처 모드 진행 상태에 함께 반환됩니다.

| 번호 | 준비물 |
|---|---|
| 1 | 담요 / 이불 |
| 2 | 물티슈 / 거즈 |
| 3 | 배변패드 / 깨끗한 천 |
| 4 | 아이스팩 |
| 5 | 목받침용수건 |
| 6 | 위생장갑 |

체크하지 않은 경우 `restingStep2CheckedItemNumbers`는 빈 배열(`[]`)로 반환됩니다.

---

## 엔드포인트 목록

| 메서드 | 경로 | 설명 | 초기화 항목 |
|---|---|---|---|
| `POST` | `/api/pets/{petId}/emergency` | 긴급 대처 모드 활성화 | — |
| `GET` | `/api/pets/{petId}/emergency-progress` | 진행 상태 조회 | — |
| `PUT` | `/api/pets/{petId}/emergency-progress` | 진행 상태 저장 | — |
| `POST` | `/api/pets/{petId}/emergency/complete` | 긴급 대처 모드 완료 | 장례업체 저장/피하기, 미리 살펴보기 |
| `POST` | `/api/pets/{petId}/emergency/deactivate` | 긴급 대처 모드 해제 (이별 전 복귀) | 긴급 대처 진행 상태만 |

---

## 긴급 대처 모드 활성화

반려동물을 `AFTER_FAREWELL` 상태로 전환합니다. 이미 `AFTER_FAREWELL`이면 에러를 반환합니다.

```
POST /api/pets/{petId}/emergency
Authorization: Bearer {token}
```

**Response**

```json
{
  "code": "OK",
  "data": {
    "memorial": {
      "petId": 1,
      "name": "뽀삐",
      "profileImageUrl": "https://...",
      "deathDate": "2025-04-14T10:00:00"
    }
  }
}
```

---

## 긴급 대처 모드 진행 상태 조회

```
GET /api/pets/{petId}/emergency-progress
Authorization: Bearer {token}
```

긴급 대처 모드가 비활성 상태(`emergencyMode = false`)인 경우 에러를 반환합니다.

**Response**

```json
{
  "code": "OK",
  "data": {
    "lifecycleStatus": "AFTER_FAREWELL",
    "emergencyMode": true,
    "restingActiveStepNumber": 2,
    "restingCompletedStepCount": 1,
    "restingTotalStepCount": 7,
    "restingStep2CheckedItemNumbers": [1, 3, 4],
    "funeralCompanyCompleted": false,
    "updatedAt": "2025-04-14T12:00:00"
  }
}
```

---

## 긴급 대처 모드 진행 상태 저장

```
PUT /api/pets/{petId}/emergency-progress
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body** — 변경하려는 필드만 보내도 됩니다. 생략한 필드는 기존 값이 유지됩니다.

```json
{
  "restingActiveStepNumber": 3,
  "restingCompletedStepCount": 2,
  "funeralCompanyCompleted": false
}
```

**Response** — 조회 응답과 동일한 구조

---

## 긴급 대처 모드 완료

이별 후 상태를 확정하고 긴급 대처 모드를 종료합니다.
장례업체 저장/피하기와 미리 살펴보기 진행 상태가 **초기화**됩니다.

```
POST /api/pets/{petId}/emergency/complete
Authorization: Bearer {token}
```

**Response** — PetResponse (펫 최신 상태)

```json
{
  "code": "OK",
  "data": {
    "petId": 1,
    "name": "뽀삐",
    "lifecycleStatus": "AFTER_FAREWELL",
    "emergencyMode": false
  }
}
```

---

## 긴급 대처 모드 해제 (이별 전 복귀)

반려동물을 `BEFORE_FAREWELL`로 되돌리고 긴급 대처 진행 상태를 초기화합니다.
장례업체 저장/피하기와 미리 살펴보기 진행 상태는 **유지**됩니다.

```
POST /api/pets/{petId}/emergency/deactivate
Authorization: Bearer {token}
```

**Response** — PetResponse (펫 최신 상태)

---

## 필드 설명

### EmergencyProgressResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `lifecycleStatus` | string | `BEFORE_FAREWELL` / `AFTER_FAREWELL` |
| `emergencyMode` | boolean | 긴급 대처 모드 활성 여부 |
| `restingActiveStepNumber` | int | 현재 진행 중인 안치 준비 단계 (1~7) |
| `restingCompletedStepCount` | int | 완료한 안치 준비 단계 수 (0~7) |
| `restingTotalStepCount` | int | 안치 준비 전체 단계 수 (항상 7) |
| `restingStep2CheckedItemNumbers` | int[] | 미리 살펴보기 안치준비 2단계에서 체크한 준비물 번호 목록 (1~6) |
| `funeralCompanyCompleted` | boolean | 장례업체 선택 완료 여부 |
| `updatedAt` | datetime | 마지막 저장 시각 |

### EmergencyProgressUpdateRequest

| 필드 | 타입 | 설명 |
|---|---|---|
| `restingActiveStepNumber` | int? | 현재 진행 중인 안치 준비 단계 (1~7), 생략 시 기존 값 유지 |
| `restingCompletedStepCount` | int? | 완료한 안치 준비 단계 수 (0~7), 생략 시 기존 값 유지 |
| `funeralCompanyCompleted` | boolean? | 장례업체 선택 완료 여부, 생략 시 기존 값 유지 |

---

## 주의사항

- `restingStep2CheckedItemNumbers`는 **미리 살펴보기 진행 상태에서 읽어옵니다.** 긴급 대처 모드 API로는 변경할 수 없습니다. 변경은 미리 살펴보기 PUT API를 사용하세요.
- `restingActiveStepNumber`는 서버에서 `1 ≤ n ≤ 7` 범위로 클램핑됩니다.
- `restingCompletedStepCount`는 서버에서 `0 ≤ n ≤ 7` 범위로 클램핑됩니다.
- 긴급 대처 모드가 비활성 상태에서 진행 상태 조회/저장을 시도하면 에러가 반환됩니다.
- **complete** 후에는 `farewellPreviewProgress`가 삭제되므로 이후 `restingStep2CheckedItemNumbers`는 `[]`로 반환됩니다.
