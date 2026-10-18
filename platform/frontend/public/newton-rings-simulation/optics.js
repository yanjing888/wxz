// Teaching transfer model: fringe geometry is physical; brightness/blur are relative,
// not a calibrated photometric or full wave-optics model of the manufacturer's lens.
export const LAMBDA_MM = 0.0005893;
export const RADIUS_MM = 1000; // Manufacturer packing list, PDF page 19.
export const DEFAULT_OPTICS = {
  lightOn: false,
  cameraOn: false,
  videoConnected: false,
  monitorOn: false,
  lampPower: 80,
  lampPosition: 10,
  mirrorAngle: 42,
  reticle: 35,
  focus: 32,
  center: 10,
  baseMirrorFolded: false,
  cameraFocus: 35,
  aperture: 55,
  exposure: 90,
  zoom: 6,
  reading: 25,
  showRays: true,
  xray: false,
};
export const IDEAL_OPTICS = {
  lightOn: true,
  cameraOn: true,
  videoConnected: true,
  monitorOn: true,
  lampPower: 100,
  lampPosition: 0,
  mirrorAngle: 45,
  reticle: 50,
  focus: 50,
  center: 0,
  baseMirrorFolded: true,
  cameraFocus: 50,
  aperture: 70,
  exposure: 100,
  zoom: 6,
  reading: 25,
  showRays: true,
  xray: true,
};
export const TUNING_KEYS = [
  "lampPower",
  "lampPosition",
  "mirrorAngle",
  "reticle",
  "focus",
  "center",
  "baseMirrorFolded",
  "cameraFocus",
  "aperture",
  "exposure",
  "zoom",
];
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export function videoSignal(o, cameraMounted = true) {
  if(o.observationMode==='eyepiece')return {online:true,message:'目镜观察 · 十字丝固定，环纹随鼓轮移动',action:''};
  if (!o.monitorOn)
    return {
      online: false,
      message: "示教显示器未开启",
      action: "打开“显示器电源”",
    };
  if (!cameraMounted)
    return {
      online: false,
      message: "CCD 相机尚未安装",
      action: "先完成相机装配",
    };
  if (!o.cameraOn)
    return {
      online: false,
      message: "CCD 相机未供电",
      action: "打开“CCD 相机电源”",
    };
  if (!o.videoConnected)
    return {
      online: false,
      message: "无视频信号：视频线未连接",
      action: "连接“CCD → 示教屏幕视频线”",
    };
  return {
    online: true,
    message: "CCD → HDMI/USB → 显示器：信号已接通",
    action: "",
  };
}
export function opticalMetrics(o, assembled = true) {
  const cameraMode=o.observationMode!=='eyepiece';
  const focusError=o.focus-(o.focusTarget??50);
  const reticleError=o.reticle-(o.reticleTarget??50);
  const cameraError=cameraMode?o.cameraFocus-(o.cameraFocusTarget??50):0;
  const aperture=cameraMode?o.aperture:70;
  const alignment = Math.exp(
    -(((o.mirrorAngle - 45) / 6) ** 2 + (o.lampPosition / 19) ** 2),
  );
  const throughput =
    (o.lightOn && assembled ? 1 : 0) *
    (o.lampPower / 100) *
    alignment *
    (aperture / 70) ** 2 *
    (cameraMode?o.exposure / 100:1);
  const stray = o.baseMirrorFolded ? 0.015 : 0.22;
  const ringBlur =
    Math.abs(focusError) * 0.11 * (0.6 + aperture / 100) +
    Math.abs(cameraError) * 0.09 +
    Math.max(0, 25 - aperture) * 0.1;
  const reticleBlur =
    Math.abs(reticleError) * 0.05 + Math.abs(cameraError) * 0.08;
  const brightness = clamp(throughput * (0.5 + stray));
  const sharpness = clamp(Math.exp(-ringBlur * 0.48));
  const contrast = clamp(
    ((0.92 / (1 + stray * 5)) * sharpness) /
      (1 + Math.max(0, throughput - 1.1) * 0.9),
  );
  const issues = [];
  const signal = videoSignal(o);
  if (!signal.online) issues.push(signal.message);
  if (!assembled) issues.push("光学组件尚未复原");
  if (!o.lightOn) issues.push("钠光灯未开启");
  if (Math.abs(o.lampPosition) > 5) issues.push("光源位置偏移：照明不均匀");
  if (Math.abs(o.mirrorAngle - 45) > 2)
    issues.push("半反镜偏转：入射光未对准空气膜");
  if (!o.baseMirrorFolded) issues.push("底座大反光镜未收起：背景杂散光增加");
  if (Math.abs(reticleError) > 5) issues.push("目镜视度未调清叉丝");
  if (Math.abs(focusError) > 5) issues.push("镜筒离焦：环纹模糊");
  if (Math.abs(cameraError) > 5)
    issues.push("变焦镜头离焦：相机图像模糊");
  if (Math.abs(o.center) > 4) issues.push("组件未居中：测量路径可能偏离直径");
  if (brightness < 0.2) issues.push("成像偏暗：检查照明、孔径和曝光");
  if (throughput > 1.35) issues.push("高光过曝：缩短曝光或适当收小孔径");
  if (aperture < 30) issues.push("孔径过小：通光量降低且细节变软");
  return {
    alignment,
    throughput,
    stray,
    ringBlur,
    reticleBlur,
    brightness,
    sharpness,
    contrast,
    issues,
    ready: issues.length === 0,
  };
}
export function ringPosition(order, side) {
  return (
    25 + (side === "left" ? -1 : 1) * Math.sqrt(order * LAMBDA_MM * RADIUS_MM)
  );
}
export const MEASURE_TARGETS = [
  ...[20, 15, 10, 5].map((order) => ({ order, side: "left" })),
  ...[5, 10, 15, 20].map((order) => ({ order, side: "right" })),
];
export function calculateRadius(records) {
  const diameters = [5, 10, 15, 20].map((order) => {
    const l = records.find((r) => r.order === order && r.side === "left"),
      r = records.find((r) => r.order === order && r.side === "right");
    return { order, diameter: r.reading - l.reading };
  });
  const pairs = [];
  for (let i = 0; i < diameters.length; i++)
    for (let j = i + 1; j < diameters.length; j++) {
      const n = diameters[i],
        m = diameters[j];
      pairs.push({
        m: m.order,
        n: n.order,
        radius:
          (m.diameter ** 2 - n.diameter ** 2) /
          (4 * (m.order - n.order) * LAMBDA_MM),
      });
    }
  return {
    diameters,
    pairs,
    radius: pairs.reduce((sum, p) => sum + p.radius, 0) / pairs.length,
  };
}
export function drawField(canvas, o, assembled = true) {
  const ctx = canvas.getContext("2d"),
    w = canvas.width,
    h = canvas.height,
    m = opticalMetrics(o, assembled),
    image = new OffscreenCanvas(w, h),
    ic = image.getContext("2d"),
    data = ic.createImageData(w, h),
    cx = w / 2,
    cy = h / 2,
    rad = h * 0.47,
    mmPerPixel = (2.4 * 6) / o.zoom / rad;
  const signal = videoSignal(o);
  if (!signal.online) {
    ctx.filter = "none";
    ctx.fillStyle = "#03090e";
    ctx.fillRect(0, 0, w, h);
    ctx.textAlign = "center";
    ctx.fillStyle = "#7c9daf";
    ctx.font = "14px sans-serif";
    ctx.fillText(signal.message, w / 2, h / 2 - 6);
    ctx.fillStyle = "#52768b";
    ctx.font = "11px sans-serif";
    ctx.fillText(signal.action, w / 2, h / 2 + 20);
    ctx.textAlign = "start";
    return m;
  }
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const k = (y * w + x) * 4,
        rr = ((x - cx) ** 2 + (y - cy) ** 2) / rad ** 2;
      data.data[k + 3] = 255;
      if (rr > 1) continue;
      const px = (x - cx) * mmPerPixel + (o.reading - (o.ringOrigin??25)) - o.center * 0.02,
        py = (y - cy) * mmPerPixel;
      const phase = (Math.PI * (px * px + py * py)) / (LAMBDA_MM * RADIUS_MM);
      const illumination = clamp(
        1 +
          ((x - cx) / rad) * (o.lampPosition / 42 + (o.mirrorAngle - 45) / 24),
        0.05,
        1.5,
      );
      const raw =
        (Math.sin(phase) ** 2 * 0.88 + m.stray) *
          m.throughput *
          illumination *
          (1 - rr * 0.25) +
        (Math.random() - 0.5) * 0.006;
      data.data[k] = clamp(raw) * 255;
      data.data[k + 1] = clamp(raw * 0.76) * 255;
      data.data[k + 2] = clamp(raw * 0.2) * 255;
    }
  ic.putImageData(data, 0, 0);
  ctx.fillStyle = "#02070b";
  ctx.fillRect(0, 0, w, h);
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, rad, 0, Math.PI * 2);
  ctx.clip();
  ctx.filter = `blur(${m.ringBlur}px)`;
  ctx.drawImage(image, 0, 0);
  ctx.filter = `blur(${m.reticleBlur}px)`;
  ctx.strokeStyle = o.lightOn ? "#c4dbcfb0" : "#30404d";
  ctx.lineWidth = 0.8;
  ctx.beginPath();
  ctx.moveTo(cx - rad, cy);
  ctx.lineTo(cx + rad, cy);
  ctx.moveTo(cx, cy - rad);
  ctx.lineTo(cx, cy + rad);
  ctx.stroke();
  ctx.restore();
  ctx.filter = "none";
  ctx.fillStyle = "#81a3b8";
  ctx.font = "10px monospace";
  ctx.fillText(o.observationMode==='eyepiece'?'EYEPIECE · RETICLE FIXED':`HDMI / USB · 16:9 · ${o.zoom.toFixed(1)} mm`, 10, h - 9);
  return m;
}
