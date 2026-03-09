# 유저 탈퇴 및 반려동물 삭제 시나리오

## 1. 개요

- 유저 탈퇴 시 **해당 유저가 owner인 반려동물(Pet)**은 모두 삭제된다.
- 해당 반려동물을 **공유만 하던(owner가 아닌) 다른 유저**가 그 펫을 **selectedId**로 가지고 있던 경우,  
  홈(선택된 반려동물 조회) 접근 시 **"이용 중이던 반려동물 프로필이 삭제되었습니다"** 메시지를 보여줘야 한다.

---

## 2. 탈퇴 & 삭제 시나리오

### 2.1 유저 탈퇴 시 서버 처리 순서

| 순서 | 처리 내용 |
|------|-----------|
| 1 | 탈퇴 요청 유저가 **owner인 Pet 목록** 조회 |
| 2 | 각 Pet에 대해: **UserPet 전체 삭제** → **Pet 삭제** (기존 `deletePet`과 동일한 연쇄 삭제) |
| 3 | 유저 **soft delete** (`deletedAt` 설정) |

※ 다른 유저의 `selectedPetId`는 **탈퇴 시점에 서버에서 null로 바꾸지 않는다**.  
→ 해당 유저가 홈 접근 시 `GET /api/pets/selected`에서 **410 SELECTED_PET_DELETED**를 받도록 하여, 프론트에서 "이용 중인 반려동물 프로필이 삭제되었습니다" 안내 후 **반려동물 전환 페이지**로 보낸다.

### 2.2 시나리오 예시

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

## 3. 홈 화면: 선택 반려동물이 이미 삭제된 경우

### 3.1 상황

- 유저에게 **selectedPetId**는 있으나, 해당 Pet이 이미 삭제되어 **UserPet이 없거나 Pet이 DB에 없는 경우**  
  → “이용 중이던 반려동물 프로필이 삭제되었습니다”로 안내해야 함.

### 3.2 API: `GET /api/pets/selected`

**처리 로직:**

1. `selectedPetId == null` → 기존처럼 **PET_NOT_FOUND** (선택된 반려동물 없음).
2. `selectedPetId != null`인데, **UserPet이 없거나 Pet이 없음**  
   → 이 유저의 **selectedPetId를 null로 저장**한 뒤,  
   → **SELECTED_PET_DELETED** 커스텀 에러를 반환.

**결과:**

- 클라이언트는 **동일한 에러 코드(SELECTED_PET_DELETED)**로 “이용 중이던 반려동물 프로필이 삭제되었습니다” 메시지를 표시.
- 한 번 에러 응답 후에는 서버에서 selectedPetId가 null이므로, 다음 요청부터는 “선택된 반려동물 없음” 흐름으로 동작.

---

## 4. 커스텀 에러 및 응답 설계

### 4.1 에러 코드

| 코드 (enum) | HTTP Status | 메시지 (message) | 용도 |
|-------------|-------------|-------------------|------|
| `SELECTED_PET_DELETED` | `GONE` (410) | 이용 중이던 반려동물 프로필이 삭제되었습니다. | 선택된 반려동물(selectedPet) 조회 시, 해당 Pet이 이미 삭제된 경우 |

- **410 Gone**: 리소스가 이전에 존재했으나 현재는 삭제되어 더 이상 사용할 수 없음을 명시할 때 사용.

### 4.2 에러 응답 형식 (기존 패턴 유지)

```json
{
  "success": false,
  "data": null,
  "message": "이용 중이던 반려동물 프로필이 삭제되었습니다."
}
```

- HTTP Status: **410 Gone**
- 기존 `ApiResponse.error(message)` 및 `GlobalExceptionHandler`의 CustomException 처리와 동일하게, **message**로 클라이언트에 문구 전달.

### 4.3 클라이언트 처리 제안

- **GET /api/pets/selected** 응답이 **410 (SELECTED_PET_DELETED)** 인 경우:
  - 토스트/알림: **"이용 중인 반려동물 프로필이 삭제되었습니다."** 표시.
  - 선택 반려동물 상태 초기화(로컬 selectedPetId 제거).
  - **반려동물 전환(선택) 페이지로 이동** (단순 "선택된 반려동물 없음"이 아니라, 삭제 안내 후 전환 유도).

---

## 5. 요약

| 구분 | 내용 |
|------|------|
| **탈퇴 시** | owner인 Pet·UserPet만 삭제. 다른 유저의 selectedPetId는 갱신하지 않음. |
| **홈(selected 조회)** | selectedPetId는 있는데 Pet/UserPet이 없으면 → selectedPetId null 저장 후 **SELECTED_PET_DELETED (410)** 반환. |
| **에러 메시지** | "이용 중이던 반려동물 프로필이 삭제되었습니다." |
| **클라이언트** | 410 수신 시 "이용 중인 반려동물 프로필이 삭제되었습니다" 안내 후 **반려동물 전환 페이지**로 이동. (404 "선택된 반려동물 없음"과 구분하여 처리.) |
