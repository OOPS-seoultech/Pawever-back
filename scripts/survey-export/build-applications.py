"""배송·CS용 신청자 명부를 만든다.

    python -X utf8 build-applications.py <applications.csv> <stories.csv> <나갈폴더>

이름·연락처·주소가 그대로 담긴다. 응시자에게 약속한 대로
굿즈 발송일로부터 3주 뒤 파기해야 하는 파일이다.

동의 버전과 시각은 내보내기에 실려 오므로 그대로 쓴다.
개인정보 동의 자체는 신청의 전제조건이라 명부에 있는 사람은 모두 동의한 것이다.
"""

import sys
from pathlib import Path

from _shared import excluded_responses, read_csv, seoul, write_csv

if len(sys.argv) < 4:
    raise SystemExit(__doc__)

APPLICATIONS = Path(sys.argv[1])
STORIES = Path(sys.argv[2])
OUT = Path(sys.argv[3])

TEST_RESPONSES = excluded_responses()
YES, NO, NONE = "O", "X", "–"

applications = read_csv(APPLICATIONS)

if "개인정보동의버전" not in applications[0]:
    raise SystemExit("내보내기에 동의 열이 없다. 백엔드 배포가 반영되지 않았다.")

# 사연 원본이 없으면 동의 열이 전부 "해당 없음"으로 채워진다.
# 조용히 틀린 명부를 만드느니 여기서 멈춘다.
if not STORIES.exists():
    raise SystemExit(f"사연 원본이 없다: {STORIES}\n  /export/stories 를 먼저 내려받아야 한다.")
stories = {r["응답ID"]: r for r in read_csv(STORIES)}

# 제작번호는 이미 넘긴 사진 파일 이름과 맞춰야 한다.
# 사진은 신청 시각 순으로 01부터 매겨지므로, 뺀 응답이 앞자리를 차지했다면
# 진짜 신청자는 그다음 번호부터 시작한다. 여기서 다시 1번부터 매기면 사진과 어긋난다.
applications.sort(key=lambda r: r["신청일시"])
for order, row in enumerate(applications, start=1):
    row["제작번호"] = f"{order:02d}"

kept = [r for r in applications if r["응답ID"] not in TEST_RESPONSES]
print(f"신청 {len(applications)}건 중 {len(applications) - len(kept)}건 제외 → {len(kept)}건")
print(f"제작번호 {kept[0]['제작번호']} ~ {kept[-1]['제작번호']} (사진 파일명과 같은 번호)")

header = [
    "제작번호", "신청일시(한국)", "굿즈이름", "굿즈종류", "직접입력굿즈",
    "반려견이름", "보호자이름", "연락처", "우편번호", "주소", "상세주소", "사진수",
    "개인정보동의", "동의일시(한국)", "동의버전",
    "사연분석동의", "SNS공유동의", "마케팅수신동의",
    "응답ID",
]

rows = []
for row in kept:
    story = stories.get(row["응답ID"])
    rows.append([
        row["제작번호"],
        seoul(row["신청일시"]),
        row["굿즈이름"],
        row["굿즈종류"],
        row["직접입력굿즈"],
        row["반려견이름"],
        row["보호자이름"],
        row["연락처"],
        row["우편번호"],
        row["주소"],
        row["상세주소"],
        row["사진수"],
        # 동의 없이는 제출 자체가 막힌다. 명부에 있다는 것이 곧 동의했다는 뜻이다.
        YES,
        seoul(row["개인정보동의일시"]),
        row["개인정보동의버전"],
        (YES if story["분석동의"] == "true" else NO) if story else NONE,
        (YES if story["공유동의"] == "true" else NO) if story else NONE,
        # 광고성 정보 수신 동의는 받은 적이 없다. X(물어봤는데 거부)와 구분해 적는다.
        "미수집",
        row["응답ID"],
    ])

write_csv(OUT / "신청자.csv", header, rows)

told = sum(1 for r in rows if r[15] != NONE)
shared = sum(1 for r in rows if r[16] == YES)
print(f"  개인정보 동의   {len(rows)}명 (전원, 신청 조건)")
print(f"  사연 작성       {told}명 · SNS 공유 동의 {shared}명")
print(f"  마케팅 수신 동의 0명 — 받은 적이 없어 전 행 '미수집'")

blank = [r for r in rows if not r[7].strip() or not r[9].strip()]
if blank:
    print(f"\n연락처나 주소가 빈 행 {len(blank)}건: {[r[0] for r in blank]}")
