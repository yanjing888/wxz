import { createLab } from "./model.js";
import {
  DEFAULT_OPTICS,
  drawField,
  videoSignal,
} from "./optics.js";
import {
  COURSE_VERSION,
  UNITS,
  INSTALL_UNITS,
  UNIT_BY_ID,
  RAW_TO_UNIT,
  STAGES,
  CONTROLS,
  QUESTIONS,
  gradeAnswer,
  makeChallenge,
} from "./student-course.js";
import { openReport, downloadReport, downloadData } from "./student-report.js";
import { adjustmentGoals } from "./student-adjustment.js";
const $ = (id) => document.getElementById(id),
  storageKey = `wxz-newton-student-v3:${localStorage.getItem("wxz_userId") || "guest"}`;
const fresh = () => ({
  version: COURSE_VERSION,
  stage: 0,
  installed: [],
  selected: "sample",
  guidePassed: false,
  guideSnapshot: null,
  answers: [],
  quizCursor: 0,
  quizDone: false,
  draft: [],
  challenge: null,
  actions: [],
  startedAt: Date.now(),
  name: localStorage.getItem("wxz_displayName") || "",
  optics: {
    ...DEFAULT_OPTICS,
    center: 0,
    reading: 23.9,
    ringOrigin: 25,
    focusTarget: 50,
    reticleTarget: 50,
    cameraFocusTarget: 50,
    observationMode: "unavailable",
  },
});
let state = fresh();
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (
    saved?.version === COURSE_VERSION &&
    Array.isArray(saved.installed) &&
    Array.isArray(saved.answers)
  ) {
    state = {
      ...state,
      ...saved,
      optics: { ...state.optics, ...saved.optics },
    };
    state.installed = state.installed.filter((id) =>
      INSTALL_UNITS.some((u) => u.id === id),
    );
  }
} catch {}
let lab,
  busy = false,
  explodedPreview = false,
  toastTimer,
  labels = true,
  notified = false;
const monitorBuffer = document.createElement("canvas");
monitorBuffer.width = 480;
monitorBuffer.height = 270;
function save() {
  try {
    localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {}
}
function log(action, detail = {}) {
  state.actions.push({ at: new Date().toISOString(), action, ...detail });
  save();
}
function toast(text) {
  $("toast").textContent = text;
  $("toast").classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $("toast").classList.remove("visible"), 3000);
}
const installed = (id) =>
  !UNIT_BY_ID[id]?.install || state.installed.includes(id);
const allInstalled = () => state.installed.length === INSTALL_UNITS.length;
const inChallenge = () => state.stage === 3 && !!state.challenge;
function centered() {
  return (
    Math.abs(
      state.optics.reading -
        (state.optics.ringOrigin ?? 25) -
        state.optics.center * 0.02,
    ) <= 0.04
  );
}
function goals() {
  return adjustmentGoals(state.optics, allInstalled());
}
function challengePassed() {
  return !!state.challenge?.result?.passed;
}
function quizScore() {
  return state.answers.filter((a) => a.correct).length * 5;
}
function totalScore() {
  return quizScore() + (state.challenge?.result?.score || 0);
}
function passed() {
  return (
    allInstalled() &&
    state.guidePassed &&
    state.quizDone &&
    challengePassed() &&
    totalScore() >= 80
  );
}
function allowed(stage) {
  return (
    stage === 0 ||
    (stage === 1 && allInstalled()) ||
    (stage === 2 && state.guidePassed) ||
    (stage === 3 && state.quizDone) ||
    (stage === 4 && !!state.challenge?.result)
  );
}
function completed(stage) {
  return [
    allInstalled(),
    state.guidePassed,
    state.quizDone,
    challengePassed(),
    passed(),
  ][stage];
}
function selectUnit(raw, context = {}) {
  const id = UNIT_BY_ID[raw] ? raw : RAW_TO_UNIT[raw];
  if (!id) return;
  state.selected = id;
  const unit = UNIT_BY_ID[id];
  lab?.select(unit.model);
  $("partName").textContent = unit.name;
  $("partReference").textContent = unit.install
    ? installed(id)
      ? "已安装"
      : "待安装"
    : "已装调节机构";
  $("partDescription").textContent = unit.description;
  $("partTip").textContent = explodedPreview
    ? "旋转或缩放观察组件位置；点击“返回装配图”继续操作。"
    : inChallenge()
    ? "请独立观察视场进行调整。本轮不提供最佳位置提示。"
    : unit.tip;
  $("partsList")
    .querySelectorAll("[data-unit]")
    .forEach((b) => b.classList.toggle("active", b.dataset.unit === id));
  renderControl();
  if (
    !explodedPreview &&
    context.source === "model" &&
    id === "baseMirror" &&
    state.stage !== 2 &&
    state.stage !== 4 &&
    installed(id)
  ) {
    change("baseMirrorFolded", !state.optics.baseMirrorFolded, "模型点击");
    renderControl();
  }
  save();
}
function change(key, value, source = "控件") {
  if (busy || explodedPreview || state.stage === 2 || state.stage === 4) return;
  state.optics[key] = value;
  if (state.stage === 1) state.guidePassed = false;
  if (inChallenge()) {
    state.challenge.edits++;
    if (!state.challenge.touched.includes(key))
      state.challenge.touched.push(key);
  }
  log("调节", { key, value, source });
  lab.optics(state.optics);
  paint();
  syncControls();
  renderProgress();
}
function directControl(raw) {
  const id = UNIT_BY_ID[raw] ? raw : RAW_TO_UNIT[raw];
  if (
    ["power", "cameraPower", "cameraVideo", "monitor", "baseMirror"].includes(
      raw,
    )
  )
    return null;
  const key =
    raw === "cameraExposure" || raw === "camera"
      ? "exposure"
      : raw === "cameraAperture"
        ? "aperture"
        : null;
  return key ? CONTROLS.camera.find((c) => c.key === key) : CONTROLS[id]?.[0];
}
const gestures = {
  sample: "左右拖动夹座调整位置",
  objective: "上下拖动物镜或调焦手轮",
  mirror: "拖动半反镜或旋轮改变角度",
  lamp: "左右拖动调灯位 · 单击开关灯",
  eyepiece: "CCD 相机正下方：左右拖动黑色调焦环",
  camera: "拖动镜头下部调焦 / 蓝色机身调曝光 / 镜头中间刻线环调孔径",
  baseMirror: "点击底座前侧青绿色圆环内的小旋钮，翻转反光镜",
  drum: "左右拖动鼓轮 · 单击正转 · Shift 单击反转",
};
let activeGesture = null,
  activeRaw = "";
