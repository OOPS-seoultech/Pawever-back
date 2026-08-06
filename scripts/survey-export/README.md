# 설문 결과 정리

내보내기 API가 뱉는 CSV는 기계가 읽는 모양이다. 사람이 볼 수 있게 나누고,
읽을 수 있게 대시보드 한 장으로 접는 스크립트다.

## 나오는 것

| 파일 | 받는 사람 | 내용 |
|---|---|---|
| `마케팅.csv` | 마케팅 | 유입 경로, 쪽별 체류 시간, 이탈 지점 |
| `설문.csv` | 연구 | 문항별 응답. 코드값이 아니라 실제 문구 |
| `문항정의.csv` | 연구 | 문항·선택지 코드북 |
| `사연.csv` | 연구 | 서술형 답변. 맨 앞 두 칸이 동의 여부 |
| `신청자.csv` | 배송·CS | **개인정보 포함.** 이름·연락처·주소·동의 기록 |
| `대시보드.html` | 전부 | 파일 하나로 열리는 화면. 인터넷 불필요 |

전부 `응답ID`로 서로 이어붙일 수 있다.

## 준비물

- Python 3 (`python -X utf8` — 윈도우에서 한글이 깨지지 않게)
- Node 20 이상
- `Pawever-landing` 저장소가 옆에 있어야 한다. 문항 문구가 거기에만 있다.
- 내보내기 토큰. 서버 `.env`의 `GOODS_SURVEY_EXPORT_TOKEN`

```powershell
$t = (ssh pawever "grep '^GOODS_SURVEY_EXPORT_TOKEN=' /home/ubuntu/Pawever-back/.env | cut -d= -f2-").Trim()
```

## 순서

원본 세 장을 받는다. 토큰이 화면에 남지 않게 변수로 넘긴다.

```powershell
$api = "https://api.pawever.kr/api/internal/goods-survey/export"
$raw = "$HOME\Desktop\설문원본"; mkdir $raw -Force
foreach ($n in "responses","stories","applications") {
  curl.exe -sS -H "X-Survey-Export-Token: $t" "$api/$n" -o "$raw\$n.csv"
}
```

문항 정의를 랜딩 화면에서 뽑는다. `npx tsx`가 랜딩 저장소의 의존성을 쓰므로
그 폴더에서 실행해야 한다.

```powershell
cd <Pawever-landing>\client
npx tsx <Pawever-back>\scripts\survey-export\dump-schema.mts . "$raw\schema.json"
```

나누고, 접고, 명부를 만든다.

```powershell
cd <Pawever-back>\scripts\survey-export
$out = "$HOME\Desktop\설문분석"
python -X utf8 split-export.py      "$raw\responses.csv" "$raw\schema.json" $out "$raw\stories.csv"
python -X utf8 build-dashboard.py   "$raw\schema.json"   "$raw\responses.csv" $out
python -X utf8 build-applications.py "$raw\applications.csv" "$raw\stories.csv" $out
```

검산한다. 둘 다 통과해야 넘긴다.

```powershell
node smoke.mjs "$out\대시보드.html"
python -X utf8 verify.py $out
```

끝나면 `$raw` 폴더를 지운다. 테스트 응답과 개인정보가 그대로 든 원본이다.

## 캠페인마다 손봐야 하는 것

`excluded.txt` — 분석에서 뺄 응답ID. 우리가 넣어 본 테스트 응답이 여기 들어간다.
파일에 남은 ID가 원본에 없으면 스크립트가 멈춘다. 지난 캠페인 목록을 그대로
쓰다가 조용히 아무것도 안 빠지는 일을 막기 위해서다.

## 알아둘 것

**시각은 전부 UTC로 저장된다.** `신청자.csv`는 한국 시간으로 바꿔 내보낸다.
`마케팅.csv`의 원본 시각 열은 UTC 그대로다.

**매트릭스 문항은 다섯 줄이 번호도 제목도 같다.** 줄 이름을 붙이지 않으면
열 이름이 겹쳐서 판다스나 엑셀로 읽을 때 뒤 열이 앞 열을 덮는다. 조용히
사라지므로 `split-export.py`가 겹침을 발견하면 아예 멈춘다.

**랜딩 체류 시간은 쓰지 마라.** 인앱 브라우저에서 `document.hasFocus()`가
첫 조작 전까지 false라 대부분 1초 미만으로 찍힌다. 측정 방식을 고치기 전까지는
`랜딩→시작_분`을 믿을 수 없다.

**사연은 동의가 두 갈래다.** 분석 동의는 필수라 전부 분석에 쓸 수 있지만,
밖으로 인용하려면 `공유동의`가 `true`인 것만 써야 한다.

**마케팅 수신 동의는 받은 적이 없다.** 신청 화면의 이용 목적이 "굿즈 제작·발송,
문의 대응"으로 한정돼 있다. 그래서 `신청자.csv`의 해당 열은 `X`가 아니라
`미수집`이다. 이 명단으로 광고성 메시지를 보내려면 동의를 새로 받아야 한다.

**`신청자.csv`에는 파기 기한이 있다.** 응시자에게 "굿즈 발송일로부터 3주 뒤
삭제"라고 안내했다. 전달할 때 함께 알려야 한다.
