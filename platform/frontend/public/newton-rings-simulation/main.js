import { DEFAULT_OPTICS, TUNING_KEYS, opticalMetrics } from "./optics.js";
import { renderOptics, renderMeasurement, updateField } from "./experiment.js";
import { createLab, PARTS, CATEGORIES } from "./model.js";
import {
  STAGES,
  DISASSEMBLY,
  ASSEMBLY,
  STEP_TIPS,
  QUESTIONS,
} from "./curriculum.js";
const $ = (id) => document.getElementById(id),
  partById = Object.fromEntries(PARTS.map((p) => [p.id, p]));
const userKey = localStorage.getItem("wxz_userId") || "guest",
  storageKey = `wxz-newton-3d-v2:${userKey}`;
const initial = () => ({
  version: 2,
  stage: 0,
  readings: [],
  measurement: null,
  guidedReadings: 0,
  seen: [],
  disassembled: 0,
  assembled: 0,
  aligned: false,
  selected: "base",
  actions: [],
  mistakes: 0,
  answers: [],
  attempts: [],
  submitted: false,
  startedAt: Date.now(),
  finishedAt: null,
  name: localStorage.getItem("wxz_displayName") || "",
  optics: { ...DEFAULT_OPTICS },
});
let state = initial();
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (
    saved?.version === 2 &&
    Array.isArray(saved.actions) &&
    Array.isArray(saved.seen)
  ) {
    state = { ...state, ...saved };
    state.optics = { ...DEFAULT_OPTICS, ...saved.optics };
    state.stage = Math.max(0, Math.min(6, state.stage));
    // Correct only the old demo's untouched, unverified automatic preset.
    const legacyDemo = state.actions.some(
      (a) =>
        a.action === "演示：连接视频线、开启相机和屏幕、点亮钠光灯并调好成像",
    );
    const legacyPreset = {
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
    };
    if (
      !state.opticsInteractionVersion &&
      !state.manualOpticsTouched &&
      !state.aligned &&
      !state.submitted &&
      !state.measurement &&
      legacyDemo &&
      TUNING_KEYS.every((key) => state.optics[key] === legacyPreset[key])
    ) {
      for (const key of TUNING_KEYS) state.optics[key] = DEFAULT_OPTICS[key];
      state.opticsInteractionVersion = 1;
      state.actions.push({
        at: new Date().toISOString(),
        action: "修正旧版演示自动调好参数：恢复待装调状态",
        part: null,
        ok: true,
      });
      localStorage.setItem(storageKey, JSON.stringify(state));
    }
  }
} catch {}
let lab,
  busy = false,
  toastTimer,
  quizSelection = null,
  labelsOn = true,
  rotating = false,
  reportSnapshot = "";
