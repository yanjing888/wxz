const lessons = [
  {
    title: "光从哪里来？",
    path: "电能 → 激发钠原子 → 发出黄光",
    text: "钠光灯放电使钠原子被激发，原子回到较低能级时发出特定波长的光。本实验用约 589.3 nm 的黄光作近似单色照明，便于观察清晰、稳定的干涉环。",
    observation: "光路是光传播路径的示意，不是实际存在的彩色线。模型中的颜色用于区分传播方向和不同光束。",
    question: "为什么不用普通白光直接替代钠光？",
    answer: "白光包含多种波长，不同波长产生的环纹位置不同，叠加后较高阶环纹容易模糊。近似单色光更便于辨认和测量环纹。",
  },
  {
    title: "光如何到达空气膜？",
    path: "钠光灯 → 45° 半反镜 → 牛顿环空气膜",
    text: "光在均匀介质中沿直线传播，遇到界面会发生反射和透射。半反镜把一部分水平入射光反射向下，使空气膜得到照明；返回的光又有一部分能透过半反镜进入观察光路。",
    observation: "在练习中改变灯位或半反镜角度，观察照明变化。约 45° 的安装角使水平光转向竖直方向；半反镜本身不产生光。",
    question: "开了灯，为什么仍可能看不到清楚的环纹？",
    answer: "开灯只说明有光源。灯位偏离、半反镜方向不合适、光学组件未装好或成像未调清，都可能影响观察。应沿光传播的顺序逐项检查。",
  },
  {
    title: "两束光如何形成明暗环？",
    path: "空气膜上界面反射 ＋ 下界面反射 → 叠加干涉",
    text: "平凸透镜的凸面与平面玻璃之间形成厚度向外逐渐增大的空气膜。光在空气膜上下界面反射后，两束光叠加；不同位置的膜厚带来不同的光程差，形成明暗相间的等厚干涉环。",
    observation: "在近垂直入射、空气膜且接触良好的条件下，其中一次反射有半波损失，接触中心为暗斑。反射暗环满足 2t = mλ，结合 t ≈ r²/(2R)，得到 rₘ² = mλR。λ 为波长，R 为透镜曲率半径。",
    question: "为什么看到的是圆环，而不是均匀亮面？",
    answer: "理想球面透镜下，与接触点距离相同的位置具有相同膜厚，因此干涉条件相同的位置连成圆。环纹来自光的叠加，不是玻璃上刻出的圆圈。",
  },
  {
    title: "环纹如何变成屏幕图像？",
    path: "反射干涉光 → 半反镜透射 → 物镜与观察端 → CCD → 显示器",
    text: "物镜收集返回的光并形成环纹图像，观察端用于目视或接入示教相机。CCD 将光信号转换为电信号，经视频连接传到显示器。屏幕显示的是成像结果，并非光直接沿视频线传播。",
    observation: "先调清十字丝，再调清环纹；使用相机时还要检查相机调焦、电源、视频线和显示器。光路畅通与视频信号接通是两件事。",
    question: "屏幕无信号，是否一定是空气膜没有干涉？",
    answer: "不是。相机未通电、视频线未连接或显示器关闭，也会导致无图像。应先区分视频信号问题和光学成像问题。",
  },
];

export function initLightPathLearning(host) {
  host.innerHTML = `<p class="learning-intro">沿着光的传播，理解牛顿环如何形成。</p><nav class="learning-steps" aria-label="光路学习步骤">${lessons.map((lesson, i) => `<button type="button" data-lesson="${i}" aria-pressed="false">${i + 1}. ${lesson.title}</button>`).join("")}</nav><article class="learning-content" aria-live="polite"></article><div class="view-options"><button type="button" data-view="optics">观察光学组件</button><button type="button" data-view="film">查看空气膜剖面</button></div><p class="learning-intro">可在“视图选项”中显示光路、开启镜筒透视，再回到练习观察。</p>`;
  const show = (index) => {
    const lesson = lessons[index];
    host.querySelectorAll("[data-lesson]").forEach((button) => button.setAttribute("aria-pressed", String(Number(button.dataset.lesson) === index)));
    host.querySelector("article").innerHTML = `<h3>${lesson.title}</h3><p class="learning-path">${lesson.path}</p><p>${lesson.text}</p><p class="learning-observation"><b>观察与理解</b><br>${lesson.observation}</p><details><summary>想一想：${lesson.question}</summary><p>${lesson.answer}</p></details>`;
  };
  host.querySelectorAll("[data-lesson]").forEach((button) => { button.onclick = () => show(Number(button.dataset.lesson)); });
  show(0);
}
