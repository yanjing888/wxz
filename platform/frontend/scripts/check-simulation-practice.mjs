import assert from "node:assert/strict";
import { practiceTask, PRACTICE_NAV } from "../public/newton-rings-simulation/student-practice.js";
import { INSTALL_UNITS } from "../public/newton-rings-simulation/student-course.js";
import { DEFAULT_OPTICS, IDEAL_OPTICS } from "../public/newton-rings-simulation/optics.js";

const state = { installed: [], optics: { ...DEFAULT_OPTICS, ...IDEAL_OPTICS, lampPower: 80 }, guidePassed: false };
for (const unit of INSTALL_UNITS) {
  const task = practiceTask(state);
  assert.equal(task.kind, "install");
  assert.equal(task.unit, unit.id);
  assert.equal(task.number, state.installed.length + 1);
  state.installed.push(unit.id);
}
assert.equal(practiceTask(state).kind, "ready");
// Saved learning results must not be rewritten by presentation changes.
state.guidePassed = true;
const saved = JSON.stringify(state);
assert.equal(practiceTask(state).action, "进入测验");
assert.equal(JSON.stringify(state), saved);
// Present only the first outstanding operation, respecting its prerequisites.
state.optics.cameraOn = false;
state.optics.reticle = 0;
assert.equal(practiceTask(state).key, "signal");
state.optics.cameraOn = true;
assert.equal(practiceTask(state).key, "reticle");
state.optics.reticle = state.optics.reticleTarget ?? 50;
assert.equal(practiceTask(state).kind, "ready");
// A later regression must reappear as the current task.
state.optics.mirrorAngle = 30;
assert.equal(practiceTask(state).key, "illumination");
// UI navigation collapses the practice stages while preserving existing IDs.
assert.deepEqual(PRACTICE_NAV.map((n) => n.stage), [0, 2, 3, 4]);
console.log("PASS: sequential practice, prerequisite ordering, saved progress and navigation compatibility");
