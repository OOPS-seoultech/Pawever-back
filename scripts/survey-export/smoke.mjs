/* 브라우저 없이 대시보드의 렌더 경로를 한 번 돌려 본다.
 *
 *   node smoke.mjs <대시보드.html>
 *
 * 문법이 맞아도 undefined 접근으로 터지는 곳이 남는다.
 * 화면 네 개와 거르개 조합을 실제로 그려 보고, 숫자가 맞는지 검산한다.
 */
import { readFileSync } from "node:fs";

const target = process.argv[2];
if (!target) {
  console.error("사용법: node smoke.mjs <대시보드.html>");
  process.exit(1);
}

class Node {
  constructor(tag) {
    this.tag = tag;
    this.children = [];
    this.style = {};
    this.className = "";
    this.dataset = {};
    this.attrs = {};
    this.offsetHeight = 132;
    this.classList = { add: cls => { this.className += " " + cls; } };
  }
  get lastChild() { return this.children[this.children.length - 1]; }
  set textContent(value) { this._text = String(value); this.children = []; }
  get textContent() {
    if (this._text !== undefined) return this._text;
    return this.children.map(c => c.textContent ?? "").join("");
  }
  append(...nodes) {
    for (const node of nodes) {
      if (node === undefined || node === null) {
        throw new Error(`<${this.tag}>에 빈 노드를 붙였다`);
      }
      this.children.push(node);
    }
  }
  replaceChildren(...nodes) { this.children = []; this._text = undefined; this.append(...nodes); }
  setAttribute(name, value) { this.attrs[name] = value; }
  getAttribute(name) { return this.attrs[name]; }
  removeAttribute(name) { delete this.attrs[name]; }
  addEventListener() {}
  closest() { return null; }
  querySelector() { return null; }
}

const registry = new Map();
globalThis.document = {
  createElement: tag => new Node(tag),
  createTextNode: text => ({ textContent: String(text) }),
  documentElement: Object.assign(new Node("html"), { style: { setProperty() {} } }),
  querySelector: sel => {
    if (!registry.has(sel)) registry.set(sel, new Node(sel));
    return registry.get(sel);
  },
};
globalThis.window = { addEventListener() {}, matchMedia: () => ({ matches: false }) };

const html = readFileSync(target, "utf8");
const source = html.split("<script>")[1].split("</script>")[0];
const app = new Function(
  source +
  "\nreturn { state, sift, spread, render, DATA, BASELINE, HAS_STORY };"
)();

const text = sel => registry.get(sel).textContent;
const problems = [];
const VIEWS = ["flow", "quiz", "story", "sheet"];

console.log("머리 숫자 :", text("#tally"));
if (!text("#tally").includes(String(app.DATA.rows.length))) {
  problems.push("응답 수가 머리말에 없다");
}

const flow = text("#view-flow");
if (!flow.includes("굿즈 신청")) problems.push("깔때기에 신청 칸이 없다");

for (const view of VIEWS) {
  app.state.view = view;
  app.render();
  const size = text("#view-" + view).length;
  console.log(`  ${view.padEnd(6)} ${size}자`);
  if (!size) problems.push(`${view} 화면이 비었다`);
}

// 거르개를 걸어도 무너지지 않는가
for (const src of app.DATA.labels.src.map((_, i) => i)) {
  for (const view of VIEWS) {
    app.state.src = src;
    app.state.stance = "app";
    app.state.view = view;
    app.render();
  }
}
app.state.src = null;
app.state.stance = null;
console.log(`거르개 통과 : 유입 ${app.DATA.labels.src.length}종 × ${VIEWS.length}화면`);

// 거르개는 응답을 더하거나 빼지 않는다
const all = app.sift().length;
const bySource = app.DATA.labels.src
  .map((_, i) => { app.state.src = i; return app.sift().length; })
  .reduce((a, b) => a + b, 0);
app.state.src = null;
if (all !== bySource) problems.push(`유입별 합 ${bySource} ≠ 전체 ${all}`);
console.log(`합계 검산 : ${all} = ${bySource}`);

// 단일선택 문항은 선택 합이 응답자 수와 같아야 한다
app.DATA.questions.forEach((q, i) => {
  const { answered, counts } = app.spread(app.DATA.rows, i);
  if (answered > app.DATA.rows.length) problems.push(`${q.num} 응답자가 전체보다 많다`);
  if (!q.multi && counts.reduce((a, b) => a + b, 0) !== answered) {
    problems.push(`${q.num} 단일선택인데 선택 합이 응답자 수와 다르다`);
  }
});
console.log(`문항 검산 : ${app.DATA.questions.length}개`);

console.log(problems.length ? "\n문제:\n  " + problems.join("\n  ") : "\n이상 없음");
process.exit(problems.length ? 1 : 0);
