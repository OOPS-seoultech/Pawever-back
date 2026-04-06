# 소셜 로그인 수신 필드

앱에서 네이버/카카오 로그인 시 각 제공처에서 받아오는 항목과 필수·선택 구분, 백엔드 매핑 정리.

---

## 네이버 로그인

| 항목           | 필수/선택 | API 필드   | User 필드  |
|----------------|-----------|------------|------------|
| 회원이름       | 필수      | `name`     | `name`     |
| 성별           | 추가      | `gender`   | `gender`   |
| 생일           | 추가      | `birthday` | `birthday` |
| 연령대         | 필수      | `age`      | `ageRange` |
| 출생연도       | 추가      | `birthyear`| `birthYear`|
| 휴대전화번호   | 필수      | `mobile`   | `phone`    |

- 프로필 이미지(`profile_image` → `profileImageUrl`), 닉네임(`nickname`), 이메일(`email`) 등 추가로 수신 가능한 값도 저장 시 매핑함.
- 신규 가입 시 위 필수 항목(name, age, mobile)이 없으면 네이버 연동/재동의 안내 필요.

---

## 카카오 로그인

| 항목             | 필수/선택 | API 필드              | User 필드   |
|------------------|-----------|------------------------|-------------|
| 이름             | 필수      | `kakao_account.name`   | `name`      |
| 연령대           | 필수      | `kakao_account.age_range` | `ageRange` |
| 계정-전화번호    | 필수      | `kakao_account.phone_number` | `phone`  |
| 계정-이메일      | 선택      | `kakao_account.email`  | `email`     |
| 성별             | 선택      | `kakao_account.gender` | `gender`    |
| 생일             | 선택      | `kakao_account.birthday` | `birthday` |
| 출생연도         | 선택      | `kakao_account.birthyear` | `birthYear` |

- 프로필 닉네임/프로필 이미지(`kakao_account.profile` → `nickname`, `profileImageUrl`)도 매핑.
- 전화번호는 `+82 10-...` 형식일 경우 `010-...` 로 변환 후 저장.
- 선택 항목은 미동의 시 null로 저장되며, 필수 항목(name, age_range, phone_number) 미수집 시 카카오 동의 단계/재요청 필요.

---

## 코드 위치

- **네이버**: `NaverApiClient.NaverUserInfo` → `AuthService.naverLogin()` 에서 `User` 필드 매핑.
- **카카오**: `KakaoApiClient.KakaoUserInfo` / `KakaoAccount` → `AuthService.kakaoLogin()` 에서 `User` 필드 매핑.

필수 항목이 비어 있으면 가입/연동이 완성되지 않을 수 있으므로, 앱에서 제공처 동의 화면에서 필수 항목 수집 후 로그인 요청하는 흐름을 권장한다.
