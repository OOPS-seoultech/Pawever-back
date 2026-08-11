# 앱 이용 현황 내보내기

앱 회원과 이용 현황을 CSV 한 장으로 받는다. 설문 내보내기(`scripts/survey-export`)와
같은 방식이며, 통로와 토큰만 따로 둔다.

## 준비

서버 `.env`에 토큰을 넣는다. **비어 있으면 내보내기가 열리지 않는다.**

```bash
STATS_EXPORT_TOKEN=<충분히 긴 무작위 문자열>
```

설문 토큰(`GOODS_SURVEY_EXPORT_TOKEN`)과 같은 값을 쓰지 않는다. 통계는 집계값뿐이라
팀 안에서 넓게 공유되는데, 토큰을 합치면 이름·연락처·주소가 나가는 설문 통로까지
함께 열린다.

## 받기

```powershell
$t = (ssh pawever "grep '^STATS_EXPORT_TOKEN=' /home/ubuntu/Pawever-back/.env | cut -d= -f2-").Trim()
curl.exe -sS -H "X-Stats-Export-Token: $t" `
  "https://api.pawever.kr/api/internal/stats/export/summary" `
  -o "$HOME\Desktop\앱통계.csv"
```

엑셀에서 바로 열린다(BOM 포함).

## 나오는 것

`구분,지표,값` 세 칸이 세로로 쌓인다. 지표를 열로 만들면 새 지표가 생길 때마다 열이
늘어 예전 파일과 나란히 볼 수 없어서 행으로 쌓는다.

| 구분 | 담기는 것 |
|---|---|
| `기준` | 집계 시각(KST), 시간대 안내 |
| `회원` | 전체 가입, 순 회원, 탈퇴, 온보딩 완료·미완료·완료율 |
| `가입채널` | 카카오·네이버·애플 |
| `유입경로` | 주변 추천·쓰레드·인스타그램·오프라인 소개·기타·미응답 |
| `연령대`·`성별` | 소셜에서 받은 값. 없으면 `미상` |
| `동의` | 푸시 알림·마케팅 수신 동의와 각 동의율, 푸시 토큰 보유 |
| `반려동물` | 등록, 이별 전·후, 응급 모드, 보호자-펫 연결, 펫 보유 회원 |
| `활동` | 미션 배정·완료, 추모 댓글, 응급 진행, 이별 준비 진행, 서비스 리뷰 |
| `월별가입` | 한국 시간 기준 월별 신규 가입 |

## 읽을 때 알아야 할 것

**`전체 가입`은 회원 수가 아니다.** 소셜 로그인만 해도 `users` 레코드가 생긴다.
서비스를 쓰기 시작한 사람은 `온보딩 완료`다. 두 수의 차이가 로그인 직후 이탈이다.

**채널·유입경로·연령대·동의는 순 회원만 센다.** 탈퇴하면 소셜 ID와 동의 시각이
모두 파기되므로(`User.withdraw`) 전체 가입자로 나누면 늘 모자라 보인다.

**`보호자-펫 연결`은 펫 수와 다르다.** 한 마리를 여러 보호자가 함께 본다.

**시각은 한국 시간으로 묶는다.** DB는 UTC로 저장하므로 그대로 세면 자정 언저리
가입이 전달로 밀린다. `월별가입`은 KST로 변환한 뒤 묶는다.

**테스트 계정이 섞여 있다.** 설문 쪽 `excluded.txt` 같은 제외 목록이 아직 없다.
팀이 만든 계정과 펫이 그대로 포함된 수치다.

**DAU·리텐션은 나오지 않는다.** 접속을 남기는 테이블이 없고 `request.log`는 4xx/5xx와
느린 요청만 기록한다. 필요해지면 `users.last_active_at`을 더하거나 접속 이벤트
테이블을 새로 두어야 한다.

## 서버에서 바로 확인하기

배포 전에 숫자만 급히 볼 때 쓴다. 통계에 쓰는 열은 암호화 대상이 아니라 SQL로 읽힌다.

```sql
-- 가입·온보딩·탈퇴
SELECT COUNT(*)                                                   AS 전체가입,
       SUM(deleted_at IS NULL)                                    AS 순회원,
       SUM(deleted_at IS NULL AND onboarding_complete = 1)        AS 온보딩완료
FROM users;

-- 가입 채널 (순 회원)
SELECT SUM(kakao_id IS NOT NULL) AS 카카오,
       SUM(naver_id IS NOT NULL) AS 네이버,
       SUM(apple_id IS NOT NULL) AS 애플
FROM users WHERE deleted_at IS NULL;

-- 월별 신규 가입 (한국 시간)
SELECT DATE_FORMAT(created_at + INTERVAL 9 HOUR, '%Y-%m') AS 월, COUNT(*) AS 가입
FROM users GROUP BY 월 ORDER BY 월;
```
