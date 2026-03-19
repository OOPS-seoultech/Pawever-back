# 미리 살펴보기 — 체크리스트 5단계 콘텐츠 구조 (Figma 기준)

메모리얼 `guides` / `guide_steps`는 제거되었습니다. **“미리 살펴보기”** 화면은 **체크리스트 5단계**(`checklist_items` id 1~5 + `pet_checklists` 완료 여부)와 **프론트(또는 정적 번들) 콘텐츠**로 나누는 것을 권장합니다.

## 백엔드 역할 (유지)

| API | 역할 |
|-----|------|
| `GET /api/pets/{petId}/checklist` | 5단계별 `completed`, 진행률 |
| `POST /api/pets/{petId}/checklist/{checklistItemId}/toggle` | Owner만 단계 완료 토글 |

**DB에는 “화면 카피·카드 문단·이미지”를 넣지 않아도 됩니다.** (변경이 잦고 Figma와 1:1 매핑이 프론트가 유리함)

## Figma에서 보이는 공통 패턴 (메타데이터 기준)

- 상단 **5단계 스테퍼** (이별방법 → 지원사업) — 동일 레이아웃, 현재 단계만 강조
- **본문**: 히어로/일러스트 + **카드 블록**(`Good Div` 등) — 제목 + 여러 줄 본문 + (선택) 링크/확장 행
- **보조 CTA**: 예) “혹시 다른 방법을 생각하셨나요? 그건 불법일 수 있어요” — 별도 화면/바텀시트로 분기 가능
- 하단 **Primary btn** 1~2개 (예: 장례업체 보기 / 이별키트 보기)

예: `2189:53693` (이별방법·장례업체) — 스테퍼 + 카드 2개 + 경고 배너 + 2버튼.

## 프론트 하드코딩 vs 백엔드/JSON

| 방식 | 적합한 경우 |
|------|-------------|
| **프론트 하드코딩** (또는 앱 번들 내 `previewChecklist.ts` / i18n) | 카피·화면 수가 고정, 출시 주기가 짧음, Figma와 동기화를 코드 리뷰로 관리 |
| **원격 JSON** (S3/CDN, 버전 필드 포함) | 카피 자주 수정, 앱스토어 심사 없이 문구만 바꾸고 싶음 |
| **DB/CMS** | 운영/기획이 직접 수정, A/B, 다국어를 서버에서 통제 |

**권장(현 단계):**  
- **진행 상태만 API**  
- **콘텐츠는 프론트 상수 + 타입** (`PreviewScreen`, `ContentBlock`)으로 구조화  
- 나중에 “문구만 원격”이 필요해지면 같은 타입의 JSON을 CDN으로 옮기기만 하면 됨.

## 제안 데이터 모델 (프론트 TypeScript 예시)

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
  figmaNodeId: string; // 디자인 추적용 (런타임 필수 아님)
  title?: string;
  blocks: ContentBlock[];
  /** 다음 화면 라우팅 (스와이프/버튼) */
  actions?: { label: string; target: PreviewScreenKey | 'BACK' | 'CLOSE' }[];
}

type ContentBlock =
  | { type: 'hero'; imageAsset: string }
  | { type: 'card'; title: string; body: string[]; footnote?: string; link?: { label: string; href: string } }
  | { type: 'disclosure'; label: string; target: PreviewScreenKey } // 예: 불법 안내
  | { type: 'caption'; text: string };
```

## Figma 노드 ↔ 단계 매핑 (참고)

| 단계 | 화면 | Figma `node-id` |
|------|------|-----------------|
| 1 이별방법 | 장례업체 | `2189-53693` |
| 1 이별방법 | 이별 키트 | `2189-53409` |
| 1 이별방법 | 불법 안내(클릭 시) | `2189-53956` |
| 2 안치준비 | 첫 | `2189-52744` |
| 2 안치준비 | step1~5 | `2189-51500`, `2189-52946`, `2189-51708`, `2189-51914`, `2189-52120` |
| 3 행정처리 | | `2189-50345`, `2189-50482` |
| 4 물건정리 | | `2211-26612` |
| 5 지원사업 | | `2052-77197`, `2052-77681` |

프론트에서는 `ChecklistStageId`별로 `PreviewScreenKey[]` (탭/플로우 순서)만 정의하면 됩니다.

## 긴급 대처 API 변경

`POST /api/pets/{petId}/emergency` 응답에서 **`guides` 필드가 제거**되었습니다.  
이제 `{ "memorial": { ... } }` 만 반환합니다. 미리 살펴보기/안내 콘텐츠는 앱 내 플로우 + 위 구조로 처리하세요.
