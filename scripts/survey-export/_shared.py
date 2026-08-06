"""내보내기 정리 스크립트가 함께 쓰는 것들."""

import csv
from datetime import datetime, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent


def excluded_responses():
    """빼야 할 응답ID. 목록을 스크립트마다 두면 언젠가 서로 어긋난다."""
    ids = set()
    for line in (HERE / "excluded.txt").read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            ids.add(line)
    return ids


def read_csv(path):
    with Path(path).open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write_csv(path, header, rows):
    """엑셀이 한글을 깨뜨리지 않도록 BOM을 붙인다."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(header)
        writer.writerows(rows)
    print(f"  {path.name:16s} {len(rows):4d}행 · {len(header):3d}열")


def seoul(value):
    """저장된 시각은 UTC다. 그대로 두면 오후 7시 신청이 오전 10시로 읽힌다."""
    if not value:
        return ""
    at = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if at.tzinfo:
        at = at.replace(tzinfo=None)
    return (at + timedelta(hours=9)).strftime("%Y-%m-%d %H:%M")
