import { opticalMetrics } from "./optics.js";

// Shared by guided adjustment and the independent challenge. Camera exposure
// must not change whether the lamp and half mirror are optically aligned.
export function adjustmentGoals(o, assembled) {
  const m = opticalMetrics({ ...o, observationMode: "camera" }, assembled);
  const illuminationIssues = [];
  if (!assembled) illuminationIssues.push("先完成组件安装");
  if (!o.lightOn) illuminationIssues.push("开启钠光灯");
  if (Math.abs(o.lampPosition) > 5)
    illuminationIssues.push(`灯位 ${o.lampPosition}，需调至 -5～5`);
  if (Math.abs(o.mirrorAngle - 45) > 2)
    illuminationIssues.push(`倾角 ${o.mirrorAngle}°，需调至 43°～47°`);
  return [
    {
      id: "signal", label: "CCD 供电、接线和屏幕", points: 10,
      ok: assembled && o.lightOn && o.cameraOn && o.videoConnected && o.monitorOn,
    },
    {
      id: "illumination", label: "光源与半反镜照明", points: 10,
      ok: illuminationIssues.length === 0,
      detail: illuminationIssues.join("；"),
    },
    {
      id: "reticle", label: "十字丝清晰", points: 10,
      ok: Math.abs(o.reticle - (o.reticleTarget ?? 50)) <= 3,
    },
    {
      id: "objective", label: "物镜高度与环纹焦点", points: 10,
      ok: Math.abs(o.focus - (o.focusTarget ?? 50)) <= 3,
    },
    {
      id: "reflection", label: "底座反光镜收起", points: 5,
      ok: o.baseMirrorFolded,
    },
    {
      id: "camera", label: "CCD 图像明亮、清晰且不过曝", points: 5,
      ok: Math.abs(o.cameraFocus - (o.cameraFocusTarget ?? 50)) <= 3 &&
        m.sharpness >= 0.65 && m.contrast >= 0.45 &&
        m.brightness >= 0.2 && m.throughput <= 1.35 && o.aperture >= 30,
    },
    {
      id: "centering", label: "暗斑与十字丝交点重合", points: 10,
      ok: Math.abs(o.reading - (o.ringOrigin ?? 25) - o.center * 0.02) <= 0.04,
    },
  ];
}
