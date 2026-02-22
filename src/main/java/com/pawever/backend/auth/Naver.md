## React Native + Spring Boot 네이버 로그인 구현

### 전체 플로우

```
1. [프론트] 네이버 로그인 버튼 클릭
2. [프론트] 네이버 앱 실행 (또는 네이버 계정 로그인)
3. [프론트] 액세스 토큰 수신
4. [프론트] 액세스 토큰을 백엔드로 전송
5. [백엔드] 네이버 API로 사용자 정보 조회
6. [백엔드] 회원가입/로그인 처리 후 JWT 발급
7. [프론트] JWT 저장
```

---

## 1. 네이버 Developers 설정

### 애플리케이션 등록

```
1. https://developers.naver.com/apps/#/register 접속
2. 애플리케이션 등록

앱 정보:
- 애플리케이션 이름: pawever
- 사용 API: 네이버 로그인
```

### 사용 API 설정

```
네이버 로그인:
- 제공 정보 선택
  ✅ 회원이름
  ✅ 이메일 주소
  ✅ 프로필 사진
  ⬜ 생일
  ⬜ 연령대
```

### 환경 추가

```
PC웹: http://localhost:28080/oauth/naver/callback
Android:
- 패키지명: com.pawever.app
- Download URL: (Play Store URL, 개발 중에는 비워둠)

iOS:
- URL Scheme: pawever (또는 원하는 스킴)
- URL Scheme: (App Store URL, 개발 중에는 비워둠)
```

### 발급받을 정보

```
Client ID: abc123def456...
Client Secret: XYZ789...
```

---

### application.yml

```yaml
naver:
  client-id: ${NAVER_CLIENT_ID}
  client-secret: ${NAVER_CLIENT_SECRET}
  redirect-uri: http://localhost:28080/oauth/naver/callback
```

## 카카오 + 네이버 통합 구조

### 백엔드 API

```
POST /api/auth/kakao   → 카카오 로그인
POST /api/auth/naver   → 네이버 로그인
```

둘 다 같은 형식의 JWT 반환:
```json
{
  "token": "JWT_TOKEN",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "닉네임",
    "loginType": "NAVER"
  }
}
```

---

## 네이버 Developers 설정 체크리스트

### 백엔드 개발자

- [ ] https://developers.naver.com 앱 등록
- [ ] Client ID, Client Secret 발급
- [ ] 네이버 로그인 API 활성화
- [ ] 제공 정보 선택 (이름, 이메일, 프로필)
- [ ] PC웹 Callback URL 등록
- [ ] Client ID를 프론트엔드에 전달
- [ ] Client Secret을 환경변수에 저장

### 프론트엔드 개발자

- [ ] Client ID 백엔드로부터 받기
- [ ] 패키지명 백엔드에 전달
- [ ] iOS URL Scheme 백엔드에 전달
- [ ] `@react-native-seoul/naver-login` 설치
- [ ] Android/iOS 설정 완료

---

## 주의사항

1. **Client Secret 보안**
    - 프론트 코드에 노출되면 안 됨
    - 백엔드 환경변수에만 저장

2. **계정 통합**
    - 같은 이메일로 카카오/네이버 로그인 시 처리 방법 결정
    - 옵션 1: 별도 계정으로 관리
    - 옵션 2: 이메일 기준으로 통합

3. **테스트**
    - 네이버 앱 설치된 기기에서 테스트
    - 네이버 앱 없는 기기에서도 테스트