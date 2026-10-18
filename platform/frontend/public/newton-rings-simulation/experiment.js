import {
  opticalMetrics,
  drawField,
  MEASURE_TARGETS,
  ringPosition,
  calculateRadius,
  videoSignal,
  DEFAULT_OPTICS,
  TUNING_KEYS,
} from "./optics.js";
const $ = (id) => document.getElementById(id);
export function updateField(state, lab) {
  const complete = lab.removed.size === 0;
  const m = drawField($("rings"), state.optics, complete);
  const signal = videoSignal(state.optics, !lab.removed.has("camera"));
  if (!signal.online && lab.removed.has("camera")) {
    const ctx = $("rings").getContext("2d");
    ctx.fillStyle = "#03090e";
    ctx.fillRect(0, 0, 480, 270);
    ctx.fillStyle = "#7c9daf";
    ctx.font = "14px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(signal.message, 240, 135);
    ctx.textAlign = "start";
  }
  lab.updateMonitor($("rings"));
  $("fieldStatus").textContent = !signal.online
    ? signal.message
    : !state.optics.lightOn
      ? "视频已接通 · 钠光灯未开启，当前为暗场"
      : m.ready
        ? "环纹清晰 · 亮度适中 · 可以读数"
        : m.issues[0] || "正在观察";
  for (const [id, value] of Object.entries({
    brightness: m.brightness,
    sharpness: m.sharpness,
    contrast: m.contrast,
  })) {
    if ($(id + "Metric")) {
      $(id + "Metric").textContent = signal.online
        ? Math.round(value * 100) + "%"
        : "—";
      $(id + "Bar").style.width =
        (signal.online ? Math.round(value * 100) : 0) + "%";
    }
  }
  if ($("liveDiagnosis"))
    $("liveDiagnosis").textContent = m.issues.length
      ? m.issues.join("；")
      : "光路、叉丝、环纹和相机成像均已调好。";
  for (const [id, key] of Object.entries({
    lampSwitch: "lightOn",
    cameraPowerSwitch: "cameraOn",
    videoCableSwitch: "videoConnected",
    monitorPowerSwitch: "monitorOn",
  }))
    if ($(id)) $(id).checked = !!state.optics[key];
  if ($("signalStatus")) {
    $("signalStatus").textContent = signal.message;
    $("signalStatus").classList.toggle("online", signal.online);
  }
  if ($("lightOn")) $("lightOn").checked = state.optics.lightOn;
  return m;
}
export function renderOptics(
  state,
  lab,
  { save, log, selectPart, render, toast },
) {
  const o = state.optics;
  const sliders = [
    ["lampPosition", "灯箱横向位置", -30, 30, "", "lamp"],
    ["mirrorAngle", "半反镜角度", 30, 60, "°", "mirrorKnob"],
    ["reticle", "目镜视度 / 分划板", 0, 100, "", "eyepiece"],
    ["focus", "调焦手轮 / 镜筒升降", 0, 100, "", "focus"],
    ["center", "牛顿环组件居中", -30, 30, "", "lens"],
    ["cameraFocus", "变焦镜头调焦", 0, 100, "", "zoom"],
    ["zoom", "变焦镜头焦距", 2.8, 12, " mm", "zoom"],
    ["aperture", "镜头孔径开度", 10, 100, "%", "zoom"],
    ["exposure", "相机曝光（相对）", 20, 300, "%", "camera"],
    ["lampPower", "照明强度（相对）", 20, 100, "%", "lamp"],
  ];
  $("guideContent").innerHTML =
    `<div class="light-path"><b>照明与成像的顺序</b><p><i class="yellow"></i> 钠光 → 半反镜 → 空气膜<br><i class="green"></i> 两界面反射光 → 物镜 → 棱镜室<br><i class="purple"></i> 45° 斜视端 → 变焦镜头 → 相机<br>相机光电转换 → HDMI/USB → 显示器</p><button id="viewFilm">放大空气膜剖面</button></div><label class="switch-row">钠光灯电源<input id="lightOn" type="checkbox" ${o.lightOn ? "checked" : ""}></label><label class="switch-row">底座大反光镜向内收起<input id="baseMirrorFolded" type="checkbox" ${o.baseMirrorFolded ? "checked" : ""}></label><label class="switch-row">透视镜筒查看内部光路<input id="xray" type="checkbox" ${o.xray ? "checked" : ""}></label>` +
    sliders
      .map(
        ([id, label, min, max, unit]) =>
          `<label class="control-row"><span>${label}<output id="${id}Value">${o[id]}${unit}</output></span><input id="${id}" type="range" min="${min}" max="${max}" step="${id === "zoom" ? 0.1 : 1}" value="${o[id]}"></label>`,
      )
      .join("") +
    `<div class="guide-card"><b>各部件分别影响什么？</b><p>灯位、半反镜：光路方向与照明均匀性。<br>底座反光镜：背景杂散光。<br>目镜视度：叉丝清晰度。<br>调焦手轮：环纹成像。<br>相机镜头：图像清晰度与视场大小。<br>孔径、曝光：亮度与高光细节。</p></div><p class="feedback" id="liveDiagnosis"></p><button class="operation primary" id="checkOptics">检查装调结果</button><p class="feedback">视度、镜筒与相机调焦以 50 为教学参考；灯位与居中以 0 为参考；半反镜约 45°。孔径、曝光按实际视场反馈调节。</p>`;
  for (const id of ["lightOn", "baseMirrorFolded", "xray"])
    $(id).onchange = (e) => {
      o[id] = e.target.checked;
      if (id === "baseMirrorFolded") state.manualOpticsTouched = true;
      lab.optics(o);
      updateField(state, lab);
      log("调节 " + id);
      save();
    };
  for (const [id, , , , unit, part] of sliders)
    $(id).oninput = (e) => {
      o[id] = +e.target.value;
      state.manualOpticsTouched = true;
      $(id + "Value").textContent = e.target.value + unit;
      selectPart(part, false);
      lab.optics(o);
      updateField(state, lab);
      save();
    };
  $("viewFilm").onclick = () => {
    lab.view("film");
    $("sceneSubtitle").textContent =
      "空气膜上下界面反射 · 膜厚与光线间距放大示意";
  };
  const resetTuning = document.createElement("button");
  resetTuning.id = "resetTuning";
  resetTuning.className = "operation";
  resetTuning.textContent = "恢复待装调状态";
  resetTuning.onclick = () => {
    for (const key of TUNING_KEYS) o[key] = DEFAULT_OPTICS[key];
    state.aligned = false;
    state.manualOpticsTouched = false;
    state.opticsInteractionVersion = 1;
    lab.optics(o);
    log("恢复待装调状态：保留接线与供电");
    save();
    render();
    toast("光学部件已恢复待调位置，接线与供电保持当前状态。");
  };
  $("guideContent").append(resetTuning);
  $("checkOptics").onclick = () => {
    const m = opticalMetrics(o, lab.removed.size === 0);
    if (m.ready) {
      state.aligned = true;
      state.alignmentSnapshot = { ...o };
      log("光路与相机成像检查通过");
      render();
      toast("装调通过，可进行同向暗环读数。");
    } else {
      log("装调检查未通过", null, false);
      toast(m.issues[0]);
    }
    save();
  };
  updateField(state, lab);
}
export function renderMeasurement(state, lab, { save, log, render, toast }) {
  const i = state.readings.length,
    target = MEASURE_TARGETS[i],
    o = state.optics;
  $("guideContent").innerHTML =
    `<div class="guide-card active"><b>${target ? `第 ${i + 1}/8 点：${target.side === "left" ? "左" : "右"}侧第 ${target.order} 级暗环` : "8 个暗环位置已记录"}</b><p>保持从左向右移动。纵丝与指定暗环相切时记录。主尺加鼓轮读数，分辨率 0.01 mm。</p></div><label class="control-row"><span>测微鼓轮读数<output id="readingValue">${o.reading.toFixed(2)} mm</output></span><input id="reading" type="range" min="21" max="29" step=".01" value="${o.reading}"></label><div class="reading-buttons"><button id="minusReading">− 0.01</button><button id="plusReading">+ 0.01</button></div>${target ? '<button class="operation" id="demoPosition">示范定位到当前暗环</button><button class="operation primary" id="recordReading">记录当前读数</button>' : ""}<table class="readings-table"><thead><tr><th>环级</th><th>左 / mm</th><th>右 / mm</th></tr></thead><tbody>${[5, 10, 15, 20].map((order) => `<tr><td>${order}</td><td>${state.readings.find((r) => r.order === order && r.side === "left")?.reading.toFixed(2) || "—"}</td><td>${state.readings.find((r) => r.order === order && r.side === "right")?.reading.toFixed(2) || "—"}</td></tr>`).join("")}</tbody></table>${i === 8 ? '<button class="operation primary" id="calculateR">计算 6 组曲率半径</button>' : ""}${state.measurement ? `<div class="guide-card active"><b>平均 R = ${state.measurement.radius.toFixed(1)} mm</b><p>由 4 个直径形成 6 组 |m−n|≥5 的组合。厂家标称透镜 R=1000 mm；读数按 0.01 mm 量化。</p></div>` : ""}<button class="operation" id="restartReadings">清空读数，重新同向测量</button><p class="feedback">示范定位帮助理解环级，使用次数会记录在报告中。实际实验应自行辨认暗环。</p>`;
  function setReading(v) {
    o.reading = Math.round(Math.min(29, Math.max(21, v)) * 100) / 100;
    $("reading").value = o.reading;
    $("readingValue").textContent = o.reading.toFixed(2) + " mm";
    lab.optics(o);
    updateField(state, lab);
    save();
  }
  $("reading").oninput = (e) => setReading(+e.target.value);
  $("minusReading").onclick = () => setReading(o.reading - 0.01);
  $("plusReading").onclick = () => setReading(o.reading + 0.01);
  if (target) {
    $("demoPosition").onclick = () => {
      setReading(ringPosition(target.order, target.side));
      state.guidedReadings++;
      log("示范定位暗环");
    };
    $("recordReading").onclick = () => {
      const m = opticalMetrics(o, lab.removed.size === 0);
      if (!m.ready) {
        toast("请先调好照明与成像。");
        return;
      }
      const last = state.readings.at(-1);
      if (last && o.reading < last.reading) {
        toast("当前读数小于上一记录，请沿原方向继续。");
        log("读数返向", null, false);
        return;
      }
      if (
        Math.abs(o.reading - ringPosition(target.order, target.side)) > 0.025
      ) {
        toast("纵丝还未与指定暗环相切，请微调鼓轮。");
        return;
      }
      state.readings.push({ ...target, reading: o.reading });
      log("记录暗环 " + target.order + " " + target.side);
      save();
      render();
    };
  }
  if (i === 8)
    $("calculateR").onclick = () => {
      state.measurement = calculateRadius(state.readings);
      log("完成曲率半径计算");
      save();
      render();
    };
  $("restartReadings").onclick = () => {
    state.readings = [];
    state.measurement = null;
    o.reading = 21;
    lab.optics(o);
    log("重新测量暗环");
    render();
  };
  updateField(state, lab);
}
