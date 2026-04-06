# 유저 탈퇴 및 반려동물 삭제 (참고)

## 동작 요약

- 탈퇴 유저가 **owner인 Pet**은 모두 삭제된다.
- 그 Pet을 **공유만 하던 유저**가 `selectedPetId`로 그 Pet을 가리키고 있으면, 이후 홈에서 선택 반려동물 조회 시 **삭제 안내**가 필요하다.

---

## 탈퇴 시 서버 처리 순서

| 순서 | 처리 내용 |
|------|-----------|
| 1 | 탈퇴 요청 유저가 **owner인 Pet 목록** 조회 |
| 2 | 각 Pet에 대해 **UserPet 삭제** → **Pet 삭제** (단일 Pet 삭제 API와 동일한 연쇄 규칙) |
| 3 | 유저 **soft delete** (`deletedAt` 설정) |

다른 유저의 `selectedPetId`는 **탈퇴 처리 시 자동으로 null이 되지 않는다**. 이후 `GET /api/pets/selected`에서 Pet 부재를 감지하면 **410 SELECTED_PET_DELETED**로 처리한다.

### 시나리오 예시

- **User A**: Pet 1의 owner. 탈퇴 요청.
- **User B**: Pet 1을 공유만 함(isOwner=false). 현재 **selectedPetId = 1**.

**탈퇴 후:**

1. Pet 1 삭제, Pet 1에 연결된 모든 UserPet 삭제.
2. User A는 `deletedAt` 설정으로 탈퇴 처리.
3. User B의 `selectedPetId`는 **의도적으로 1로 유지** (서버에서 null로 갱신하지 않음).

**User B가 이후 홈 접근 시:**

- `GET /api/pets/selected` 호출 시, `selectedPetId = 1`이지만 Pet 1·UserPet은 이미 삭제된 상태.
- 서버: User B의 **selectedPetId를 null로 저장**한 뒤 **410 SELECTED_PET_DELETED** 반환 (메시지: "이용 중이던 반려동물 프로필이 삭제되었습니다.").
- 프론트: 이 **커스텀 에러(410)** 로 분기하여 **"이용 중인 반려동물 프로필이 삭제되었습니다"** 안내 후 **반려동물 전환 페이지**로 이동.
- (한 번 410 응답 후에는 서버에서 selectedPetId가 null이므로, 이후 같은 유저가 다시 해당 API를 호출하면 "선택된 반려동물 없음"으로 처리됨.)

---

## `GET /api/pets/selected` — 선택 Pet이 이미 삭제된 경우

**상황**: `selectedPetId`는 있으나 해당 Pet·UserPet이 없음(다른 owner 탈퇴 등).

**처리:**

1. `selectedPetId == null` → **PET_NOT_FOUND** (선택된 반려동물 없음).
2. `selectedPetId != null`인데 Pet/UserPet이 없음 → **selectedPetId를 null로 저장**한 뒤 **SELECTED_PET_DELETED** 반환.

한 번 410 응답 후에는 `selectedPetId`가 정리되므로, 이후 호출은 “선택 없음” 흐름으로 이어진다.

---

## 에러 코드·응답

### 에러 코드

| 코드 (enum) | HTTP Status | 메시지 (message) | 용도 |
|-------------|-------------|-------------------|------|
| `SELECTED_PET_DELETED` | `GONE` (410) | 이용 중이던 반려동물 프로필이 삭제되었습니다. | 선택된 반려동물(selectedPet) 조회 시, 해당 Pet이 이미 삭제된 경우 |

- **410 Gone**: 리소스가 이전에 존재했으나 현재는 삭제되어 더 이상 사용할 수 없음을 명시할 때 사용.

### 에러 응답 형식

```json
{
  "success": false,
  "data": null,
  "message": "이용 중이던 반려동물 프로필이 삭제되었습니다."
}
```

- HTTP Status: **410 Gone**
- `ApiResponse` / `GlobalExceptionHandler`의 CustomException 패턴과 동일하게 **message**로 문구 전달.

### 클라이언트 처리

- **GET /api/pets/selected** 응답이 **410 (SELECTED_PET_DELETED)** 인 경우:
  - 토스트/알림: **"이용 중인 반려동물 프로필이 삭제되었습니다."** 표시.
  - 선택 반려동물 상태 초기화(로컬 selectedPetId 제거).
  - **반려동물 전환(선택) 페이지로 이동** (단순 "선택된 반려동물 없음"이 아니라, 삭제 안내 후 전환 유도).

---

## 한 줄 정리

| 구분 | 내용 |
|------|------|
| 탈퇴 | owner Pet/UserPet 삭제. 다른 유저 `selectedPetId`는 자동 null 아님. |
| `GET /api/pets/selected` | Pet 없으면 `selectedPetId` 정리 후 **410 SELECTED_PET_DELETED** |
| 클라이언트 | 410 시 삭제 안내 → 반려동물 선택 화면으로. 404 “선택 없음”과 구분. |
