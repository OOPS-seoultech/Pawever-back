// 문항 정의와 사연 문구는 랜딩 화면 코드에만 있다.
// 백엔드에 베껴 두면 언젠가 어긋나므로 여기서 직접 읽어 낸다.
//
//   cd <Pawever-landing>/client
//   npx tsx <Pawever-back>/scripts/survey-export/dump-schema.mts <landing>/client <나갈파일.json>
import { writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { resolve } from "node:path";

const [clientDir, target] = process.argv.slice(2);
if (!clientDir || !target) {
  console.error("사용법: dump-schema.mts <landing>/client <out.json>");
  process.exit(1);
}

const page = (name: string) =>
  pathToFileURL(resolve(clientDir, "src/pages", name)).href;

const schema = await import(page("goodsSurveySchema.ts"));
const content = await import(page("goodsSurveyContent.ts"));

const questions = (schema.surveyQuestions as any[]).map(question => ({
  id: question.id,
  page: question.page,
  number: question.number,
  section: question.section,
  kind: question.kind ?? "single",
  // 답에 따라 문구가 바뀌는 문항은 기본형을 싣는다.
  variesByAnswer:
    typeof question.title === "function" || typeof question.options === "function",
  title: schema.getQuestionTitle(question, {}),
  // 매트릭스 문항은 다섯 줄이 같은 번호·제목을 쓴다. 줄 이름이 없으면 열이 구분되지 않는다.
  matrixRow: question.matrix?.row ?? null,
  matrixIndex: question.matrix?.index ?? null,
  freeTextOptionId: question.freeTextOptionId ?? null,
  options: schema
    .getQuestionOptions(question, {})
    .map((option: any) => ({ id: option.id, label: option.label })),
}));

// 기본 정보(T1~T3)는 문자열 한 줄, 서술형은 label·prompt를 가진 객체다.
const story = Object.entries((content.goodsSurveyStoryContent as any).fields ?? {}).map(
  ([id, field]: [string, any]) =>
    typeof field === "string"
      ? { id, label: field, prompt: "", maxLength: null, essay: false }
      : {
          id,
          label: field.label,
          prompt: field.prompt ?? "",
          maxLength: field.maxLength ?? null,
          essay: true,
        }
);

writeFileSync(target, JSON.stringify({ questions, story }, null, 2), "utf8");

const pages = new Map<number, number>();
questions.forEach(q => pages.set(q.page, (pages.get(q.page) ?? 0) + 1));
const matrix = questions.filter(q => q.matrixRow);
console.log(`문항 ${questions.length}개 · ${pages.size}쪽 · 사연 ${story.length}개`);
console.log(`매트릭스 줄 ${matrix.length}개 (같은 번호를 나눠 쓰는 문항)`);
console.log(`답에 따라 문구가 바뀌는 문항 ${questions.filter(q => q.variesByAnswer).length}개`);
