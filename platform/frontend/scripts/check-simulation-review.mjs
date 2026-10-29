import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import * as course from "../public/newton-rings-simulation/student-course.js";
import * as optics from "../public/newton-rings-simulation/optics.js";
import * as practice from "../public/newton-rings-simulation/student-practice.js";
import { adjustmentGoals } from "../public/newton-rings-simulation/student-adjustment.js";
import { initLightPathLearning } from "../public/newton-rings-simulation/light-path-learning.js";

// Exercise the actual student controller with a minimal DOM and model adapter.
const nodes = new Map();
const node = (id) => {
  if (!nodes.has(id)) nodes.set(id, {
    style: {}, dataset: {}, classList: { toggle() {}, add() {}, remove() {} },
    setAttribute() {}, querySelectorAll: () => [],
    getContext: () => ({ fillRect() {}, fillText() {} }),
  });
  return nodes.get(id);
};
const modelCalls = [];
const model = {
  thumbs: {}, select() {}, optics() {}, explode() {}, view() {}, updateMonitor() {},
  configureStudentUnits(_units, installed) { modelCalls.push([...installed]); },
};
const source = (await readFile(new URL("../public/newton-rings-simulation/student.js", import.meta.url), "utf8")).replace(/\r\n/g, "\n");
const controller = source.slice(0, source.indexOf('try {\n  lab = createLab')).replace(/import\s+[\s\S]*?from\s+"[^"]+";/g, "");
const context = vm.createContext({
  ...course, ...optics, ...practice, adjustmentGoals,
  document: { body: node("body"), getElementById: node, createElement: () => node("canvas"), querySelector: node },
  localStorage: { getItem: () => null, setItem() {} },
  setTimeout: (callback) => { queueMicrotask(callback); return 1; }, clearTimeout() {},
  drawField: () => ({ brightness: 1, sharpness: 1, contrast: 1, ready: true, issues: [] }),
  model, assert,
});
vm.runInContext(controller + '\nlab = model;', context);
await vm.runInContext(`(async () => {
  // Initial assembly still transitions into practice.
  for (const unit of INSTALL_UNITS) {
    state.selected = unit.id;
    await installCurrent();
  }
  assert.equal(state.stage, 1);
  assert.equal(allInstalled(), true);
  state.guidePassed = true;
  const original = JSON.stringify(state);
  setExplodedPreview(true);
  render();
  assert.equal(reviewInstalled.length, 0);
  assert.equal($('taskAction').textContent, '安装此组件');
  await installReviewUnit(INSTALL_UNITS[1].id);
  assert.equal(reviewInstalled.length, 0, 'out-of-order installation rejected');
  // Direct model clicks install one unit, without changing saved completion.
  interact(INSTALL_UNITS[0].model, { phase: 'start' });
  interact(INSTALL_UNITS[0].model, { phase: 'click' });
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(reviewInstalled.length, 1);
  assert.equal(state.installed.length, 6);
  // Return midway, then reopen: a fresh six-step review is available.
  setExplodedPreview(false);
  assert.equal(JSON.stringify(state), original);
  setExplodedPreview(true);
  assert.equal(reviewInstalled.length, 0);
  for (const unit of INSTALL_UNITS) {
    runCurrentTask();
    await Promise.resolve();
    await Promise.resolve();
    assert.equal(reviewInstalled.at(-1), unit.id);
  }
  assert.equal($('taskAction').textContent, '返回练习');
  runCurrentTask();
  assert.equal(explodedPreview, false);
  assert.equal(JSON.stringify(state), original, 'optics and assessment progress preserved');
})()`, context);
assert.deepEqual(modelCalls.at(-1), course.INSTALL_UNITS.map((unit) => unit.id));

const article = {};
const buttons = Array.from({ length: 4 }, (_, i) => ({ dataset: { lesson: String(i) }, setAttribute(key, value) { this[key] = value; } }));
const host = { querySelectorAll: () => buttons, querySelector: () => article };
initLightPathLearning(host);
for (const [i, title] of ["光从哪里来", "光如何到达空气膜", "两束光如何形成明暗环", "环纹如何变成屏幕图像"].entries()) {
  buttons[i].onclick();
  assert.ok(article.innerHTML.includes(title));
  assert.equal(buttons[i]["aria-pressed"], "true");
  assert.equal(buttons.filter((button) => button["aria-pressed"] === "true").length, 1);
}
for (const file of ["index.html", "student.html"]) {
  const html = await readFile(new URL(`../public/newton-rings-simulation/${file}`, import.meta.url), "utf8");
  assert.ok(html.includes('id="lightPathLearning"'));
}
console.log("PASS: initial assembly, model-click and button reassembly, early return, reopen, preserved practice state, four learning steps and both entry pages");