function interact(raw, event) {
  const id = UNIT_BY_ID[raw] ? raw : RAW_TO_UNIT[raw];
  if (!id || busy || explodedPreview || state.stage === 2 || state.stage === 4) return false;
  if (!installed(id)) {
    if (state.stage !== 0) return false;
    if (event.phase === "start") {
      selectUnit(id);
      activeGesture = null;
      return true;
    }
    if (event.phase === "click") installCurrent();
    return true;
  }
  const c = directControl(raw),
    action = {
      power: "lightOn",
      lamp: "lightOn",
      camera: "cameraOn",
      cameraPower: "cameraOn",
      cameraVideo: "videoConnected",
      monitor: "monitorOn",
      baseMirror: "baseMirrorFolded",
    }[raw];
  if (!c && !action) return false;
  if (event.phase === "hover") {
    if (raw === "eyepiece" || raw === "baseMirror") showManipulation(raw);
    return true;
  }
  if (event.phase === "start") {
    activeRaw = raw;
    activeGesture = { control: c, initial: c ? state.optics[c.key] : 0 };
    selectUnit(id);
    showManipulation(raw);
    return true;
  }
  if (event.phase === "click") {
    if (action) change(action, !state.optics[action], "模型点击");
    else if (c && id !== "mirror")
      stepControl(c, event.shiftKey ? -1 : 1, "模型点击");
  }
  if (event.phase === "drag") {
    if (c && activeGesture?.control === c) {
      const distance =
        c.key === "focus" ? -event.dy : event.dx - event.dy * 0.5;
      const sensitivity = {
        reading: 0.01,
        mirrorAngle: 0.1,
        lampPosition: 0.2,
        center: 0.2,
        exposure: 1,
        reticle: 0.4,
        focus: 0.4,
        cameraFocus: 0.4,
        aperture: 0.4,
      }[c.key];
      const value =
        Math.round(
          (activeGesture.initial +
            distance * sensitivity * (event.shiftKey ? 0.2 : 1)) /
            c.step,
        ) * c.step;
      const limited = Number(
        Math.max(c.min, Math.min(c.max, value)).toFixed(2),
      );
      if (limited !== state.optics[c.key]) change(c.key, limited, "模型拖动");
    } else if (raw === "baseMirror" && Math.hypot(event.dx, event.dy) > 8) {
      const folded = event.dx - event.dy > 0;
      if (folded !== state.optics.baseMirrorFolded)
        change("baseMirrorFolded", folded, "模型拖动");
    }
  }
  if (event.phase === "end" || event.phase === "cancel") activeGesture = null;
  showManipulation(raw);
  return true;
}
function stepControl(c, direction, source, event = {}) {
  const step = c.key === "reading" ? (event.shiftKey ? 0.1 : 0.02) : c.step;
  change(
    c.key,
    Number(
      Math.max(
        c.min,
        Math.min(c.max, state.optics[c.key] + direction * step),
      ).toFixed(2),
    ),
    source,
  );
}
function showManipulation(raw = activeRaw) {
  const host = $("modelInteraction");
  if (!host) return;
  const id = UNIT_BY_ID[raw] ? raw : RAW_TO_UNIT[raw],
    c = directControl(raw);
  const specials = {
    power: "点击模型电源盒开关钠光灯",
    cameraPower: "点击开关 CCD 电源",
    cameraVideo: "点击蓝色视频线连接 / 拔下",
    monitor: "点击示教屏幕开关电源",
  };
  host.textContent = id
    ? `${UNIT_BY_ID[id].name} · ${specials[raw] || gestures[id]}${c ? " · " + c.label + " " + (c.key === "reading" ? state.optics[c.key].toFixed(2) : state.optics[c.key]) + (c.unit || "") : ""}`
    : "";
}
function adjust(raw, direction, event) {
  const id = UNIT_BY_ID[raw] ? raw : RAW_TO_UNIT[raw],
    control = directControl(raw);
  if (
    explodedPreview ||
    !control ||
    !installed(id) ||
    state.stage === 2 ||
    state.stage === 4 ||
    busy
  )
    return false;
  // A drag uses the value at pointerdown. Do not mix wheel edits into that
  // gesture, or its final pointerup would overwrite the wheel adjustment.
  if (activeGesture) return true;
  if (state.selected !== id) selectUnit(id);
  activeRaw = raw;
  stepControl(control, direction, "模型滚轮", event);
  showManipulation(raw);
  return true;
}
function renderControl() {
  const id = state.selected,
    host = $("componentControl");
  host.innerHTML =
    !explodedPreview && installed(id) && state.stage !== 2 && state.stage !== 4
      ? `<section class="component-editor"><h3>${UNIT_BY_ID[id].name} · 在模型上操作</h3><p>${gestures[id]}</p>${["mirror", "lamp"].includes(id) ? CONTROLS[id].map((c) => `<label class="adjustment-value">${c.label}<span><input data-optics-key="${c.key}" aria-label="${c.label}" type="number" min="${c.min}" max="${c.max}" step="${c.step}" value="${state.optics[c.key]}">${c.unit || ""}</span></label>`).join("") : ""}<p class="wheel-hint">${id === "mirror" ? "单击选中；拖动或滚轮调节角度，Shift + 拖动精调。" : "直接按住模型拖动；滚轮微调。单击可小步调节，Shift + 单击反向，Shift + 拖动精调。"}</p></section>`
      : "";
  host.querySelectorAll("[data-optics-key]").forEach((input) => {
    input.onchange = () => {
      const c = CONTROLS[id].find((c) => c.key === input.dataset.opticsKey);
      if (input.value.trim() && Number.isFinite(input.valueAsNumber)) {
        const value = Number((Math.round(Math.max(c.min, Math.min(c.max, input.valueAsNumber)) / c.step) * c.step).toFixed(2));
        change(c.key, value, "数值调节");
        showManipulation(id);
      }
      input.value = state.optics[c.key];
    };
  });
}
function syncControls() {
  $("componentControl").querySelectorAll("[data-optics-key]").forEach((input) => {
    if (input !== document.activeElement)
      input.value = state.optics[input.dataset.opticsKey];
  });
  $("raysToggle").textContent = state.optics.showRays
    ? "显示光路 ✓"
    : "显示光路";
  $("raysToggle").setAttribute("aria-pressed", state.optics.showRays);
  $("xrayToggle").setAttribute("aria-pressed", state.optics.xray);
  for (const [id, key, unit] of [
    ["lampSwitch", "lightOn", "lamp"],
    ["cameraPowerSwitch", "cameraOn", "camera"],
    ["videoCableSwitch", "videoConnected", "camera"],
    ["monitorPowerSwitch", "monitorOn", null],
  ]) {
    $(id).checked = !!state.optics[key];
    $(id).disabled =
      explodedPreview || (!!unit && !installed(unit)) || state.stage === 2 || state.stage === 4;
  }
}
function paint() {
  if (!lab) return;
  if (explodedPreview) {
    drawField($("rings"), { ...state.optics, observationMode: "unavailable" }, false);
    lab.updateMonitor($("rings"));
    $("observationMode").textContent = "爆炸图观察";
    $("fieldStatus").textContent = "返回装配图后继续观察成像";
    $("signalStatus").textContent = "正在查看组件结构，安装进度已保留";
    $("signalStatus").classList.remove("online");
    for (const key of ["brightness", "sharpness", "contrast"])
      $(key + "Metric").textContent = "—";
    syncControls();
    return;
  }
  const opticalAssembly = ["sample", "objective", "mirror", "lamp"].every(
    installed,
  );
  const mode = installed("camera")
    ? "camera"
    : installed("eyepiece")
      ? "eyepiece"
      : "unavailable";
  if (state.optics.observationMode !== mode) {
    state.optics.observationMode = mode;
    lab.optics(state.optics);
  }
  const m = drawField($("rings"), state.optics, opticalAssembly),
    signal = videoSignal(state.optics, installed("camera"));
  if (mode === "unavailable") {
    const ctx = $("rings").getContext("2d");
    ctx.fillStyle = "#03090e";
    ctx.fillRect(0, 0, 480, 270);
    ctx.fillStyle = "#769bad";
    ctx.font = "14px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("安装目镜后可目视观察，安装 CCD 后可示教观察", 240, 135);
    ctx.textAlign = "start";
  }
  if (mode === "camera") lab.updateMonitor($("rings"));
  else {
    drawField(
      monitorBuffer,
      { ...state.optics, observationMode: "camera", cameraOn: false },
      false,
    );
    lab.updateMonitor(monitorBuffer);
  }
  $("observationMode").textContent =
    mode === "camera"
      ? "CCD 示教视场"
      : mode === "eyepiece"
        ? "目镜视场"
        : "等待安装观察模块";
  const visible = mode !== "unavailable" && signal.online;
  $("fieldStatus").textContent =
    mode === "unavailable"
      ? "请继续按步骤安装"
      : !signal.online
        ? signal.message
        : !opticalAssembly
          ? "光路尚未接通，继续安装光学组件"
          : !state.optics.lightOn
            ? "光源未开启 · 当前为暗场"
            : inChallenge()
              ? "独立调节中 · 以当前图像为准"
              : m.ready && centered()
                ? "环纹清晰、均匀、居中"
                : m.issues[0] || "转动测微鼓轮，让暗斑对准十字交点";
  $("signalStatus").textContent =
    mode === "eyepiece"
      ? "当前目视观察；安装 CCD 后再连接示教屏幕"
      : !installed("camera")
        ? "CCD 未安装，显示器暂无视频信号"
        : videoSignal({ ...state.optics, observationMode: "camera" }).message;
  $("signalStatus").classList.toggle(
    "online",
    mode === "camera" && signal.online,
  );
  for (const [key, value] of Object.entries({
    brightness: m.brightness,
    sharpness: m.sharpness,
    contrast: m.contrast,
  }))
    $(key + "Metric").textContent = visible
      ? Math.round(value * 100) + "%"
      : "—";
  if ($("guideGoals"))
    $("guideGoals").innerHTML = goals()
      .map(
        (g) =>
          `<li class="${g.ok ? "passed" : ""}">${g.ok ? "✓" : "○"} ${g.label}${g.detail ? " · " + g.detail : ""}</li>`,
      )
      .join("");
  if (inChallenge() && $("challengeEdits"))
    $("challengeEdits").textContent =
      `已调节 ${state.challenge.edits} 次 · 涉及 ${state.challenge.touched.length} 项控制`;
  syncControls();
}
function renderProgress() {
  $("assemblyStatus").textContent =
    `安装组件 ${state.installed.length} / ${INSTALL_UNITS.length}`;
  const next = $("nextStage");
  next.hidden = state.stage === 4;
  next.textContent =
    ["进入引导调节 →", "进入预习测验 →", "进入成像挑战 →", "查看结果报告 →"][
      state.stage
    ] || "";
  next.disabled = ![
    allInstalled(),
    state.guidePassed,
    state.quizDone,
    !!state.challenge?.result,
  ][state.stage];
  const fraction = [
    state.installed.length / 6,
    goals().filter((g) => g.ok).length / 7,
    state.answers.length / QUESTIONS.length,
    state.challenge?.result?.score / 60 || 0,
    passed() ? 1 : 0,
  ][state.stage];
  $("progressBar").style.width = Math.min(100, fraction * 100) + "%";
  $("progressText").textContent = [
    `已安装 ${state.installed.length}/6 个组件`,
    state.guidePassed ? "引导装调已通过" : "调好视场后，提交装调检查",
    `预习题 ${state.answers.length}/${QUESTIONS.length} · ${quizScore()}/40 分`,
    state.challenge?.result
      ? `本轮挑战 ${state.challenge.result.score}/60 分`
      : "独立调整后提交当前成像",
    `总分 ${totalScore()}/100`,
  ][state.stage];
}
function renderDock() {
  const expected = INSTALL_UNITS.find((u) => !state.installed.includes(u.id));
  $("partsList").innerHTML = UNITS.map(
    (u) =>
      `<button class="part-card ${installed(u.id) ? "installed" : "pending"} ${u.id === state.selected ? "active" : ""} ${state.stage === 0 && u.id === expected?.id ? "current" : ""}" data-unit="${u.id}"><img src="${lab.thumbs[u.model]}" alt="${u.name}三维模型"><span>${u.name}</span><small>${!u.install ? "已装 · 可调节" : installed(u.id) ? "✓ 已安装" : "待安装"}</small></button>`,
  ).join("");
  $("partsList")
    .querySelectorAll("[data-unit]")
    .forEach((b) => (b.onclick = () => selectUnit(b.dataset.unit)));
}
function renderJourney() {
  $("journey").innerHTML = STAGES.map(
    (s, i) =>
      `<button data-stage="${i}" class="${state.stage === i ? "active" : completed(i) ? "completed" : ""}" ${allowed(i) ? "" : "disabled"}><span class="step-index">${completed(i) ? "✓" : i + 1}</span>${s.name}</button>`,
  ).join("");
  $("journey")
    .querySelectorAll("button")
    .forEach((b) => (b.onclick = () => setStage(Number(b.dataset.stage))));
}
function render() {
  const stage = STAGES[state.stage];
  $("stageCount").textContent = `0${state.stage + 1} / 05`;
  $("stageTitle").textContent = stage.title;
  $("stageIntro").textContent = stage.intro;
  $("sceneTitle").textContent = [
    "搭建实验装置",
    "部件调节与光路",
    "预习知识测验",
    "独立成像挑战",
    "预习结果",
  ][state.stage];
  renderJourney();
  renderGuide();
  renderDock();
  selectUnit(state.selected);
  paint();
  renderProgress();
  save();
}
// This is a temporary model view; saved installation and assessment stay intact.
function setExplodedPreview(enabled) {
  explodedPreview = enabled;
  activeGesture = null;
  activeRaw = "";
  lab.configureStudentUnits(UNITS, enabled ? [] : state.installed);
  lab.explode(0);
  lab.view(enabled ? "exploded" : "home");
}
function setStage(stage) {
  if (busy || !allowed(stage)) return;
  setExplodedPreview(false);
  state.stage = stage;
  if (stage === 0 && !allInstalled()) lab.view("exploded");
  if (stage === 3 && !state.challenge) startChallenge();
  render();
  document.querySelector(".left-panel").scrollTop = 0;
}
async function installCurrent() {
  const current = INSTALL_UNITS.find((u) => !state.installed.includes(u.id));
  if (!current || busy || explodedPreview) return;
  if (state.selected !== current.id) {
    toast(`请先选择「${current.name}」。`);
    return;
  }
  busy = true;
  state.installed.push(current.id);
  log("安装组件", { unit: current.id });
  if (current.id === "lamp") {
    state.optics.lightOn = true;
    log("接通钠光灯电源");
  }
  if (current.id === "camera") {
    state.optics.cameraOn = false;
    state.optics.videoConnected = false;
    state.optics.monitorOn = false;
  }
  lab.configureStudentUnits(UNITS, state.installed);
  lab.optics(state.optics);
  render();
  await new Promise((r) => setTimeout(r, 650));
  busy = false;
  const next = INSTALL_UNITS.find((u) => !state.installed.includes(u.id));
  if (next) state.selected = next.id;
  render();
  toast(
    `${current.name}已就位${current.id === "lamp" ? "，黄色光路开始照明" : ""}。`,
  );
}
function renderGuide() {
  const host = $("guideContent");
  if (state.stage === 0) {
    const current = INSTALL_UNITS.find((u) => !state.installed.includes(u.id));
    host.innerHTML = `<ol class="install-list">${INSTALL_UNITS.map((u, i) => `<li class="${installed(u.id) ? "done" : current?.id === u.id ? "active" : ""}"><span class="number">${installed(u.id) ? "✓" : i + 1}</span>${u.name}</li>`).join("")}</ol>${current ? `<div class="guide-card active"><b>当前：${current.name}</b><p>${current.tip}</p></div><p class="feedback">${busy ? "组件正在就位…" : "直接点击场景中的组件模型进行安装。"}</p>` : '<div class="guide-card active"><b>六个组件安装完成</b><p>连接 CCD 电源、视频线和显示器，再进入引导调节。接线不会自动改善焦点或曝光。</p></div>'}<details class="help-toggle" open><summary>安装时观察什么？</summary><p>开启“显示光路”观察黄色入射光、绿色与紫色返回光。缺少部件时，光路会在对应位置中断。</p><p>目镜装好后先出现目视视场；CCD 安装后切换到示教视场。学生只安装完整组件，不拆镜片与螺钉。</p></details>`;

    if (!current) {
      const toggle = document.createElement("button");
      toggle.className = "operation";
      toggle.id = "explodedToggle";
      toggle.textContent = explodedPreview ? "返回装配图" : "查看爆炸图";
      toggle.setAttribute("aria-pressed", String(explodedPreview));
      toggle.onclick = () => {
        if (busy) return;
        setExplodedPreview(!explodedPreview);
        render();
      };
      toggle.disabled = busy;
      host.querySelector(".guide-card").append(toggle);
    }
    $("modelHint").textContent = explodedPreview
      ? "爆炸图观察 · 可旋转、缩放、点选部件，安装进度已保留"
      : current
      ? `下一步：点击三维${current.name}模型进行安装`
      : "安装完成，进入引导调节";
  }
  if (state.stage === 1) {
    host.innerHTML = `<div class="guide-card active"><b>按住实际部件调节，观察实时成像</b><p>拖动目镜调叉丝 → 上下拖动物镜调环纹 → 拖动半反镜与灯体调照明 → 拖动 CCD 镜头和机身调焦、曝光 → 拖动鼓轮对准中心。</p></div><ul id="guideGoals" class="goal-list"></ul><button id="checkGuide" class="operation primary">提交装调检查</button><details class="help-toggle"><summary>需要一点提示</summary><p>半反镜在约 45° 附近微调；底座反光镜反转向内。慢慢扫描焦点，找十字丝和环纹最清楚的位置。</p><p>图像很亮仍模糊时先调焦，不要继续增加曝光。鼓轮改变环纹相对固定十字丝的位置。</p></details>`;
    $("checkGuide").onclick = () => {
      const checks = goals();
      if (checks.every((g) => g.ok)) {
        state.guidePassed = true;
        state.guideSnapshot = { ...state.optics };
        log("引导装调通过");
        renderJourney();
        renderProgress();
        toast("视场调节通过，可以进入预习测验。");
      } else {
        toast(
          "还需要：" +
            checks
              .filter((g) => !g.ok)
              .map((g) => g.detail ? `${g.label}（${g.detail}）` : g.label)
              .join("、"),
        );
        log("引导装调未通过");
      }
    };
    $("modelHint").textContent =
      "直接按住部件拖动调节 · 滚轮微调 · 单击小步调节";
  }
  if (state.stage === 2) renderQuiz();
  if (state.stage === 3) {
    const c = state.challenge;
    host.innerHTML = `<div class="challenge-box"><b>新的失调状态 · 独立完成</b><p>请修正照明、十字丝、物镜焦点、相机成像和鼓轮居中。可自行选择是否显示光路，提交前不显示最佳参数或具体纠错提示。</p></div><div class="challenge-timer" id="challengeTimer"></div><p class="feedback" id="challengeEdits">已调节 ${c.edits} 次 · 涉及 ${c.touched.length} 项控制</p><button id="submitChallenge" class="primary challenge-submit">提交当前成像</button>${c.result ? `<ul class="rubric">${c.result.rubric.map((g) => `<li class="${g.ok ? "passed" : "failed"}"><span>${g.ok ? "✓" : "○"} ${g.label}</span><b>${g.ok ? g.points : 0}/${g.points}</b></li>`).join("")}</ul><p class="feedback">${c.result.passed ? "挑战通过，可查看报告。" : "尚未通过，继续自行调整后再次提交。"}</p>` : ""}<button id="newChallenge" class="operation">重新抽取挑战状态</button>`;
    $("submitChallenge").onclick = submitChallenge;
    $("newChallenge").onclick = () => {
      startChallenge();
      render();
    };
    $("modelHint").textContent = "独立挑战：以视场为准调节，提交后查看反馈";
  }
  if (state.stage === 4) {
    host.innerHTML = `<div class="quiz-type">PRE-LAB RESULT</div><div class="score">${totalScore()}<small> / 100</small></div><div class="guide-card ${passed() ? "active" : ""}"><b>${passed() ? "预习通过" : "尚未达到通过要求"}</b><p>知识测验 ${quizScore()}/40 分<br>成像挑战 ${state.challenge?.result?.score || 0}/60 分<br>要求总分 ≥80 且成像挑战通过。</p></div><label class="control-row">姓名 / 学号（可选）<input class="name-input" id="studentName" maxlength="60"></label><button id="openReport" class="operation primary">查看并下载预习报告</button><button id="retryQuiz" class="operation">重做预习题</button><button id="retryChallenge" class="operation">再次挑战成像</button><button id="enterLab" class="operation" ${passed() ? "" : "disabled"}>进入真实实验台</button>`;
    $("studentName").value = state.name;
    $("studentName").oninput = (e) => {
      state.name = e.target.value;
      save();
    };
    $("openReport").onclick = () => openReport(state, lab, reportData());
    $("retryQuiz").onclick = () => {
      state.answers = [];
      state.quizCursor = 0;
      state.quizDone = false;
      state.draft = [];
      state.stage = 2;
      render();
    };
    $("retryChallenge").onclick = () => {
      state.stage = 3;
      startChallenge();
      render();
    };
    $("enterLab").onclick = () => {
      if (passed())
        window.parent.postMessage(
          { type: "wxz-enter-lab", experiment: "newton_rings" },
          location.origin,
        );
    };
    $("modelHint").textContent =
      "预习记录已生成，报告包含逐题解析与成像挑战结果";
  }
}
function renderQuiz() {
  const host = $("guideContent");
  if (state.quizDone) {
    host.innerHTML = `<div class="score">${quizScore()}<small> / 40</small></div><p class="feedback">预习题已完成，接下来独立完成成像挑战。</p>`;
    return;
  }
  const i = state.quizCursor,
    q = QUESTIONS[i],
    answer = state.answers.find((a) => a.id === q.id),
    draft = state.draft;
  host.innerHTML = `<div class="quiz-type">${{ single: "单选题", multi: "多选题 · 选全且无错选", judge: "判断题" }[q.type]} · ${i + 1}/${QUESTIONS.length} · 5 分</div><h3 class="quiz-question">${q.question}</h3>${q.options.map((text, j) => `<button class="answer ${draft.includes(j) || answer?.selected.includes(j) ? "selected" : ""}" data-answer="${j}" aria-pressed="${draft.includes(j) || !!answer?.selected.includes(j)}" ${answer ? "disabled" : ""}>${String.fromCharCode(65 + j)}. ${text}</button>`).join("")}${answer ? `<div class="quiz-result ${answer.correct ? "" : "wrong"}"><b>${answer.correct ? "回答正确" : "需要复习"}</b><br>${q.explanation}<br>正确选择：${q.answers.map((v) => String.fromCharCode(65 + v)).join("、")}</div><button id="nextQuestion" class="operation primary">${i === QUESTIONS.length - 1 ? "完成预习题" : "下一题 →"}</button>` : `<button id="submitAnswer" class="operation primary" ${draft.length ? "" : "disabled"}>提交答案</button>`}`;
  host.querySelectorAll("[data-answer]").forEach(
    (b) =>
      (b.onclick = () => {
        const value = Number(b.dataset.answer);
        state.draft =
          q.type === "multi"
            ? state.draft.includes(value)
              ? state.draft.filter((v) => v !== value)
              : [...state.draft, value]
            : [value];
        save();
        renderQuiz();
      }),
  );
  if (answer)
    $("nextQuestion").onclick = () => {
      state.draft = [];
      if (i === QUESTIONS.length - 1) {
        state.quizDone = true;
        log("完成知识测验", { score: quizScore() });
      } else state.quizCursor++;
      render();
    };
  else
    $("submitAnswer").onclick = () => {
      if (!state.draft.length) return;
      state.answers.push({
        id: q.id,
        type: q.type,
        selected: [...state.draft],
        correct: gradeAnswer(q, state.draft),
        at: new Date().toISOString(),
      });
      log("提交预习题", { question: q.id });
      renderQuiz();
      renderProgress();
    };
  $("modelHint").textContent = "完成单选、多选和判断题后，进入独立成像挑战";
}
function startChallenge() {
  const seed = makeChallenge();
  state.optics = { ...state.optics, ...seed };
  state.challenge = {
    startedAt: Date.now(),
    edits: 0,
    touched: [],
    attempts: [],
    result: null,
  };
  notified = false;
  state.selected = "eyepiece";
  log("开始独立成像挑战");
  lab.optics(state.optics);
  lab.view("home");
}
function submitChallenge() {
  const rubric = goals(),
    score = rubric.reduce((sum, g) => sum + (g.ok ? g.points : 0), 0),
    result = {
      score,
      passed: rubric.every((g) => g.ok),
      rubric,
      submittedAt: Date.now(),
      durationSeconds: Math.round(
        (Date.now() - state.challenge.startedAt) / 1000,
      ),
      edits: state.challenge.edits,
      touched: [...state.challenge.touched],
      optics: { ...state.optics },
      snapshot: lab.screenshot(),
    };
  state.challenge.result = result;
  state.challenge.attempts.push({
    at: result.submittedAt,
    score,
    passed: result.passed,
  });
  log("提交成像挑战", { score, passed: result.passed });
  render();
  notifyCompletion();
  toast(
    result.passed
      ? "独立成像挑战通过。"
      : "挑战尚未通过，请根据提交反馈继续调整。",
  );
}
function notifyCompletion() {
  if (passed() && !notified) {
    notified = true;
    window.parent.postMessage(
      {
        type: "wxz-simulation-complete",
        experiment: "newton_rings",
        version: 3,
        score: totalScore(),
        challengePassed: true,
        completedStages: 4,
      },
      location.origin,
    );
  }
}
function reportData() {
  return {
    schema: "wxz.newton-student.prep.v3",
    student: state.name || "未填写",
    startedAt: state.startedAt,
    finishedAt: state.challenge?.result?.submittedAt || Date.now(),
    installed: state.installed,
    guidePassed: state.guidePassed,
    guideSnapshot: state.guideSnapshot,
    answers: state.answers,
    knowledgeScore: quizScore(),
    challenge: state.challenge,
    totalScore: totalScore(),
    passed: passed(),
    actions: state.actions,
  };
}
try {
  lab = createLab($("viewport"), selectUnit, adjust, interact);
  const interaction = document.createElement("div");
  interaction.id = "modelInteraction";
  interaction.setAttribute("role", "status");
  $("viewport").append(interaction);
  lab.configureStudentUnits(UNITS, state.installed, true);
  lab.optics(state.optics);
  $("loading").remove();
  lab.view(state.stage === 0 ? "exploded" : "home");
  render();
  notifyCompletion();
} catch (error) {
  console.error(error);
  $("loading").textContent =
    "三维场景加载失败，请刷新页面并确认浏览器支持 WebGL。";
}
$("nextStage").onclick = () => setStage(state.stage + 1);
for (const [id, key, unit] of [
  ["lampSwitch", "lightOn", "lamp"],
  ["cameraPowerSwitch", "cameraOn", "camera"],
  ["videoCableSwitch", "videoConnected", "camera"],
  ["monitorPowerSwitch", "monitorOn", null],
])
  $(id).onchange = (e) => {
    if (unit && !installed(unit)) {
      e.target.checked = false;
      return;
    }
    change(key, e.target.checked, "电源与接线");
  };
