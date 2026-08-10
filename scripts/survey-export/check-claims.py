"""광고 문구로 쓸 수치를 원본에서 확인한다.

랜딩·릴스·상세페이지에 적을 숫자는 기억이나 눈대중이 아니라 응답 원본에서
나와야 한다. 이 저장소는 이미 한 번, 근거 없는 카운터를 표시광고법상 위험이라며
걷어낸 적이 있다(V5 마이그레이션).

지금 확인하는 두 가지:

1. "하루 만에 N명 마감"  — 첫 신청부터 마지막 신청까지 실제로 몇 시간 걸렸나
2. "가장 많이 고른 굿즈"  — 어떤 굿즈를 실제로 신청했나

2번은 그냥 세면 틀린다. 2026-08-10 이전 랜딩은 굿즈 카드를 누르지 않고
CTA만 눌러도 아크릴이 자동으로 붙었고, 그 값이 제작 화면에도 미리 선택돼
있었다. 그래서 아크릴은 부풀고 나머지는 깎인 채로 저장돼 있다.

이 스크립트는 셋을 나눠 보여준다.

- 있는 그대로  : 오염 포함. 아크릴은 실제보다 크다
- 확실한 선택  : 랜딩에서 아크릴 아닌 카드를 눌렀거나 제작 화면에서 값을 바꾼 사람
- 판단 불가    : 랜딩도 제작도 아크릴. 진짜 고른 것인지 기본값이 지나간 것인지 알 수 없다

아크릴은 "판단 불가"만큼이 상한이고, 나머지 굿즈의 비율은 하한이다.
광고에 쓸 때는 하한을 쓴다.

사용법:
    python -X utf8 check-claims.py <responses.csv>
"""

import collections
import sys
from datetime import datetime, timedelta, timezone

from _shared import excluded_responses, read_csv

KST = timezone(timedelta(hours=9))

GOODS_NAMES = {
    "acrylic": "아크릴 얼굴키링",
    "face": "3D 얼굴 키캡형",
    "backplate": "뒷판형 3D 얼굴키링",
    "figure": "3D 전신 피규어",
    "custom": "자유 요청",
    "unselected": "고르지 않음",
    "": "(빈값)",
}

# 이 값이 기본으로 붙던 굿즈. 오염원이라 따로 다룬다.
DEFAULTED = "acrylic"


def at_kst(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(KST)


def bars(counter, total, title):
    print(f"\n{title}  (n={total})")
    if not total:
        print("  해당 없음")
        return
    for code, count in counter.most_common():
        share = count / total * 100
        print(
            f"  {GOODS_NAMES.get(code, code):<16}"
            f" {count:>4}건  {share:>5.1f}%  {'█' * round(share / 2.5)}"
        )


def check_sellout_speed(applications):
    print("=" * 62)
    print("1. 마감 속도 — \"하루 만에 마감\"이라고 쓸 수 있는가")
    print("=" * 62)
    if not applications:
        print("  신청 건이 없다.")
        return

    ordered = sorted(applications, key=lambda row: row["신청시각"])
    first, last = at_kst(ordered[0]["신청시각"]), at_kst(ordered[-1]["신청시각"])
    hours = (last - first).total_seconds() / 3600
    print(f"  신청 완료      {len(ordered)}건")
    print(f"  첫 신청        {first:%Y-%m-%d %H:%M} KST")
    print(f"  마지막 신청    {last:%Y-%m-%d %H:%M} KST")
    print(f"  전체 소요      {hours:.1f}시간")

    for window in (6, 12, 24, 48):
        reached = sum(
            1 for row in ordered if at_kst(row["신청시각"]) <= first + timedelta(hours=window)
        )
        print(f"    {window:>2}시간 내 누적  {reached:>4}건")

    daily = collections.Counter(f"{at_kst(r['신청시각']):%m-%d}" for r in ordered)
    print("\n  일자별(KST)")
    for day, count in sorted(daily.items()):
        print(f"    {day}  {count:>4}건  {'█' * count}")

    print()
    if hours <= 24:
        print(f"  → \"하루 만에 마감\" 표기 가능. 실제 {hours:.0f}시간.")
    else:
        print(f"  → \"하루 만에\"는 쓸 수 없다. 실제 {hours / 24:.1f}일 걸렸다.")


def check_goods_preference(applications, every_response):
    print("\n" + "=" * 62)
    print("2. 굿즈 선호 — \"가장 많이 고른 굿즈\"라고 쓸 수 있는가")
    print("=" * 62)
    if not applications:
        print("  신청 건이 없다.")
        return

    bars(
        collections.Counter(r["최종굿즈"] for r in applications),
        len(applications),
        "① 있는 그대로 — 기본값 오염 포함",
    )

    deliberate = [
        r
        for r in applications
        if r["선택굿즈"] != DEFAULTED or r["최종굿즈"] != r["선택굿즈"]
    ]
    unknown = [
        r
        for r in applications
        if r["선택굿즈"] == DEFAULTED and r["최종굿즈"] == DEFAULTED
    ]
    bars(
        collections.Counter(r["최종굿즈"] for r in deliberate),
        len(deliberate),
        "② 스스로 고른 것이 확실한 사람만",
    )
    print(f"\n  판단 불가(랜딩도 제작도 아크릴): {len(unknown)}건")

    clicked = [r for r in every_response if r["선택굿즈"] not in (DEFAULTED, "")]
    bars(
        collections.Counter(r["선택굿즈"] for r in clicked),
        len(clicked),
        "③ 랜딩에서 카드를 실제로 누른 흔적 (전체 응답 기준)",
    )

    top, count = collections.Counter(r["최종굿즈"] for r in applications).most_common(1)[0]
    share = count / len(applications) * 100
    print()
    if top == DEFAULTED:
        print(
            f"  → 1위가 {GOODS_NAMES[DEFAULTED]}({share:.1f}%)인데, 이 값은 기본값이 섞여 있어"
        )
        print("     실제보다 크다. 1위라고 쓰면 안 된다. ②를 근거로 삼아라.")
    else:
        print(f"  → {GOODS_NAMES.get(top, top)} {share:.1f}%가 1위다.")
        print(f"     오염은 {GOODS_NAMES[DEFAULTED]}를 부풀리는 방향이므로 이 비율은 하한이다.")
        print("     광고에는 이 하한을 쓴다.")


def main():
    if len(sys.argv) < 2:
        raise SystemExit("사용법: python -X utf8 check-claims.py <responses.csv>")

    excluded = excluded_responses()
    rows = [r for r in read_csv(sys.argv[1]) if r["응답ID"] not in excluded]
    applications = [r for r in rows if r["신청완료"] == "Y" and r["신청시각"]]

    print(f"응답 {len(rows)}행 (테스트 {len(excluded)}건 제외)\n")
    check_sellout_speed(applications)
    check_goods_preference(applications, rows)

    completed = sum(1 for r in rows if r["상태"] in ("RESERVED", "SUBMITTED"))
    print("\n" + "=" * 62)
    print("참고 — 퍼널")
    print("=" * 62)
    print(f"  설문 시작        {len(rows):>4}")
    print(f"  설문 완료        {completed:>4}   {completed / len(rows) * 100:.1f}%")
    print(f"  굿즈 신청        {len(applications):>4}   완료자 대비 "
          f"{len(applications) / completed * 100:.1f}%")


if __name__ == "__main__":
    main()