function save() {
  try {
    localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {}
}
function log(action, part = null, ok = true) {
  state.actions.push({
    at: new Date().toISOString(),
    action,
    part: part ? partById[part].name : null,
    ok,
  });
  save();
}
function toast(message) {
  $("toast").textContent = message;
  $("toast").classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => $("toast").classList.remove("visible"), 3000);
}
function recognized() {
  return state.seen.length === PARTS.length;
}
function aligned() {
  return opticalMetrics(state.optics, lab.removed.size === 0).ready;
}
function canAdvance(index = state.stage) {
  return [
    recognized(),
    state.disassembled === DISASSEMBLY.length,
    state.assembled === ASSEMBLY.length,
    state.aligned,
    !!state.measurement,
    state.submitted,
    true,
  ][index];
}
function highestStage() {
  let n = 0;
  while (n < 6 && canAdvance(n)) n++;
  return n;
}
function score() {
  return (
    Math.max(0, 20 - state.mistakes * 2) +
    state.answers.filter((a) => a.correct).length * 10
  );
}
function selectPart(id, userAction = true) {
  if (!partById[id]) return;
  state.selected = id;
  lab?.select(id);
  const p = partById[id];
  $("partNumber").textContent = String(p.number).padStart(2, "0");
  $("partName").textContent = p.name;
  $("partEnglish").textContent = p.en;
  $("partDescription").textContent = p.description;
  $("partTip").textContent = p.tip;
  if (userAction && state.stage === 0 && !state.seen.includes(id)) {
    state.seen.push(id);
    log("认识部件", id);
    renderGuide();
    renderJourney();
  }
  document.querySelectorAll(".part-card").forEach((el) => {
    el.classList.toggle("active", el.dataset.part === id);
    el.classList.toggle("seen", state.seen.includes(el.dataset.part));
  });
  if (
    state.stage === 5 &&
    !state.submitted &&
    QUESTIONS[state.answers.length]?.type === "part"
  ) {
    $("identifiedPart").textContent = `当前选择：${p.name}`;
    $("submitAnswer").disabled = false;
  }
  save();
}
function restoreAssembly() {
  lab.removed.clear();
  DISASSEMBLY.slice(0, state.disassembled).forEach((id) => lab.removed.add(id));
  ASSEMBLY.slice(0, state.assembled).forEach((id) => lab.removed.delete(id));
}
function setExplode(value) {
  lab.explode(value);
  $("explode").value = Math.round(value * 100);
  $("explodeValue").textContent = `${Math.round(value * 100)}%`;
}
function setStage(index) {
  if (index > highestStage() && index !== 3) {
    toast("请先完成前面的预习任务。");
    return;
  }
  if (busy) return;
  state.stage = index;
  quizSelection = null;
  setExplode(0);
  restoreAssembly();
  if (index === 1 || index === 2) {
    state.optics.lightOn = false;
    lab.optics(state.optics);
    drawRings();
  }
  if (index === 1 || index === 2) lab.view("exploded");
  else lab.view("home");
  render();
  document.querySelector(".left-panel").scrollTop = 0;
  save();
}
function renderJourney() {
  $("journey").innerHTML = STAGES.map(
    (s, i) =>
      `<button data-stage="${i}" class="${i === state.stage ? "active" : canAdvance(i) ? "completed" : ""}" ${i > highestStage() && i !== 3 ? "disabled" : ""}><span class="step-index">${canAdvance(i) && i < 6 ? "✓" : i + 1}</span>${s.name}</button>`,
  ).join("");
  $("journey")
    .querySelectorAll("button")
    .forEach((b) => (b.onclick = () => setStage(+b.dataset.stage)));
}
function render() {
  renderJourney();
  renderGuide();
  selectPart(state.selected, false);
  drawRings();
}
function renderGuide() {
  const n = state.stage,
    s = STAGES[n];
  $("stageCount").textContent = `0${n + 1} / 07`;
  $("stageTitle").textContent = s.title;
  $("stageIntro").textContent = s.intro;
  $("sceneTitle").textContent = [
    "实拍结构还原",
    "结构拆解训练",
    "示教装配复原",
    "完整光路与成像",
    "暗环读数与计算",
    "预习测评",
    "预习完成记录",
  ][n];
  const next = $("nextStage");
  next.hidden = n === 6;
  next.disabled = !canAdvance() || n > highestStage();
  next.textContent =
    [
      "开始拆解 →",
      "开始装配 →",
      "光路与成像 →",
      "开始暗环测量 →",
      "开始预习测评 →",
      "查看结果报告 →",
    ][n] || "";
  next.onclick = () => setStage(n + 1);
  let done = [
    state.seen.length / PARTS.length,
    state.disassembled / DISASSEMBLY.length,
    state.assembled / ASSEMBLY.length,
    +state.aligned,
    +!!state.measurement,
    state.answers.length / 8,
    +state.submitted,
  ][n];
  $("progressBar").style.width = `${done * 100}%`;
  const content = $("guideContent");
  if (n === 0) {
    $("progressText").textContent =
      `已认识 ${state.seen.length} / ${PARTS.length} 个部件`;
    content.innerHTML = `<div class="guide-card active"><div class="eyebrow">01 · 结构探索</div><b>点选三维部件，打开它的档案</b><p>已认识的部件会在下方部件库出现 ✓。你也可以点选库中的三维缩略图。</p></div><div class="guide-card"><div class="eyebrow">02 · 爆炸观察</div><b>拖动场景底部的爆炸图滑杆</b><p>观察玻璃、透镜和压环的上下位置。旋转或缩放，从不同角度查看连接关系。</p><button class="operation" id="showExploded">展开爆炸图 ↗</button></div><div class="notice">核心：平凸透镜凸面朝下，放在平面玻璃上。两者之间的空气膜产生反射干涉。</div>`;
    $("showExploded").onclick = () => {
      setExplode(1);
      lab.view("exploded");
      log("查看爆炸图");
    };
  }
  if (n === 1 || n === 2) {
    const order = n === 1 ? DISASSEMBLY : ASSEMBLY,
      count = n === 1 ? state.disassembled : state.assembled,
      verb = n === 1 ? "拆卸" : "安装";
    $("progressText").textContent =
      `已${verb} ${count} / ${DISASSEMBLY.length} 组部件`;
    content.innerHTML =
      `<div class="notice">${n === 1 ? "光源已关闭。仅做虚拟结构拆解，真实设备不随意拆卸内部镜组。" : "先放平面玻璃，再将透镜凸面朝下放好，最后固定压环。"}</div>` +
      order
        .map(
          (id, i) =>
            `<div class="guide-card ${i === count ? "active" : i < count ? "done" : ""}"><div class="eyebrow">${i < count ? "✓ 已完成" : `${String(i + 1).padStart(2, "0")} / ${DISASSEMBLY.length}`}</div><b>${verb}${partById[id].name}</b>${i === count ? `<p>${n === 2 && id === "lens" ? "装回时保持凸面向下，仅接触透镜边缘。" : STEP_TIPS[id]}</p><button class="operation primary" id="operate" ${busy ? "disabled" : ""}>${busy ? "移动中…" : `执行${verb}所选部件`}</button>` : ""}</div>`,
        )
        .join("");
    if (count < order.length)
      $("operate").onclick = () => operate(n, order[count]);
  }
  if (n === 3) {
    $("progressText").textContent = state.aligned
      ? "装调检查已通过"
      : "照明、焦点、曝光分别调节";
    renderOptics(state, lab, { save, log, selectPart, render, toast });
  }
  if (n === 4) {
    $("progressText").textContent = `已记录 ${state.readings.length}/8 个位置`;
    renderMeasurement(state, lab, { save, log, render, toast });
  }
  if (n === 5) {
    $("progressText").textContent = `已作答 ${state.answers.length} / 8 题`;
    renderQuiz();
  }
  if (n === 6) {
    const passed = score() >= 80;
    $("progressText").textContent = passed
      ? "达到预习通过标准"
      : "建议复习错题后重新测评";
    content.innerHTML = `<div class="eyebrow">PRE-LAB RESULT</div><div class="score">${score()}<small> / 100</small></div><div class="guide-card ${passed ? "active" : ""}"><b>${passed ? "预习通过 · 可以进入实验" : "尚未通过 · 请继续巩固"}</b><p>结构认识 ${state.seen.length}/${PARTS.length}<br>拆卸 ${state.disassembled}/${DISASSEMBLY.length} · 装配 ${state.assembled}/${ASSEMBLY.length}<br>操作规范 ${Math.max(0, 20 - state.mistakes * 2)}/20<br>知识测评 ${state.answers.filter((a) => a.correct).length * 10}/80</p></div><label class="control-row">报告姓名 / 学号（可选）<input id="studentName" class="name-input" maxlength="60" placeholder="填写后会显示在报告中"></label><button class="operation primary" id="openReport">生成并查看报告</button><button class="operation" id="retryQuiz">重新测评</button><p class="feedback">报告包含本次操作轨迹、逐题答案与解析。下载的 HTML 可离线打开，并可打印成 PDF。</p>`;
    $("studentName").value = state.name;
    $("studentName").oninput = (e) => {
      state.name = e.target.value;
      save();
    };
    $("openReport").onclick = openReport;
    const enter = document.createElement("button");
    enter.className = "operation";
    enter.id = "enterRealLab";
    enter.textContent = "进入真实实验台";
    enter.disabled = !passed;
    enter.onclick = () =>
      window.parent.postMessage(
        { type: "wxz-enter-lab", experiment: "newton_rings" },
        location.origin,
      );
    content.append(enter);
    $("retryQuiz").onclick = () => {
      state.attempts.push({
        at: state.finishedAt,
        score: score(),
        answers: state.answers,
      });
      state.answers = [];
      state.submitted = false;
      state.finishedAt = null;
      setStage(5);
      log("重新测评");
    };
  }
}
async function operate(stage, id) {
  if (busy) return;
  if (state.selected !== id) {
    state.mistakes++;
    log(stage === 1 ? "拆卸对象错误" : "装配对象错误", state.selected, false);
    toast(`请选择「${partById[id].name}」后再操作。`);
    return;
  }
  busy = true;
  setExplode(0);
  if (stage === 1) {
    if (id === "camera") {
      state.optics.videoConnected = false;
      state.optics.cameraOn = false;
      lab.optics(state.optics);
      log("拆卸前断开相机电源和视频线", "camera");
    }
    lab.removed.add(id);
    state.disassembled++;
  } else {
    lab.removed.delete(id);
    state.assembled++;
  }
  log(stage === 1 ? "拆卸" : "安装", id);
  renderGuide();
  await new Promise((resolve) => setTimeout(resolve, 700));
  busy = false;
  render();
  toast(`${partById[id].name}已${stage === 1 ? "拆下" : "装回"}`);
}
function updateOptics() {
  lab.optics(state.optics);
  drawRings();
  save();
}
function drawRings() {
  if (lab) updateField(state, lab);
}
function renderQuiz() {
  const content = $("guideContent");
  if (state.submitted) {
    content.innerHTML = `<div class="score">${score()}<small> / 100</small></div><p class="feedback">本次测评已完成。打开结果报告查看逐题反馈。</p>`;
    return;
  }
  const i = state.answers.length,
    q = QUESTIONS[i];
  if (!q) return;
  content.innerHTML =
    `<div class="quiz-progress">QUESTION ${String(i + 1).padStart(2, "0")} / ${DISASSEMBLY.length} · 每题 10 分</div><h3 class="quiz-question">${q.question}</h3>` +
    (q.type === "part"
      ? `<div class="guide-card"><p>旋转模型进行辨认，也可在部件库中点选。选择后提交。</p><b id="identifiedPart">当前选择：${partById[state.selected].name}</b></div>`
      : q.options
          .map(
            (text, j) =>
              `<button class="answer" data-answer="${j}">${String.fromCharCode(65 + j)}. ${text}</button>`,
          )
          .join("")) +
    `<button id="submitAnswer" class="operation primary" ${q.type !== "part" ? "disabled" : ""}>提交答案</button>`;
  content.querySelectorAll("[data-answer]").forEach(
    (b) =>
      (b.onclick = () => {
        quizSelection = +b.dataset.answer;
        content
          .querySelectorAll(".answer")
          .forEach((el) => el.classList.toggle("selected", el === b));
        $("submitAnswer").disabled = false;
      }),
  );
  $("submitAnswer").onclick = () => {
    const selected = q.type === "part" ? state.selected : quizSelection;
    if (selected === null) return;
    const correct =
      q.type === "part" ? selected === q.part : selected === q.answer;
    state.answers.push({
      question: i,
      selected,
      correct,
      at: new Date().toISOString(),
    });
    quizSelection = null;
    log("提交测评答案", q.type === "part" ? selected : null, correct);
    if (state.answers.length === 8) {
      state.submitted = true;
      state.finishedAt = Date.now();
      save();
      if (score() >= 80) {
        window.parent.postMessage(
          {
            type: "wxz-simulation-complete",
            experiment: "newton_rings",
            version: 2,
            score: score(),
            completedStages: 5,
          },
          location.origin,
        );
      }
      setStage(6);
    } else renderGuide();
  };
}
const escapeHtml = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
function reportData() {
  return {
    schema: "wxz.newton-rings.prep.v2",
    experiment: "牛顿环法测定平凸透镜曲率半径",
    student: state.name || "未填写",
    startedAt: new Date(state.startedAt).toISOString(),
    finishedAt: new Date(state.finishedAt || Date.now()).toISOString(),
    durationSeconds: Math.round(
      ((state.finishedAt || Date.now()) - state.startedAt) / 1000,
    ),
    score: score(),
    passed: score() >= 80,
    passThreshold: 80,
    operationScore: Math.max(0, 20 - state.mistakes * 2),
    quizScore: state.answers.filter((a) => a.correct).length * 10,
    operationErrors: state.mistakes,
    measurement: state.measurement,
    readings: state.readings,
    guidedReadings: state.guidedReadings,
    alignmentSnapshot: state.alignmentSnapshot,
    recognizedParts: state.seen.map((id) => partById[id].name),
    disassembled: state.disassembled,
    assembled: state.assembled,
    opticsCheckPassed: state.aligned,
    optics: { ...state.optics },
    answers: state.answers.map((a, i) => {
      const q = QUESTIONS[i];
      return {
        question: q.question,
        selected:
          q.type === "part"
            ? partById[a.selected]?.name
            : q.options[a.selected],
        correctAnswer:
          q.type === "part" ? partById[q.part].name : q.options[q.answer],
        correct: a.correct,
        explanation: q.explanation,
      };
    }),
    actions: state.actions,
    previousAttempts: state.attempts.map((a) => ({ at: a.at, score: a.score })),
    note: "依据 MKS-NHY 厂家说明书（部件图见 PDF 第4、15页）和两张实拍图还原。厂家主机外形195×155×285mm，透镜标称R=1000mm。外形比例由照片估计；亮度与模糊为教学相对模型，空气膜剖面经过放大。",
  };
}
function reportMarkup(d) {
  return `<article class="report-document"><div class="eyebrow">物小智 · PHYSICS LAB / PRE-LAB REPORT</div><h1>牛顿环实验 · 仿真预习报告</h1><p>姓名 / 学号：${escapeHtml(d.student)}<br>完成时间：${new Date(d.finishedAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" })}（北京时间）<br>预习时长：${Math.floor(d.durationSeconds / 60)} 分 ${d.durationSeconds % 60} 秒</p><p class="result">${d.passed ? "预习通过" : "尚未通过"} · ${d.score} / 100 分</p><p>操作规范 ${d.operationScore}/20（每次选错拆装对象扣 2 分，最低 0 分）；知识测评 ${d.quizScore}/80；通过线 80 分。</p>${reportSnapshot ? `<img src="${reportSnapshot}" alt="本次预习的三维仪器场景">` : ""}<h2>任务完成情况</h2><table><tr><th>任务</th><th>结果</th></tr><tr><td>认识仪器部件</td><td>${d.recognizedParts.length}/${PARTS.length}</td></tr><tr><td>分步拆卸 / 装配复原</td><td>${d.disassembled}/${DISASSEMBLY.length} · ${d.assembled}/${ASSEMBLY.length}</td></tr><tr><td>光路装调检查</td><td>${d.opticsCheckPassed ? "通过" : "未通过"}</td></tr><tr><td>拆装对象选错次数</td><td>${d.operationErrors}</td></tr></table><h2>暗环读数与曲率半径</h2>${d.measurement ? `<p>平均 R = ${d.measurement.radius.toFixed(1)} mm；示范定位 ${d.guidedReadings} 次。</p><table><tr><th>m</th><th>n</th><th>R / mm</th></tr>${d.measurement.pairs.map((p) => `<tr><td>${p.m}</td><td>${p.n}</td><td>${p.radius.toFixed(2)}</td></tr>`).join("")}</table><p>${d.readings.map((r) => `${r.side === "left" ? "左" : "右"}${r.order}: ${r.reading.toFixed(2)} mm`).join("；")}</p>` : "尚未完成"}<h2>测评答案与解析</h2>${d.answers.map((a, i) => `<p><b>${i + 1}. ${escapeHtml(a.question)} ${a.correct ? "✓" : "✗"}</b><br>你的答案：${escapeHtml(a.selected)}<br>正确答案：${escapeHtml(a.correctAnswer)}<br>解析：${escapeHtml(a.explanation)}</p>`).join("")}<h2>操作记录</h2><table><tr><th>时间（北京时间）</th><th>操作</th><th>部件</th><th>结果</th></tr>${d.actions.map((a) => `<tr><td>${new Date(a.at).toLocaleTimeString("zh-CN", { timeZone: "Asia/Shanghai" })}</td><td>${escapeHtml(a.action)}</td><td>${escapeHtml(a.part || "—")}</td><td>${a.ok ? "完成" : "需调整"}</td></tr>`).join("")}</table><h2>模型说明与原理参考</h2><p>${escapeHtml(d.note)}</p><p>反射暗环满足 rₘ² = mλR，故 R = (Dₘ² − Dₙ²) / [4(m − n)λ]。λ = 589.3 nm。原理参考：<a href="https://web.physics.ucsb.edu/~lecturedemonstrations/Composer/Pages/84.27.html">UCSB Physics · Newton’s rings</a>。</p><p>本报告记录浏览器端的学习过程与仿真测评结果。报告文件由学生下载保存。</p></article>`;
}
function openReport() {
  reportSnapshot = lab.screenshot();
  $("reportBody").innerHTML = reportMarkup(reportData());
  $("reportDialog").showModal();
}
function download(content, type, ext) {
  const blob = new Blob([content], { type }),
    url = URL.createObjectURL(blob),
    a = document.createElement("a");
  a.href = url;
  a.download = `牛顿环仿真预习报告_${new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" })}.${ext}`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
$("downloadReport").onclick = () => {
  const d = reportData();
  download(
    `<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>牛顿环仿真预习报告</title><style>body{font:14px/1.8 'Microsoft YaHei',sans-serif;color:#20333d;max-width:850px;margin:32px auto;padding:20px}h1{font-size:26px}h2{font-size:19px;border-bottom:1px solid #bccdd4;padding-bottom:8px}table{border-collapse:collapse;width:100%;font-size:12px}td,th{border:1px solid #b5c9d0;padding:8px;text-align:left}img{width:100%;max-height:330px;object-fit:contain;background:#102331}.result{color:#218476;font-size:23px}a{color:#237f82}@media print{body{margin:0}tr{break-inside:avoid}}</style>${reportMarkup(d)}</html>`,
    "text/html;charset=utf-8",
    "html",
  );
};
$("downloadJson").onclick = () =>
  download(
    JSON.stringify(reportData(), null, 2),
    "application/json;charset=utf-8",
    "json",
  );
$("printReport").onclick = () => window.print();
$("closeReport").onclick = () => $("reportDialog").close();
$("reset").onclick = () => {
  if (
    !confirm("重新开始会清除本浏览器当前学生的预习进度。已下载报告不会受影响。")
  )
    return;
  state = initial();
  save();
  lab.removed.clear();
  setStage(0);
  updateOptics();
};
try {
  lab = createLab($("viewport"), selectPart);
  $("loading").remove();
  $("partsList").innerHTML = PARTS.map(
    (p) =>
      `<button class="part-card" data-part="${p.id}"><small>${String(p.number).padStart(2, "0")}</small><img src="${lab.thumbs[p.id]}" alt="${p.name}的三维模型"><span>${p.name}</span></button>`,
  ).join("");
  document
    .querySelectorAll(".part-card")
    .forEach((b) => (b.onclick = () => selectPart(b.dataset.part)));
  restoreAssembly();
  lab.optics(state.optics);
  render();
} catch (e) {
  console.error(e);
  $("loading").textContent =
    "三维场景加载失败。请在支持 WebGL 的浏览器中打开，并启用硬件加速。";
  $("guideContent").textContent = e.message;
}
$("explode").oninput = (e) => {
  if (busy) return;
  setExplode(+e.target.value / 100);
  if (+e.target.value > 30) lab.view("exploded");
};
document.querySelectorAll("[data-view]").forEach(
  (b) =>
    (b.onclick = () => {
      lab.view(b.dataset.view);
      $("sceneSubtitle").textContent =
        b.dataset.view === "film"
          ? "空气膜剖面 · 膜厚与光线间距放大示意"
          : "MKS-NHY / JCD3 · 厂家说明书与实拍还原";
    }),
);
$("autoRotate").onclick = () => {
  rotating = !rotating;
  lab.rotate(rotating);
  $("autoRotate").setAttribute("aria-pressed", rotating);
};
$("labelToggle").onclick = () => {
  labelsOn = !labelsOn;
  lab.labels(labelsOn);
  $("labelToggle").setAttribute("aria-pressed", labelsOn);
};
$("fullscreen").onclick = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch {
    toast("当前浏览器不允许全屏，可直接使用页面视图。");
  }
};
setInterval(() => {
  const s = Math.floor(
    ((state.finishedAt || Date.now()) - state.startedAt) / 1000,
  );
  $("elapsed").textContent =
    `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}, 1000);
if (
  state.submitted &&
  score() >= 80 &&
  state.aligned &&
  state.assembled === ASSEMBLY.length &&
  recognized()
)
  window.parent.postMessage(
    {
      type: "wxz-simulation-complete",
      experiment: "newton_rings",
      version: 2,
      score: score(),
      completedStages: 5,
    },
    location.origin,
  );

$("partFilters").innerHTML = Object.entries(CATEGORIES)
  .map(
    ([id, name]) =>
      `<button data-category="${id}" class="${id === "all" ? "active" : ""}">${name}</button>`,
  )
  .join("");
$("partFilters")
  .querySelectorAll("button")
  .forEach(
    (b) =>
      (b.onclick = () => {
        document
          .querySelectorAll(".part-card")
          .forEach(
            (c) =>
              (c.hidden =
                b.dataset.category !== "all" &&
                partById[c.dataset.part].category !== b.dataset.category),
          );
        $("partFilters")
          .querySelectorAll("button")
          .forEach((el) => el.classList.toggle("active", el === b));
      }),
  );
$("opticsDemo").onclick = () => setStage(3);
$("raysToggle").onclick = () => {
  state.optics.showRays = !state.optics.showRays;
  lab.optics(state.optics);
  $("raysToggle").setAttribute("aria-pressed", state.optics.showRays);
  save();
};
for (const [id, key, description] of [
  ["lampSwitch", "lightOn", "钠光灯电源"],
  ["cameraPowerSwitch", "cameraOn", "CCD 相机电源"],
  ["videoCableSwitch", "videoConnected", "CCD 至示教屏幕视频线"],
  ["monitorPowerSwitch", "monitorOn", "示教显示器电源"],
])
  $(id).onchange = (e) => {
    if (busy) {
      e.target.checked = !!state.optics[key];
      return;
    }
    if (
      key === "videoConnected" &&
      e.target.checked &&
      lab.removed.has("camera")
    ) {
      e.target.checked = false;
      toast("请先装回 CCD 相机，再连接视频线。");
      return;
    }
    state.optics[key] = e.target.checked;
    log(description + (e.target.checked ? "：接通" : "：断开"));
    updateOptics();
  };
$("quickPreview").onclick = () => {
  if (lab.removed.size) {
    toast("请先完成装配复原，再演示接线与成像。");
    return;
  }
  Object.assign(state.optics, {
    cameraOn: true,
    videoConnected: true,
    monitorOn: true,
    lightOn: true,
  });
  state.opticsInteractionVersion = 1;
  log("演示：仅连接视频线并开启相机、屏幕与钠光灯，保留光学调节参数");
  updateOptics();
  setStage(3);
  toast("接线与开机已完成，请逐项调节左侧部件，改善亮度和清晰度。");
};
setInterval(() => {
  if (
    lab &&
    !document.hidden &&
    state.optics.cameraOn &&
    state.optics.videoConnected &&
    state.optics.monitorOn
  )
    drawRings();
}, 250);
