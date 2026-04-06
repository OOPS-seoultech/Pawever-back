# 미리 살펴보기 — 체크리스트·콘텐츠 구조 (참고)

Figma 기준 **“미리 살펴보기”** 는 **체크리스트 5단계**(`checklist_items` id 1~5, `pet_checklists` 완료 여부)와 **화면 카피·카드 UI** 로 나누어 다루는 것을 전제로 한다.

## 백엔드 API

| API | 역할 |
|-----|------|
| `GET /api/pets/{petId}/checklist` | 5단계별 `completed`, 진행률 |
| `POST /api/pets/{petId}/checklist/{checklistItemId}/toggle` | Owner만 단계 완료 토글 |

**DB에 화면 카피·카드 문단·이미지 URL을 넣을 필요는 없다.** (변경이 잦으면 프론트·번들·원격 JSON이 유리)

## Figma에서의 공통 패턴 (메타)

- 상단 **5단계 스테퍼** (이별방법 → 지원사업) — 동일 레이아웃, 현재 단계만 강조
- **본문**: 히어로/일러스트 + **카드 블록** — 제목 + 본문 + (선택) 링크/확장 행
- **보조 CTA** — 예: 다른 이별 방법 안내 → 별도 화면/바텀시트
- 하단 **Primary 버튼** 1~2개

## 콘텐츠 배치 방식

| 방식 | 적합한 경우 |
|------|-------------|
| 프론트 하드코딩 / 번들 내 모듈 | 카피·화면 수가 고정, 출시 주기가 짧음 |
| 원격 JSON (S3/CDN, 버전 필드) | 문구만 자주 바꾸고 앱 심사 없이 반영하고 싶음 |
| DB/CMS | 운영이 직접 수정, A/B, 다국어를 서버에서 통제 |

**현 단계 권장**: 진행 상태는 API, 콘텐츠는 프론트 상수 + 타입으로 구조화. 이후 CDN JSON으로 옮기기 쉽게 타입만 유지.

## 프론트 타입 예시 (참고)

```ts
/** checklist_items.id 와 매핑 (1~5) */
type ChecklistStageId = 1 | 2 | 3 | 4 | 5;

type PreviewScreenKey =
  | 'FAREWELL_FUNERAL_COMPANY'
  | 'FAREWELL_KIT'
  | 'FAREWELL_ILLEGAL_WARNING'
  | 'BURIAL_INTRO'
  | 'BURIAL_STEP_1' | 'BURIAL_STEP_2' | 'BURIAL_STEP_3' | 'BURIAL_STEP_4' | 'BURIAL_STEP_5'
  | 'ADMIN_A' | 'ADMIN_B'
  | 'BELONGINGS'
  | 'SUPPORT_A' | 'SUPPORT_B';

interface PreviewScreen {
  key: PreviewScreenKey;
  figmaNodeId?: string;
  title?: string;
  blocks: ContentBlock[];
  actions?: { label: string; target: PreviewScreenKey | 'BACK' | 'CLOSE' }[];
}

type ContentBlock =
  | { type: 'hero'; imageAsset: string }
  | { type: 'card'; title: string; body: string[]; footnote?: string; link?: { label: string; href: string } }
  | { type: 'disclosure'; label: string; target: PreviewScreenKey }
  | { type: 'caption'; text: string };
```

## Figma 노드 ↔ 단계 (참고)

| 단계 | 화면 | Figma `node-id` |
|------|------|-----------------|
| 1 이별방법 | 장례업체 | `2189-53693` |
| 1 이별방법 | 이별 키트 | `2189-53409` |
| 1 이별방법 | 불법 안내 | `2189-53956` |
| 2 안치준비 | 첫 | `2189-52744` |
| 2 안치준비 | step1~5 | `2189-51500`, `2189-52946`, `2189-51708`, `2189-51914`, `2189-52120` |
| 3 행정처리 | | `2189-50345`, `2189-50482` |
| 4 물건정리 | | `2211-26612` |
| 5 지원사업 | | `2052-77197`, `2052-77681` |

## 긴급 대처 API

`POST /api/pets/{petId}/emergency` 응답에는 **`memorial` 정보만** 포함된다. 미리 살펴보기/가이드 문단은 앱 측 플로우·콘텐츠로 처리한다.
