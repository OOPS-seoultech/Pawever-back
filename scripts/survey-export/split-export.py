"""설문 원본 내보내기를 마케팅용·연구용으로 나눈다.

    python -X utf8 split-export.py <responses.csv> <schema.json> <나갈폴더> [<stories.csv>]

네 파일 모두 응답ID를 갖는다. 나누되 다시 붙일 수 있어야
"인스타로 온 사람과 쓰레드로 온 사람의 답이 어떻게 다른가"를 볼 수 있다.
"""

import json
import statistics
import sys
from datetime import datetime, timedelta
from pathlib import Path

from _shared import excluded_responses, read_csv, write_csv

if len(sys.argv) < 4:
    raise SystemExit(__doc__)

SOURCE = Path(sys.argv[1])
SCHEMA = Path(sys.argv[2])
OUT = Path(sys.argv[3])
STORIES = Path(sys.argv[4]) if len(sys.argv) > 4 else None

TEST_RESPONSES = excluded_responses()

# 광고 클릭 ID는 유입 분석에 쓰이지 않는다. 유입소스·캠페인·소재가 따로 있다.
# Meta 쪽에서 개인 계정으로 되짚을 수 있는 값이라 내보내지 않는다.
DROPPED = {"광고클릭ID"}

schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
questions = schema["questions"]
story_fields = schema["story"]
page_of = {q["id"]: q["page"] for q in questions}
option_of = {q["id"]: {o["id"]: o["label"] for o in q["options"]} for q in questions}


def column_label(question):
    """매트릭스 문항 다섯 줄은 번호도 제목도 같다. 줄 이름을 붙여야 열이 구분된다."""
    number = question["number"]
    if question["matrixIndex"]:
        number = f'{number}-{question["matrixIndex"]}'
    label = f'{number}. {question["title"]}'
    if question["matrixRow"]:
        label += f' [{question["matrixRow"]}]'
    return label


label_of = {q["id"]: column_label(q) for q in questions}

# 이름이 겹치면 판다스나 엑셀로 읽을 때 뒤 열이 앞 열을 덮어 조용히 사라진다.
collisions = len(label_of) - len(set(label_of.values()))
if collisions:
    raise SystemExit(f"열 이름이 {collisions}개 겹친다. 그대로 내보내면 읽을 때 사라진다.")
PAGES = sorted({q["page"] for q in questions})

rows = read_csv(SOURCE)
dropped_rows = [r for r in rows if r["응답ID"] in TEST_RESPONSES]
rows = [r for r in rows if r["응답ID"] not in TEST_RESPONSES]
for row in dropped_rows:
    print(f"  테스트 제외: {row['응답ID'][:8]} · 신청 {row['신청완료']}")
if len(dropped_rows) != len(TEST_RESPONSES):
    missing = TEST_RESPONSES - {r["응답ID"] for r in dropped_rows}
    raise SystemExit(f"빼려던 응답을 원본에서 찾지 못했다: {missing}")

columns = list(rows[0])
META = [c for c in columns[: columns.index("q1")] if c not in DROPPED]
ANSWER = columns[columns.index("q1"):]


def moment(value):
    """Z가 붙은 UTC와 안 붙은 서버 시각이 섞여 들어온다."""
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None


def naive(value):
    at = moment(value)
    return at.replace(tzinfo=None) if at else None


# 설문 시작 시각은 시간대 표시가 없다. 완료 시각(UTC)과 견줘 어긋난 만큼을 되돌린다.
gaps = []
for row in rows:
    started, finished = naive(row["설문시작시각"]), naive(row["설문완료일시"])
    if started and finished:
        gaps.append((finished - started).total_seconds())
shift = 0.0
if gaps:
    median = statistics.median(gaps)
    # 한 시간 넘게 벌어지면 시간대가 다른 것이다. 가장 가까운 정시로 맞춘다.
    if abs(median) > 3600:
        shift = round(median / 3600) * 3600
        print(f"  시간대 보정: 설문시작시각에 {shift/3600:+.0f}시간")

STEP_COLUMNS = [f"STEP{page:02d}_초" for page in PAGES]


def page_seconds(raw):
    """문항별 시간을 쪽 단위로 합친다. 분기 때문에 사람마다 대표 문항이 다르다."""
    try:
        timings = json.loads(raw or "{}")
    except json.JSONDecodeError:
        return {}
    seconds = {}
    for question_id, ms in timings.items():
        page = page_of.get(question_id)
        if page and isinstance(ms, (int, float)):
            seconds[page] = seconds.get(page, 0) + ms / 1000
    return seconds


