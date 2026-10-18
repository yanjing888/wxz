import { UNITS, QUESTIONS } from "./student-course.js";
const esc = (s) =>
  String(s ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const time = (t) =>
  new Date(t).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" });
function markup(data) {
  const result = data.challenge?.result;
  return `<article class="report-document"><p>物小智 · 牛顿环实验前仿真预习</p><h1>学生预习与成像挑战报告</h1><p>姓名 / 学号：${esc(data.student)}<br>开始：${time(data.startedAt)}<br>提交：${time(data.finishedAt)}（北京时间）</p><p class="result">${data.passed ? "预习通过" : "尚未通过"} · ${data.totalScore} / 100 分</p><p>知识测验 ${data.knowledgeScore}/40 分，成像挑战 ${result?.score || 0}/60 分。要求总分 ≥80 且成像挑战通过。</p>${result?.snapshot ? `<img src="${result.snapshot}" alt="提交成像挑战时的三维场景">` : ""}<h2>安装与引导调节</h2><p>安装：${UNITS.filter(
    (u) => u.install,
  )
    .map((u) => `${data.installed.includes(u.id) ? "✓" : "○"} ${u.name}`)
    .join(
      "；",
    )}<br>引导装调：${data.guidePassed ? "通过" : "未通过"}</p><h2>预习题答案与解析</h2>${QUESTIONS.map(
    (q, i) => {
      const answer = data.answers.find((a) => a.id === q.id);
      return `<p><b>${i + 1}. [${{ single: "单选", multi: "多选", judge: "判断" }[q.type]}] ${q.question}</b><br>你的选择：${answer ? answer.selected.map((v) => esc(q.options[v])).join("；") : "未作答"}<br>结果：${answer?.correct ? "正确 · 5 分" : "未得分"}<br>正确选择：${q.answers.map((v) => q.options[v]).join("；")}<br>解析：${q.explanation}</p>`;
    },
  ).join(
    "",
  )}<h2>独立成像挑战</h2><p>用时 ${result?.durationSeconds || 0} 秒；调节 ${result?.edits || 0} 次；提交 ${data.challenge?.attempts.length || 0} 次。</p><table><tr><th>考核项目</th><th>结果</th><th>得分</th></tr>${(result?.rubric || []).map((g) => `<tr><td>${g.label}</td><td>${g.ok ? "通过" : "需继续调节"}</td><td>${g.ok ? g.points : 0}/${g.points}</td></tr>`).join("")}</table>${
    result?.optics
      ? `<h2>提交时的调节记录</h2><table><tr><th>部件 / 控制</th><th>相对位置或读数</th></tr>${[
          ["灯位", result.optics.lampPosition],
          ["半反镜倾角", result.optics.mirrorAngle + "°"],
          ["目镜视度", result.optics.reticle],
          ["物镜升降位置", result.optics.focus],
          ["CCD 镜头调焦", result.optics.cameraFocus],
          ["相机曝光", result.optics.exposure + "%"],
          ["镜头孔径", result.optics.aperture + "%"],
          ["鼓轮读数", result.optics.reading.toFixed(2) + " mm"],
          [
            "底座反光镜",
            result.optics.baseMirrorFolded ? "向内收起" : "未收起",
          ],
        ]
          .map(
            ([label, value]) =>
              `<tr><td>${label}</td><td>${esc(value)}</td></tr>`,
          )
          .join("")}</table>`
      : ""
  }<h2>操作轨迹</h2><table><tr><th>时间</th><th>操作</th><th>记录</th></tr>${data.actions.map((a) => `<tr><td>${time(a.at)}</td><td>${esc(a.action)}</td><td>${esc(a.unit || a.key || a.question || "")}${a.value !== undefined ? " → " + esc(a.value) : ""}</td></tr>`).join("")}</table><h2>模型说明</h2><p>模型外形依据厂家 MKS-NHY / JCD3 说明书和实拍图。学生操作归并为 8 个单元，支架与紧固件不单独拆装。亮度和模糊程度为教学相对模型；空气膜剖面放大。挑战初始状态与焦点参考会变化，依据最终视场独立调整。</p><p>十字丝固定，鼓轮移动时环纹相对十字丝移动。知识题与挑战结果在浏览器中生成，本文件由学生下载保存。</p></article>`;
}
export function openReport(state, lab, data) {
  document.getElementById("reportBody").innerHTML = markup(data);
  document.getElementById("reportDialog").showModal();
}
function download(content, type, extension) {
  const blob = new Blob([content], { type }),
    url = URL.createObjectURL(blob),
    a = document.createElement("a");
  a.href = url;
  a.download = `牛顿环学生预习报告_${new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Shanghai" })}.${extension}`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
export function downloadData(data) {
  download(
    JSON.stringify(data, null, 2),
    "application/json;charset=utf-8",
    "json",
  );
}
export function downloadReport(data) {
  download(
    `<!doctype html><html lang="zh-CN"><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>牛顿环学生预习报告</title><style>body{max-width:850px;margin:30px auto;padding:20px;color:#20343d;font:14px/1.85 'Microsoft YaHei',sans-serif}h1{font-size:26px}h2{font-size:18px;border-bottom:1px solid #becdd4;padding-bottom:7px}.result{color:#218476;font-size:23px}table{border-collapse:collapse;width:100%;font-size:12px}td,th{border:1px solid #b4c9d0;padding:8px;text-align:left}img{width:100%;max-height:350px;object-fit:contain;background:#102330}p{overflow-wrap:anywhere}tr{break-inside:avoid}@media print{body{margin:0}}</style>${markup(data)}</html>`,
    "text/html;charset=utf-8",
    "html",
  );
}
