"""설문 결과를 대시보드 한 장으로 접는다.

    python -X utf8 build-dashboard.py <schema.json> <responses.csv> <나갈폴더>

CSV는 사람이 읽으라고 만든 형식이 아니다. 81열을 눈으로 훑는 대신
문항마다 분포를 그리고, 유입 경로로 걸러 서로 견줄 수 있게 한다.

split-export.py를 먼저 돌려야 한다. 같은 폴더의 마케팅.csv·사연.csv를 읽는다.
"""

import json
import statistics
import sys
from pathlib import Path

from _shared import read_csv

if len(sys.argv) < 4:
    raise SystemExit(__doc__)

HERE = Path(__file__).resolve().parent
SCHEMA = Path(sys.argv[1])
RAW = Path(sys.argv[2])
OUT = Path(sys.argv[3])

schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
questions = schema["questions"]
story_fields = schema["story"]
by_id = {q["id"]: q for q in questions}

raw_rows = read_csv(RAW)
market_rows = {r["응답ID"]: r for r in read_csv(OUT / "마케팅.csv")}

# 뺄 응답은 마케팅.csv를 만들 때 이미 걸러졌다. 목록을 두 군데 두지 않는다.
before = len(raw_rows)
raw_rows = [r for r in raw_rows if r["응답ID"] in market_rows]
if before != len(raw_rows):
    print(f"제외된 응답 {before - len(raw_rows)}건 (마케팅.csv 기준)")

stories = {}
story_path = OUT / "사연.csv"
if story_path.exists():
    label_to_id = {f["label"]: f["id"] for f in story_fields}
    for row in read_csv(story_path):
        told = {
            label_to_id[label]: value.strip()
            for label, value in row.items()
            if label in label_to_id and value and value.strip()
        }
        if told:
            stories[row["응답ID"]] = {
                "shared": row["공유동의"].lower() == "true",
                "told": told,
            }
    print(f"사연 {len(stories)}건 · 공유 동의 {sum(1 for s in stories.values() if s['shared'])}건")

raw_columns = list(raw_rows[0])
answer_columns = raw_columns[raw_columns.index("q1"):]
asked = [c for c in answer_columns if c in by_id]
free_text = [c for c in answer_columns if c.endswith("_text")]
missing = [q["id"] for q in questions if q["id"] not in set(answer_columns)]

print(f"응답 {len(raw_rows)}건")
print(f"설문에 실린 문항 {len(asked)}개 · 직접입력 칸 {len(free_text)}개")
if missing:
    print(f"내보내기에 없는 문항 {len(missing)}개: {', '.join(missing)}")

# 화면 순서대로 세운다. 쪽 번호가 같으면 원본 열 순서를 따른다.
order = {qid: i for i, qid in enumerate(answer_columns)}
asked.sort(key=lambda qid: (by_id[qid]["page"], order[qid]))

sections = []
for qid in asked:
    section = by_id[qid]["section"]
    if section not in sections:
        sections.append(section)

out_questions = []
option_index = {}
for qid in asked:
    q = by_id[qid]
    option_index[qid] = {o["id"]: i for i, o in enumerate(q["options"])}
    # 매트릭스 문항은 번호가 같으므로 줄 번호를 붙여 구분한다.
    number = q["number"]
    if q["matrixIndex"]:
        number = f'{number}-{q["matrixIndex"]}'
    out_questions.append({
        "id": qid,
        "num": number,
        "sec": sections.index(q["section"]),
        "page": q["page"],
        "multi": q["kind"] == "multi",
        "varies": q["variesByAnswer"],
        "title": q["title"],
        "row": q["matrixRow"],
        "opts": [o["label"] for o in q["options"]],
        "text": f"{qid}_text" if f"{qid}_text" in free_text else None,
    })
question_at = {q["id"]: i for i, q in enumerate(out_questions)}

PAGES = sorted({q["page"] for q in questions})
STEP_COLUMNS = [f"STEP{page:02d}_초" for page in PAGES]


def pool(values):
    """반복되는 문자열은 사전으로 접어 파일을 가볍게 만든다."""
    seen = {}
    for value in values:
        seen.setdefault(value, len(seen))
    return seen


sources = pool(sorted({(m["유입소스"] or "(없음)") for m in market_rows.values()}))
mediums = pool(sorted({(m["유입매체"] or "(없음)") for m in market_rows.values()}))
creatives = pool(sorted({(m["소재"] or "(없음)") for m in market_rows.values()}))
devices = pool(sorted({(m["기기"] or "(없음)") for m in market_rows.values()}))
goods = pool(sorted({(m["최종굿즈"] or m["선택굿즈"] or "(없음)") for m in market_rows.values()}))


def number(value):
    try:
        return round(float(value), 1)
    except (TypeError, ValueError):
        return None


rows = []
for raw in raw_rows:
    market = market_rows.get(raw["응답ID"], {})
    story = stories.get(raw["응답ID"])

    answers = {}
    texts = {}
    for qid in asked:
        value = (raw.get(qid) or "").strip()
        if not value:
            continue
        lookup = option_index[qid]
        picked = [lookup[v] for v in value.split(" | ") if v in lookup]
        if picked:
            answers[question_at[qid]] = picked
        note = (raw.get(f"{qid}_text") or "").strip()
        if note:
            texts[question_at[qid]] = note

    step = market.get("최종도달STEP") or ""
    rows.append({
        "id": raw["응답ID"][:8],
        "done": 1 if raw["상태"] == "SUBMITTED" else 0,
        "app": 1 if market.get("신청완료") == "Y" else 0,
        "src": sources[market.get("유입소스") or "(없음)"],
        "med": mediums[market.get("유입매체") or "(없음)"],
        "cre": creatives[market.get("소재") or "(없음)"],
        "dev": devices[market.get("기기") or "(없음)"],
        "goods": goods[market.get("최종굿즈") or market.get("선택굿즈") or "(없음)"],
        "step": int(step) if step.isdigit() else 0,
        "toStart": number(market.get("랜딩→시작_분")),
        "mins": number(market.get("총소요_분")),
        "secs": [number(market.get(c)) for c in STEP_COLUMNS],
        "a": answers,
        "t": texts,
        "s": story["told"] if story else None,
        "shared": 1 if story and story["shared"] else 0,
    })


def flip(mapping):
    return [label for label, _ in sorted(mapping.items(), key=lambda kv: kv[1])]


payload = {
    "sections": sections,
    "pages": PAGES,
    "questions": out_questions,
    "story": story_fields,
    "labels": {
        "src": flip(sources),
        "med": flip(mediums),
        "cre": flip(creatives),
        "dev": flip(devices),
        "goods": flip(goods),
    },
    "rows": rows,
}

data = "const DATA = " + json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + ";"

template = (HERE / "dashboard.template.html").read_text(encoding="utf-8")
if "__DATA__" not in template:
    raise SystemExit("서식 파일에 __DATA__ 자리가 없다")
page = OUT / "대시보드.html"
page.write_text(template.replace("__DATA__", data), encoding="utf-8")

app = sum(r["app"] for r in rows)
reached = [r["step"] for r in rows if r["step"]]
told = sum(1 for r in rows if r["s"])
print(f"\n응답 {len(rows)} · 신청 {app} · 사연 {told}")
print(f"도달 STEP 중앙값 {statistics.median(reached):.0f}")
print(f"\n{page}  {page.stat().st_size/1024:.0f}KB")
