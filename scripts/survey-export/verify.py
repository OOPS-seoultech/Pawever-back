"""넘기기 직전 검산.

    python -X utf8 verify.py <나갈폴더>

세 가지를 본다. 뺀 응답이 남아 있지 않은가, 파일끼리 응답ID로 이어지는가,
명부의 번호와 연락처에 구멍이 없는가.
"""

import sys
from pathlib import Path

from _shared import excluded_responses, read_csv

if len(sys.argv) < 2:
    raise SystemExit(__doc__)

OUT = Path(sys.argv[1])
EXCLUDED = excluded_responses()
problems = []


def load(name):
    path = OUT / name
    return read_csv(path) if path.exists() else None


files = {name: load(name) for name in
         ["마케팅.csv", "설문.csv", "사연.csv", "문항정의.csv", "신청자.csv"]}

print("── 파일 ──")
ids = {}
for name, rows in files.items():
    if rows is None:
        print(f"  없음 {name}")
        continue
    print(f"  {name:12s} {len(rows):4d}행 · {len(rows[0]):3d}열")
    if "응답ID" not in rows[0]:
        continue
    ids[name] = {r["응답ID"] for r in rows}
    leaked = EXCLUDED & ids[name]
    if leaked:
        problems.append(f"{name}에 제외 대상이 남았다: {[i[:8] for i in leaked]}")

print()
print("── 파일끼리 이어지는가 ──")
if "마케팅.csv" in ids and "설문.csv" in ids:
    same = ids["마케팅.csv"] == ids["설문.csv"]
    print(f"  마케팅 = 설문 : {same}")
    if not same:
        problems.append("마케팅.csv와 설문.csv의 응답이 다르다")
for name in ["사연.csv", "신청자.csv"]:
    if name in ids and "설문.csv" in ids:
        subset = ids[name] <= ids["설문.csv"]
        print(f"  {name[:-4]} ⊂ 설문 : {subset}")
        if not subset:
            problems.append(f"{name}에 설문 데이터에 없는 응답이 있다")

roster = files["신청자.csv"]
if roster:
    print()
    print("── 신청자 명부 ──")
    numbers = [int(r["제작번호"]) for r in roster]
    print(f"  제작번호 {min(numbers):02d} ~ {max(numbers):02d} · {len(roster)}명")
    if len(numbers) != len(set(numbers)):
        problems.append("제작번호가 겹친다")
    gaps = sorted(set(range(min(numbers), max(numbers) + 1)) - set(numbers))
    print(f"  빠진 번호 : {gaps or '없음'}")
    if gaps:
        problems.append(f"제작번호에 구멍이 있다: {gaps}")

    blank = [r["제작번호"] for r in roster
             if not r["연락처"].strip() or not r["주소"].strip()]
    print(f"  연락처·주소 빈 행 : {blank or '없음'}")
    if blank:
        problems.append(f"연락처나 주소가 비었다: {blank}")

    phones = [r["연락처"] for r in roster]
    if len(phones) != len(set(phones)):
        problems.append("연락처가 겹치는 신청자가 있다")

    print()
    print("── 동의 ──")
    for column in ["개인정보동의", "사연분석동의", "SNS공유동의", "마케팅수신동의"]:
        counts = {}
        for row in roster:
            counts[row[column]] = counts.get(row[column], 0) + 1
        print(f"  {column:10s} {counts}")

survey = files["설문.csv"]
if survey:
    header = list(survey[0])
    print()
    print(f"── 설문.csv 열 이름 {len(header)}개 · 고유 {len(set(header))}개 ──")
    if len(header) != len(set(header)):
        problems.append("설문.csv에 이름이 겹치는 열이 있다. 읽을 때 조용히 사라진다")

print()
print("문제:\n  " + "\n  ".join(problems) if problems else "이상 없음")
sys.exit(1 if problems else 0)