document
  .querySelectorAll("[data-view]")
  .forEach((b) => (b.onclick = () => {
    if (explodedPreview) {
      setExplodedPreview(false);
      render();
    }
    lab.view(b.dataset.view);
  }));
$("raysToggle").onclick = () => {
  state.optics.showRays = !state.optics.showRays;
  lab.optics(state.optics);
  syncControls();
  save();
};
$("xrayToggle").onclick = () => {
  state.optics.xray = !state.optics.xray;
  lab.optics(state.optics);
  syncControls();
  save();
};
$("labelToggle").onclick = () => {
  labels = !labels;
  lab.labels(labels);
  $("labelToggle").setAttribute("aria-pressed", labels);
};
$("fullscreen").onclick = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch {
    toast("当前环境无法进入全屏，可继续使用页面视图。");
  }
};
$("reset").onclick = () => {
  if (!confirm("重新预习会清除本轮安装、测验和挑战进度。已下载报告保留。"))
    return;
  state = fresh();
  explodedPreview = false;
  notified = false;
  lab.configureStudentUnits(UNITS, [], true);
  lab.optics(state.optics);
  lab.view("exploded");
  render();
};
$("closeReport").onclick = () => $("reportDialog").close();
$("downloadReport").onclick = () => downloadReport(reportData());
$("downloadJson").onclick = () => downloadData(reportData());
$("printReport").onclick = () => window.print();
document.addEventListener("keydown", (e) => {
  if (
    !["ArrowUp", "ArrowDown"].includes(e.key) ||
    /INPUT|TEXTAREA|BUTTON/.test(document.activeElement?.tagName) ||
    $("reportDialog").open
  )
    return;
  if (adjust(state.selected, e.key === "ArrowUp" ? 1 : -1, e))
    e.preventDefault();
});
setInterval(() => {
  if (!lab || document.hidden) return;
  paint();
  if (inChallenge()) {
    $("challengeTimer").textContent =
      `用时 ${Math.floor((Date.now() - state.challenge.startedAt) / 60000)} 分 ${Math.floor((Date.now() - state.challenge.startedAt) / 1000) % 60} 秒`;
  }
  const seconds = Math.floor((Date.now() - state.startedAt) / 1000);
  $("elapsed").textContent =
    `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}, 300);
