import { INSTALL_UNITS } from "./student-course.js";
import { adjustmentGoals } from "./student-adjustment.js";

// Keep saved stage IDs and assessment rules stable; installation and adjustment
// are now two continuous parts of the same practice entry.
export const PRACTICE_NAV = [
  { name: "仿真练习", stage: 0 },
  { name: "预习测验", stage: 2 },
  { name: "成像挑战", stage: 3 },
  { name: "预习报告", stage: 4 },
];
export const ADJUSTMENT_STEPS = {
  signal: { title: "接通观察设备", instruction: "打开电源，连接视频线。", unit: "camera", action: "连接设备" },
  illumination: { title: "调节照明", instruction: "左右拖动钠光灯，转动半反镜，让视场亮起来。", unit: "mirror" },
  reticle: { title: "调清十字丝", instruction: "左右拖动相机下方的目镜调焦环。", unit: "eyepiece" },
  objective: { title: "调清环纹", instruction: "上下拖动物镜，观察环纹是否清晰。", unit: "objective" },
  reflection: { title: "收起底座反光镜", instruction: "点击底座前侧的小旋钮，将反光镜转向内。", unit: "baseMirror" },
  camera: { title: "调整相机成像", instruction: "拖动镜头调焦、机身调曝光，观察环纹。", unit: "camera" },
  centering: { title: "对准环心", instruction: "左右拖动测微鼓轮，让暗斑与十字丝交点重合。", unit: "drum" },
};
export function practiceTask(state) {
  const current = INSTALL_UNITS.find((u) => !state.installed.includes(u.id));
  if (current) return {
    key: current.id, unit: current.id, title: `安装${current.name}`,
    instruction: "点击高亮组件，将它装到仪器上。", action: "安装组件",
    number: state.installed.length + 1, total: 13, kind: "install",
  };
  const checks = adjustmentGoals(state.optics, true);
  const index = checks.findIndex((g) => !g.ok);
  if (index !== -1) return {
    ...ADJUSTMENT_STEPS[checks[index].id], key: checks[index].id,
    number: INSTALL_UNITS.length + index + 1, total: 13, kind: "adjust",
    action: ADJUSTMENT_STEPS[checks[index].id].action || "定位部件",
  };
  return {
    key: "ready", title: state.guidePassed ? "练习已完成" : "成像已调好",
    instruction: "", action: state.guidePassed ? "进入测验" : "完成练习",
    number: 13, total: 13, kind: "ready",
  };
}
