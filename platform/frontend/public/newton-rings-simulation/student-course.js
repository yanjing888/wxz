export const COURSE_VERSION = 3;
export const UNITS = [
  {
    id: "sample",
    name: "牛顿环组件",
    model: "ring",
    parts: ["plate", "lens", "ring", "screws", "clip"],
    offset: [2.1, 1.4, 0.8],
    install: true,
    description:
      "已装在夹座内的平凸透镜和平面玻璃作为一个整体安装。凸面向下，薄空气膜产生干涉。",
    tip: "点击夹座模型安装；安装后左右拖动夹座微调位置，不触碰玻璃面。",
  },
  {
    id: "objective",
    name: "物镜与升降",
    model: "objective",
    parts: ["objective"],
    offset: [1.1, 1.8, 0],
    install: true,
    description: "物镜收集反射干涉光。调焦手轮带动镜筒升降，改变环纹清晰度。",
    tip: "按住物镜或调焦手轮上下拖动，改变高度；滚轮可微调。",
  },
  {
    id: "mirror",
    name: "45° 半反镜",
    model: "mirror",
    parts: ["mirror", "mirrorKnob"],
    offset: [1.6, 2.3, 0.6],
    install: true,
    description: "将水平入射的钠光反射向下，同时允许返回的干涉光透射到物镜。",
    tip: "直接按住半反镜或其旋轮拖动，改变倾角；滚轮可微调。",
  },
  {
    id: "lamp",
    name: "钠光灯",
    model: "lamp",
    parts: ["lamp", "power"],
    offset: [1.4, 0, -0.8],
    install: true,
    description:
      "提供单色照明。光源位置和半反镜共同决定光线能否有效照亮空气膜。",
    tip: "点击灯体开关灯，左右拖动灯体调整灯位；也可点击模型上的电源盒。",
  },
  {
    id: "eyepiece",
    name: "目镜与分划板",
    model: "eyepiece",
    parts: ["eyepiece", "eyeLock"],
    offset: [-1.5, 1.2, 0.8],
    install: true,
    description:
      "先通过目镜调清十字丝，再调物镜让环纹清楚。示教时此端连接 CCD 固定座。",
    tip: "目镜热区在蓝色 CCD 相机正下方的黑色调焦环。直接按住该环左右拖动，调清十字丝；滚轮可微调。",
  },
  {
    id: "camera",
    name: "CCD 示教相机",
    model: "camera",
    parts: ["adapter", "adapterLock", "rods", "zoom", "camera"],
    offset: [-1.6, 1.5, -0.5],
    install: true,
    description:
      "固定座、变焦镜头、相机作为一个示教模块安装，再接电源与视频线。",
    tip: "拖动黑色镜头下部调焦，拖动镜头中间的蓝色刻线环调孔径；相机正下方的上部环为目镜热区。拖动蓝色机身调曝光。点击机身开关电源，点击蓝色视频线接线，点击示教屏开关屏幕。",
  },
  {
    id: "baseMirror",
    name: "底座反光镜",
    model: "baseMirror",
    parts: ["baseMirror"],
    install: false,
    description:
      "底座大反光镜在本实验中应反转向内，避免反射杂散光抬高背景亮度。",
    tip: "反光镜在工作台下方，操作位置是底座前侧带青绿色圆环的小旋钮（反光镜标记下方）。直接点击该旋钮翻转镜面，比较视场背景和环纹对比度。",
  },
  {
    id: "drum",
    name: "测微鼓轮",
    model: "drum",
    parts: ["drum"],
    install: false,
    description: "带动显微镜横向移动。十字丝保持固定，环纹相对十字丝移动。",
    tip: "按住实际鼓轮左右拖动使其旋转，或在鼓轮上滚动鼠标。单击小步正转，Shift + 单击反转。",
  },
];
export const INSTALL_UNITS = UNITS.filter((u) => u.install);
export const UNIT_BY_ID = Object.fromEntries(UNITS.map((u) => [u.id, u]));
export const RAW_TO_UNIT = Object.fromEntries(
  UNITS.flatMap((u) => u.parts.map((id) => [id, u.id])),
);
Object.assign(RAW_TO_UNIT, {
  tube: "objective",
  focus: "objective",
  heightScale: "objective",
  eyeTube: "eyepiece",
  prism: "eyepiece",
  scale: "drum",
  cameraExposure: "camera",
  cameraAperture: "camera",
  cameraPower: "camera",
  cameraVideo: "camera",
  monitor: "camera",
});
export const STAGES = [
  {
    name: "逐步安装",
    title: "搭起实验装置",
    intro:
      "直接点击当前待安装的三维组件，使它就位。六个教学组件依次安装，观察光路如何接通。",
  },
  {
    name: "引导调节",
    title: "自己调出清晰环纹",
    intro:
      "按住实际三维部件拖动调节，或在部件上滚轮微调。观察十字丝、环纹和照明的实时变化。",
  },
  {
    name: "预习测验",
    title: "检验你的理解",
    intro:
      "完成单选、多选和判断题。多选题须选全且无错选，每题 5 分，共 40 分。",
  },
  {
    name: "成像挑战",
    title: "独立完成成像装调",
    intro:
      "从一组新的失调状态出发，自行调出明亮、均匀、清晰、居中的图像。挑战中不提供最佳参数。",
  },
  {
    name: "预习报告",
    title: "记录这次预习",
    intro:
      "知识测验 40 分，成像挑战 60 分。总分达到 80 分且挑战通过，才完成预习。",
  },
];
export const CONTROLS = {
  sample: [
    { key: "center", label: "夹座横向位置", min: -4, max: 4, step: 0.2 },
  ],
  lamp: [
    { key: "lampPosition", label: "灯位横向调整", min: -30, max: 30, step: 1 },
  ],
  eyepiece: [
    { key: "reticle", label: "目镜视度 · 十字丝", min: 0, max: 100, step: 1 },
  ],
  objective: [
    { key: "focus", label: "物镜升降 · 环纹焦点", min: 0, max: 100, step: 1 },
  ],
  mirror: [
    {
      key: "mirrorAngle",
      label: "半反镜倾角",
      min: 30,
      max: 60,
      step: 0.25,
      unit: "°",
    },
  ],
  camera: [
    { key: "cameraFocus", label: "CCD 镜头调焦", min: 0, max: 100, step: 1 },
    {
      key: "exposure",
      label: "曝光量（相对）",
      min: 20,
      max: 300,
      step: 5,
      unit: "%",
    },
    {
      key: "aperture",
      label: "镜头孔径",
      min: 10,
      max: 100,
      step: 1,
      unit: "%",
    },
  ],
  drum: [
    {
      key: "reading",
      label: "主尺 + 鼓轮读数",
      min: 21,
      max: 29,
      step: 0.01,
      unit: " mm",
    },
  ],
};
export const QUESTIONS = [
  {
    id: "orientation",
    type: "single",
    question: "牛顿环夹座内，平凸透镜应如何放置？",
    options: ["平面向下", "凸面向下，与平面玻璃接触", "竖直放置", "正反都可以"],
    answers: [1],
    explanation:
      "凸面与平面玻璃之间形成由中心向外增厚的薄空气膜。学生安装时移动整个夹座即可。",
  },
  {
    id: "beams",
    type: "single",
    question: "产生牛顿环干涉的两束光来自哪里？",
    options: [
      "空气膜上下两界面的反射",
      "相机与显示器的反射",
      "两盏不同的灯",
      "底座与支架的反光",
    ],
    answers: [0],
    explanation: "同一光束在空气膜上下界面分成两束反射光，再叠加产生干涉。",
  },
  {
    id: "focus",
    type: "multi",
    question: "哪些调节会直接影响观察到的清晰度？",
    options: [
      "目镜视度",
      "物镜与组件之间的距离",
      "CCD 镜头焦点",
      "仅提高曝光就能修正离焦",
    ],
    answers: [0, 1, 2],
    explanation:
      "目镜调清十字丝，物镜调清环纹，CCD 镜头把光学像清楚地成像到传感器上；曝光不能修正离焦。",
  },
  {
    id: "exposure",
    type: "judge",
    question: "只要把相机曝光调得足够高，模糊的环纹也会变清晰。",
    options: ["正确", "错误"],
    answers: [1],
    explanation:
      "曝光改变亮度，离焦需要通过光学调焦修正。过曝还会损失高光细节。",
  },
  {
    id: "illumination",
    type: "multi",
    question: "哪些操作有助于改善照明均匀性或环纹对比度？",
    options: [
      "微调 45° 半反镜",
      "调整钠光灯的位置",
      "把底座大反光镜反转向内",
      "改变仪器铭牌位置",
    ],
    answers: [0, 1, 2],
    explanation: "前两项改善入射照明，收起底座反光镜可以减少背景杂散光。",
  },
  {
    id: "drum",
    type: "judge",
    question: "转动测微鼓轮时，十字丝本身随鼓轮在视场中移动。",
    options: ["正确", "错误"],
    answers: [1],
    explanation:
      "十字丝固定在观察系统内。移动显微镜时，被观察到的环纹相对十字丝移动。",
  },
  {
    id: "dark",
    type: "single",
    question: "理想接触、反射光观察时，中心暗斑的主要原因是什么？",
    options: [
      "中心没有光照",
      "透镜中心完全吸光",
      "两束反射光有半波损失并相消",
      "中心空气膜最厚",
    ],
    answers: [2],
    explanation:
      "中心膜厚近似为零，但一束反射光发生 π 相位变化，导致反射光相消。",
  },
  {
    id: "operation",
    type: "multi",
    question: "哪些是正确的实验操作？",
    options: [
      "同一读数行程尽量保持鼓轮同向转动",
      "避免物镜碰到牛顿环组件",
      "用夹座边缘拿取组件",
      "反复中途返向不会带来读数误差",
    ],
    answers: [0, 1, 2],
    explanation: "同向测量减少螺纹空程差；光学组件应避免碰撞和徒手触摸镜面。",
  },
];
export function gradeAnswer(question, selected) {
  const actual = [...new Set(selected)].sort((a, b) => a - b);
  return (
    actual.length === question.answers.length &&
    actual.every((a, i) => a === [...question.answers].sort((a, b) => a - b)[i])
  );
}
export function makeChallenge(random = Math.random) {
  const between = (a, b) => Math.round(a + (b - a) * random());
  return {
    focusTarget: between(45, 60),
    reticleTarget: between(44, 57),
    cameraFocusTarget: between(43, 59),
    ringOrigin: 25 + between(-35, 35) / 100,
    focus: between(12, 28),
    reticle: between(72, 88),
    cameraFocus: between(16, 30),
    mirrorAngle: random() < 0.5 ? 38 : 52,
    lampPosition: random() < 0.5 ? -15 : 15,
    baseMirrorFolded: false,
    reading: random() < 0.5 ? 23.9 : 26.1,
    exposure: between(190, 260),
    aperture: between(45, 65),
    lightOn: true,
    cameraOn: true,
    videoConnected: true,
    monitorOn: true,
    observationMode: "camera",
    center: 0,
    lampPower: 100,
    showRays: false,
    xray: false,
  };
}
