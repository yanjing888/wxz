import assert from "node:assert/strict";
import { DEFAULT_OPTICS, IDEAL_OPTICS } from "../public/newton-rings-simulation/optics.js";
import { adjustmentGoals } from "../public/newton-rings-simulation/student-adjustment.js";

const goal = (o, id, assembled = true) => adjustmentGoals(o, assembled).find((g) => g.id === id);
const baseline = { ...DEFAULT_OPTICS, ...IDEAL_OPTICS, lampPower: 80 };
assert(adjustmentGoals(baseline, true).every((g) => g.ok));
assert.equal(adjustmentGoals(baseline, true).reduce((n, g) => n + g.points, 0), 60);

// Correct alignment stays correct at every supported camera exposure/aperture,
// including the formerly failing tolerance boundaries after folding the mirror.
for (const mirrorAngle of [43, 45, 47])
  for (const lampPosition of [-5, 0, 5])
    for (const exposure of [20, 90, 300])
      for (const aperture of [10, 55, 100]) {
        const o = { ...baseline, mirrorAngle, lampPosition, exposure, aperture };
        assert(goal(o, "illumination").ok, JSON.stringify(o));
      }

for (const change of [{ lightOn: false }, { lampPosition: 6 }, { lampPosition: -6 }, { mirrorAngle: 42.75 }, { mirrorAngle: 47.25 }]) {
  const failed = goal({ ...baseline, ...change }, "illumination");
  assert.equal(failed.ok, false);
  assert(failed.detail.length > 0);
}
assert.equal(goal(baseline, "illumination", false).ok, false);

// Moving the brightness check must not allow a dark or overexposed final image.
for (const change of [{ exposure: 20, aperture: 55 }, { exposure: 300, aperture: 100 }, { aperture: 10 }, { cameraFocus: 20 }])
  assert.equal(goal({ ...baseline, ...change }, "camera").ok, false, JSON.stringify(change));
assert.equal(goal({ ...baseline, lampPosition: 10 }, "illumination").detail, "灯位 10，需调至 -5～5");
console.log("PASS: alignment boundaries, independent camera settings, actionable feedback and final image quality");