marketing_header = (
    [c for c in META if c != "문항별시간JSON"]
    + ["랜딩→시작_분", "총소요_분"]
    + STEP_COLUMNS
    + ["최종도달STEP", "도달쪽수"]
)

marketing_rows = []
for row in rows:
    seconds = page_seconds(row["문항별시간JSON"])
    landed = moment(row["랜딩진입시각"])
    started = naive(row["설문시작시각"])
    if started:
        started = started + timedelta(seconds=shift)

    to_start = ""
    if landed and started:
        minutes = (started.replace(tzinfo=landed.tzinfo) - landed).total_seconds() / 60
        if 0 <= minutes < 24 * 60:
            to_start = f"{minutes:.1f}"

    # 이탈 지점은 마지막으로 머문 문항이 가장 정확하다.
    last_page = page_of.get(row["마지막문항"])
    reached = last_page or (max(seconds) if seconds else "")

    marketing_rows.append(
        [row[c] for c in META if c != "문항별시간JSON"]
        + [to_start, f'{int(row["설문소요밀리초"] or 0)/60000:.1f}']
        + [f"{seconds[p]:.0f}" if p in seconds else "" for p in PAGES]
        + [str(reached), str(len(seconds))]
    )


def answer_label(column, raw):
    if not raw:
        return ""
    if column.endswith("_text"):
        return raw
    labels = option_of.get(column, {})
    return " | ".join(labels.get(v, v) for v in raw.split(" | "))


survey_header = ["응답ID", "상태"] + [
    label_of.get(c, f"{c[:-5]} 직접 입력" if c.endswith("_text") else c) for c in ANSWER
]
survey_rows = [
    [row["응답ID"], row["상태"]] + [answer_label(c, row[c]) for c in ANSWER]
    for row in rows
]

codebook_header = [
    "문항ID", "번호", "섹션", "쪽", "유형", "질문", "매트릭스행", "선택지ID", "선택지"
]
codebook_rows = [
    [q["id"], q["number"], q["section"], str(q["page"]),
     "복수선택" if q["kind"] == "multi" else "단일선택",
     q["title"], q["matrixRow"] or "", option["id"], option["label"]]
    for q in questions
    for option in q["options"]
]

OUT.mkdir(parents=True, exist_ok=True)
write_csv(OUT / "마케팅.csv", marketing_header, marketing_rows)
write_csv(OUT / "설문.csv", survey_header, survey_rows)
write_csv(OUT / "문항정의.csv", codebook_header, codebook_rows)

# 사연은 별도 표에 있다. 분석 동의는 필수라 전부 분석할 수 있지만,
# 밖으로 인용하려면 공유 동의를 따로 확인해야 한다. 그래서 두 칸을 맨 앞에 둔다.
if STORIES:
    if not STORIES.exists():
        raise SystemExit(f"사연 원본이 없다: {STORIES}")
    told = [r for r in read_csv(STORIES) if r["응답ID"] not in TEST_RESPONSES]

    story_header = ["응답ID", "분석동의", "공유동의"] + [f["label"] for f in story_fields]
    story_rows = [
        [row["응답ID"], row["분석동의"], row["공유동의"]]
        + [(row.get(f["id"]) or "").strip() for f in story_fields]
        for row in told
    ]
    # 한 글자도 쓰지 않은 사람은 싣지 않는다. 빈 줄이 표를 부풀릴 뿐이다.
    story_rows = [r for r in story_rows if any(r[3:])]
    write_csv(OUT / "사연.csv", story_header, story_rows)

    essays = [f for f in story_fields if f["essay"]]
    shared = sum(1 for r in story_rows if r[2].lower() == "true")
    letters = sum(len(cell) for r in story_rows for cell in r[3:])
    print(f"    공유 동의 {shared}명 / {len(story_rows)}명 · 전체 {letters:,}자")
    for field in essays:
        at = 3 + story_fields.index(field)
        written = [r[at] for r in story_rows if r[at]]
        if written:
            average = sum(len(w) for w in written) / len(written)
            print(f"    {field['label'][:28]:30s} {len(written):3d}명 · 평균 {average:.0f}자")
