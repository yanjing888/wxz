import * as T from "./vendor/three.module.js";
import { OrbitControls } from "./vendor/OrbitControls.js";
import { DEFAULT_OPTICS, opticalMetrics } from "./optics.js";
import { createWorkbench } from "./workbench.js";

// Reference numbers refer to PDF page 4 (JCD3) and page 15 (JCD-CCD).
const specs = [
  [
    "eyepiece",
    "目镜 / 示教接口",
    "JCD3 · 1",
    "目镜用于视度调节。当前实拍配置是示教模式，目镜取下，固定座套在目镜筒上。",
    "目视时先调清分划板；示教时先固定相机接口。",
    "microscope",
  ],
  [
    "eyeLock",
    "目镜锁紧螺钉",
    "JCD3 · 2",
    "固定目镜或目镜接口，防止观察过程中位置变化。",
    "轻轻锁紧，不用过大力。",
    "microscope",
  ],
  [
    "eyeTube",
    "45° 目镜镜筒",
    "JCD3 · 3",
    "连接棱镜室与目镜端，形成厂家规定的 45° 斜视观察方向。",
    "示教系统沿其光轴安装。",
    "microscope",
  ],
  [
    "prism",
    "棱镜室",
    "JCD3 · 4",
    "将竖直成像光路折转至 45° 斜视端。棱镜室可绕竖轴转动。",
    "先松锁紧螺钉，再旋转至合适观察方向。",
    "microscope",
  ],
  [
    "prismLock",
    "棱镜室锁紧螺钉",
    "JCD3 · 5",
    "锁定棱镜室的方位。",
    "调节方位后重新止紧。",
    "microscope",
  ],
  [
    "heightScale",
    "升降刻尺",
    "JCD3 · 6",
    "配合调焦机构显示镜筒高度变化，厂家升降范围 40 mm。",
    "升降方向最小读数 0.1 mm。",
    "microscope",
  ],
  [
    "tube",
    "竖直镜筒",
    "JCD3 · 7",
    "保持物镜、分划板与棱镜室的相对关系，通过调焦手轮升降。",
    "缓慢向上寻找清晰环纹，避免物镜碰撞组件。",
    "microscope",
  ],
  [
    "objective",
    "物镜组",
    "JCD3 · 8",
    "收集空气膜两界面的反射光并放大成像。厂家物镜 3×，目镜 10×。",
    "镜筒离焦使环纹模糊，曝光无法代替调焦。",
    "microscope",
  ],
  [
    "mirror",
    "45° 半反镜组",
    "JCD3 · 9",
    "把水平钠光部分反射向下；空气膜返回的干涉光部分透射向上进入物镜。",
    "微调方向，使光线近似垂直入射空气膜。",
    "microscope",
  ],
  [
    "mirrorKnob",
    "半反镜旋轮",
    "JCD3 · 10",
    "微调半反镜方位，改变照明光的方向与覆盖区域。",
    "偏转会造成偏暗、照明不均，甚至无环纹。",
    "microscope",
  ],
  [
    "clip",
    "被测件压片",
    "JCD3 · 11",
    "把牛顿环组件固定在工作台面，外形按实拍金属压片还原。",
    "压稳夹座，不触碰光学面。",
    "microscope",
  ],
  [
    "baseMirror",
    "底座反光镜与旋轮",
    "JCD3 · 12",
    "底座中大反光镜用于其他观察任务。牛顿环实验中应反转向内。",
    "未收起会使杂散光向上照入，背景泛白、对比度下降。",
    "microscope",
  ],
  [
    "focus",
    "调焦手轮",
    "JCD3 · 13",
    "转动时带动镜筒升降，改变空气膜像与分划板像面的匹配。",
    "先调叉丝，再调环纹；注意上升寻像。",
    "microscope",
  ],
  [
    "scale",
    "纵向标尺",
    "JCD3 · 14",
    "读数的整数部分在主尺上读取，小数部分由测微鼓轮读取。",
    "主尺与鼓轮之和为当前位置读数。",
    "microscope",
  ],
  [
    "drum",
    "测微鼓轮",
    "JCD3 · 15",
    "驱动显微镜沿导轨移动，测量不同暗环的左右位置。",
    "纵向范围 50 mm，最小读数 0.01 mm；同向测量避免空程差。",
    "microscope",
  ],
  [
    "lockI",
    "锁紧手轮 I",
    "JCD3 · 16",
    "固定方轴与接头轴的连接关系。",
    "松开时托住相应部件。",
    "microscope",
  ],
  [
    "joint",
    "接头轴",
    "JCD3 · 17",
    "安装在底座中，可旋转、升降以调整整套读数系统。",
    "完成定位后由锁紧手轮 II 固定。",
    "microscope",
  ],
  [
    "squareAxis",
    "方轴 / 导轨支承",
    "JCD3 · 18",
    "方轴穿入接头轴十字孔，承载测量滑台。",
    "本实验保持镜筒竖直，测量方向穿过环心。",
    "microscope",
  ],
  [
    "lockII",
    "锁紧手轮 II",
    "JCD3 · 19",
    "锁定接头轴在底座中的高度与方位。",
    "避免松动带来漂移和振动。",
    "microscope",
  ],
  [
    "base",
    "矩形仪器底座",
    "JCD3 · 20",
    "按实拍还原银灰色铸造底座、台面和支承结构。厂家主机外形 195×155×285 mm。",
    "安放平稳，避开振动与碰撞。",
    "microscope",
  ],
  [
    "adapter",
    "示教固定座",
    "JCD-CCD · 1",
    "套在目镜筒上，支承变焦镜头和摄像机。",
    "与双十字分划板的竖直方向保持一致。",
    "camera",
  ],
  [
    "adapterLock",
    "固定座锁紧螺丝",
    "JCD-CCD · 2",
    "蓝色手拧螺丝锁紧示教固定座。",
    "确认连接稳固后再连接线缆。",
    "camera",
  ],
  [
    "rods",
    "示教连接杆",
    "JCD-CCD · 3",
    "实拍中的金属连接杆把相机挂板与固定座连接，保持同轴。",
    "调整距离与方向后止紧。",
    "camera",
  ],
  [
    "camera",
    "蓝色示教相机",
    "JCD-CCD · 4",
    "把光学像转换为视频信号，经 HDMI 或 USB 输出到显示器。",
    "曝光影响亮度；过曝会截断高光细节。",
    "camera",
  ],
  [
    "zoom",
    "变焦镜头",
    "JCD-CCD · 5",
    "厂家镜头焦距 2.8–12 mm，带调焦和孔径调节环。",
    "焦距改变视场，镜头调焦改变清晰度，孔径影响通光量。",
    "camera",
  ],
  [
    "monitor",
    "示教显示器",
    "JCD-CCD · 6",
    "21.5 英寸、1080P，采用 16:9 视频输出。",
    "图像亮度和清晰度要在上游光学系统及相机设置中调整。",
    "camera",
  ],
  [
    "plate",
    "平面玻璃板",
    "牛顿环组件",
    "作为空气膜下界面，与平凸透镜凸面保持接触。",
    "拿边缘，保持镜面清洁。",
    "sample",
  ],
  [
    "lens",
    "平凸透镜",
    "牛顿环组件 · R=1000 mm",
    "凸面向下，在平面玻璃上形成中心薄、周边厚的空气膜。",
    "中心为点接触；剖面视图中膜厚与曲率已放大。",
    "sample",
  ],
  [
    "ring",
    "牛顿环夹座 / 压环",
    "牛顿环组件",
    "按照片还原黑色圆形夹座与中央通光孔。",
    "固定适度，不对玻璃施加过大的力。",
    "sample",
  ],
  [
    "screws",
    "夹座固定螺钉",
    "牛顿环组件",
    "夹座边缘三个固定点用于结构拆装演示。",
    "虚拟拆装用于认识结构；实物操作遵循说明书与教师指导。",
    "sample",
  ],
  [
    "lamp",
    "钠光灯灯箱",
    "照明系统",
    "按实拍还原米白色立式灯箱、观察窗口、散热孔和支架。",
    "将灯光水平对准半反镜；照明位置直接影响视场均匀性。",
    "light",
  ],
  [
    "power",
    "钠灯电源与线缆",
    "照明系统",
    "为钠光灯供电，连接相机的蓝色视频线按实拍布置。",
    "预习中模拟即时点亮，真实钠灯须预热。",
    "light",
  ],
];
export const PARTS = specs.map(
  ([id, name, reference, description, tip, category], i) => ({
    id,
    name,
    reference,
    description,
    tip,
    category,
    en: reference,
    number: i + 1,
  }),
);
export const CATEGORIES = {
  all: "全部部件",
  microscope: "读数显微镜",
  camera: "示教系统",
  sample: "牛顿环组件",
  light: "照明系统",
};

export function createLab(
  host,
  onSelect,
  onAdjust = () => false,
  onInteract = () => false,
) {
  let studentMode = false;
  const renderer = new T.WebGLRenderer({
    alpha: true,
    antialias: true,
    preserveDrawingBuffer: true,
  });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = T.PCFSoftShadowMap;
  renderer.toneMapping = T.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.1;
  host.prepend(renderer.domElement);
  const scene = new T.Scene(),
    camera = new T.PerspectiveCamera(35, 1, 0.05, 100),
    controls = new OrbitControls(camera, renderer.domElement);
  camera.position.set(8, 5.5, 10);
  controls.target.set(0.6, 2, 0);
  controls.enableDamping = true;
  controls.minDistance = 1.8;
  controls.maxDistance = 24;
  controls.maxPolarAngle = Math.PI * 0.88;
  scene.add(new T.HemisphereLight(0xe7f2ff, 0x283342, 2.2));
  const light = new T.DirectionalLight(0xfff4e5, 3);
  light.position.set(4, 9, 6);
  light.castShadow = true;
  light.shadow.mapSize.set(2048, 2048);
  Object.assign(light.shadow.camera, {
    left: -7,
    right: 7,
    top: 8,
    bottom: -7,
  });
  scene.add(light);
  const back = new T.DirectionalLight(0xbcd7ef, 2);
  back.position.set(-5, 4, -5);
  scene.add(back);
  const material = (color, metalness = 0.35, roughness = 0.4) =>
    new T.MeshStandardMaterial({ color, metalness, roughness });
  const silver = material(0xa8aaa4, 0.65, 0.34),
    aluminum = material(0xd5d7d2, 0.62, 0.3),
    black = material(0x171a1b, 0.25, 0.45),
    ivory = material(0xd9d6c6, 0.2, 0.55),
    blue = material(0x0876b5, 0.6, 0.29),
    chrome = material(0xb9c5c9, 0.95, 0.18),
    brass = material(0x9b845b, 0.6, 0.35),
    glass = new T.MeshPhysicalMaterial({
      color: 0xaed3ce,
      roughness: 0.08,
      transparent: true,
      opacity: 0.32,
      side: T.DoubleSide,
      depthWrite: false,
    });
  const groups = {},
    offsets = {},
    labels = {},
    removed = new Set();
  let o = { ...DEFAULT_OPTICS },
    explode = 0,
    targetExplode = 0,
    selected = "base",
    labelOn = true,
    xray = false,
    filmMode = false,
    targetView = null;
  function mesh(g, geo, mat, pos = [0, 0, 0], rot = [0, 0, 0]) {
    const m = new T.Mesh(geo, mat);
    m.position.set(...pos);
    m.rotation.set(...rot);
    m.castShadow = true;
    m.receiveShadow = true;
    g.add(m);
    return m;
  }
  const box = (g, w, h, d, p, mat = silver) =>
    mesh(g, new T.BoxGeometry(w, h, d), mat, p);
  const cyl = (g, r, h, p, mat = silver, rot = [0, 0, 0]) =>
    mesh(g, new T.CylinderGeometry(r, r, h, 48), mat, p, rot);
  function ring(g, r1, r2, h, p, mat = black) {
    return mesh(
      g,
      new T.LatheGeometry(
        [
          [r1, -h / 2],
          [r2, -h / 2],
          [r2, h / 2],
          [r1, h / 2],
          [r1, -h / 2],
        ].map(([x, y]) => new T.Vector2(x, y)),
        64,
      ),
      mat,
      p,
    );
  }
  const opticalOrigin = new T.Vector3(0.52, 2.95, 0.25),
    axis = new T.Vector3(-Math.SQRT1_2, Math.SQRT1_2, 0);
  function group(id, pos, offset = [0, 0, 0], angle = 0) {
    const g = new T.Group();
    g.position.set(...pos);
    g.rotation.z = angle;
    g.userData.home = g.position.clone();
    g.userData.anchor = new T.Vector3(0, 0.16, 0);
    groups[id] = g;
    offsets[id] = new T.Vector3(...offset);
    scene.add(g);
    return g;
  }
  function angled(id, d, offset) {
    const p = opticalOrigin.clone().addScaledVector(axis, d);
    return group(id, p.toArray(), offset, Math.PI / 4);
  }
  function screw(g, pos, size = 0.04, mat = chrome) {
    cyl(g, size, 0.025, pos, mat, [Math.PI / 2, 0, 0]);
    box(g, size * 1.3, 0.009, 0.008, [pos[0], pos[1], pos[2] + 0.018], black);
  }
  function knob(g, r = 0.17) {
    cyl(g, r, 0.12, [0, 0, 0], black, [Math.PI / 2, 0, 0]);
    cyl(g, r * 0.38, 0.13, [0, 0, 0.015], brass, [Math.PI / 2, 0, 0]);
    for (let i = 0; i < 32; i++) {
      const a = (i * Math.PI) / 16;
      box(g, 0.018, 0.03, 0.11, [r * Math.cos(a), r * Math.sin(a), 0], black);
    }
  }
  function text(
    g,
    value,
    w,
    h,
    pos,
    rot = [0, 0, 0],
    bg = "#a79b7b",
    fg = "#182328",
  ) {
    const c = document.createElement("canvas");
    c.width = 512;
    c.height = 128;
    const ctx = c.getContext("2d");
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, 512, 128);
    ctx.fillStyle = fg;
    ctx.font = "28px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(value, 256, 82);
    const texture = new T.CanvasTexture(c);
    texture.colorSpace = T.SRGBColorSpace;
    return mesh(
      g,
      new T.PlaneGeometry(w, h),
      new T.MeshBasicMaterial({ map: texture, side: T.DoubleSide }),
      pos,
      rot,
    );
  }
  function cable(g, points, color, r = 0.025) {
    const curve = new T.CatmullRomCurve3(
      points.map((p) => new T.Vector3(...p)),
    );
    return mesh(
      g,
      new T.TubeGeometry(curve, 48, r, 8, false),
      material(color, 0.15, 0.65),
    );
  }
  let g = group("base", [0, 0, 0]);
  box(g, 3.25, 0.48, 2.45, [0, 0.27, 0], silver);
  box(g, 2.9, 0.12, 2.2, [0, 0.56, 0], aluminum);
  const slidePedestal = box(g, 1.28, 0.22, 1.2, [-0.82, 0.73, -0.2], silver);
  for (const x of [-1.25, 1.25])
    for (const z of [-0.95, 0.95]) cyl(g, 0.12, 0.13, [x, 0.02, z], black);
  text(g, "JCD3   MKS-NHY", 1.1, 0.17, [0, 0.32, 1.234]);
  for (const x of [-1.45, 1.45]) screw(g, [x, 0.4, 1.24]);
  g = group("joint", [-1.0, 1.01, -0.3], [-1.2, 0.2, -0.4]);
  cyl(g, 0.33, 0.63, [0, 0, 0]);
  box(g, 0.62, 0.35, 0.63, [0, 0.32, 0]);
  g = group("squareAxis", [-0.43, 1.35, -0.3], [-1.1, 0, -1.0]);
  box(g, 1.8, 0.17, 0.2, [0, 0, 0], chrome);
  cyl(g, 0.065, 2, [0, -0.16, 0.25], chrome, [0, 0, Math.PI / 2]);
  g = group("lockI", [-1, 1.42, 0.11], [-1.6, 0.8, 0.4]);
  knob(g, 0.22);
  g = group("lockII", [-1.13, 0.82, 0.48], [-1.6, 0, 0.5]);
  knob(g, 0.23);
  g = group("scale", [-0.05, 1.53, -0.18], [-0.8, 0.65, -0.8]);
  box(g, 1.63, 0.58, 0.62, [0, 0, 0]);
  const ruler = text(
    g,
    "0   10   20   30   40   50",
    1.45,
    0.19,
    [0, 0.302, 0.04],
    [-Math.PI / 2, 0, 0],
  );
  for (let i = 0; i <= 50; i++)
    box(
      g,
      0.008,
      0.006,
      i % 5 === 0 ? 0.09 : 0.045,
      [-0.7 + i * 0.028, 0.31, 0.12],
      black,
    );
  g = group("drum", [0.87, 1.5, -0.18], [1.2, 0.3, 0.75]);
  cyl(g, 0.32, 0.2, [0, 0, 0], aluminum, [0, 0, Math.PI / 2]);
  cyl(g, 0.12, 0.25, [0.19, 0, 0], black, [0, 0, Math.PI / 2]);
  for (let i = 0; i < 50; i++) {
    const a = (i * Math.PI) / 25;
    box(
      g,
      0.08,
      0.007,
      0.012,
      [0.08, 0.322 * Math.cos(a), 0.322 * Math.sin(a)],
      black,
    );
  }
  text(g, "0  10  20", 0.3, 0.1, [0.11, 0.02, 0.324]);
  // The real carriage runs crosswise to the inclined viewing head. Rotate the
  // complete carriage and its supports in the horizontal plane, not the camera.
  const carriagePivot = new T.Vector3(-0.05, 0, -0.18);
  const carriageMount = new T.Quaternion().setFromAxisAngle(
    new T.Vector3(0, 1, 0),
    -Math.PI / 2,
  );
  for (const id of [
    "scale",
    "drum",
    "squareAxis",
    "joint",
    "lockI",
    "lockII",
  ]) {
    const part = groups[id];
    part.position
      .sub(carriagePivot)
      .applyQuaternion(carriageMount)
      .add(carriagePivot);
    part.quaternion.premultiply(carriageMount);
    part.userData.home.copy(part.position);
    offsets[id].applyQuaternion(carriageMount);
  }
  slidePedestal.position
    .sub(carriagePivot)
    .applyQuaternion(carriageMount)
    .add(carriagePivot);
  slidePedestal.quaternion.premultiply(carriageMount);
  const drumMount = groups.drum.quaternion.clone();
  g = group("tube", [0.52, 2.19, 0.25], [0, 1.3, 0]);
  cyl(g, 0.31, 1.36, [0, 0, 0], aluminum);
  ring(g, 0.31, 0.34, 0.09, [0, 0.6, 0], silver);
  ring(g, 0.31, 0.34, 0.09, [0, -0.6, 0], black);
  box(g, 0.25, 1.25, 0.25, [-0.28, 0, -0.05], silver);
  g = group("heightScale", [0.24, 2.17, 0.52], [-0.7, 1.3, 0.6]);
  box(g, 0.08, 1.15, 0.025, [0, 0, 0], brass);
  for (let i = 0; i < 40; i++)
    box(
      g,
      i % 5 === 0 ? 0.065 : 0.035,
      0.005,
      0.01,
      [0.01, -0.52 + i * 0.026, 0.02],
      black,
    );
  g = group("focus", [0.05, 2.4, 0.53], [-1.1, 1.6, 0.5]);
  knob(g, 0.22);
  cyl(g, 0.055, 0.45, [0, 0, -0.2], chrome, [Math.PI / 2, 0, 0]);
  g = group("prism", [0.52, 2.98, 0.25], [-0.4, 2.15, 0]);
  cyl(g, 0.42, 0.23, [0, 0, 0], aluminum);
  mesh(
    g,
    new T.SphereGeometry(0.4, 40, 24, 0, Math.PI * 2, 0, Math.PI / 2),
    aluminum,
    [0, 0.09, 0],
  );
  ring(g, 0.385, 0.42, 0.02, [0, 0.02, 0], black);
  g = group("prismLock", [0.52, 3.02, 0.69], [-0.4, 2.1, 0.6]);
  screw(g, [0, 0, 0], 0.055, blue);
  g = angled("eyeTube", 0.46, [-1.0, 2.55, 0]);
  cyl(g, 0.22, 0.64, [0, 0, 0], aluminum);
  ring(g, 0.22, 0.25, 0.09, [0, 0.29, 0], black);
  g = angled("eyepiece", 0.82, [-1.4, 2.7, 0]);
  const eyepieceDial = new T.Group();
  g.add(eyepieceDial);
  // Keep the student eyepiece control immediately below the CCD housing.
  eyepieceDial.position.y = 0.78;
  g.userData.anchor.set(0, 0.78, 0.31);
  ring(eyepieceDial, 0.25, 0.31, 0.13, [0, 0, 0], black);
  ring(eyepieceDial, 0.306, 0.315, 0.025, [0, 0.055, 0], blue);
  box(eyepieceDial, 0.025, 0.1, 0.025, [0, 0, 0.315], ivory);
  const hitMaterial = new T.MeshBasicMaterial({
    transparent: true,
    opacity: 0,
    depthWrite: false,
  });
  ring(eyepieceDial, 0.25, 0.34, 0.16, [0, 0, 0], hitMaterial);
  text(g, "JCD3", 0.22, 0.07, [0, 0, 0.225]);
  g = angled("eyeLock", 0.67, [-1.3, 2.2, 0.6]);
  screw(g, [0, 0, 0.24], 0.045, blue);
  g = angled("adapter", 0.91, [-1.6, 2.8, 0]);
  box(g, 0.56, 0.34, 0.5, [0, 0, 0], black);
  ring(g, 0.22, 0.27, 0.12, [0, -0.17, 0], silver);
  g = angled("adapterLock", 0.92, [-1.9, 2.8, 0.45]);
  cyl(g, 0.075, 0.11, [0.25, 0, 0.31], blue, [Math.PI / 2, 0, 0]);
  g = angled("zoom", 1.48, [-1.8, 3.1, 0]);
  cyl(g, 0.25, 0.67, [0, 0, 0], black);
  const cameraFocusDial = new T.Group();
  g.add(cameraFocusDial);
  ring(cameraFocusDial, 0.25, 0.285, 0.12, [0, -0.2, 0], black);
  box(cameraFocusDial, 0.025, 0.1, 0.02, [0, -0.2, 0.29], ivory);
  const apertureDial = new T.Group();
  g.add(apertureDial);
  ring(apertureDial, 0.25, 0.285, 0.12, [0, -0.02, 0], black);
  box(apertureDial, 0.025, 0.1, 0.02, [0, -0.02, 0.29], blue);
  apertureDial.traverse((m) => {
    if (m.isMesh) m.userData.part = "cameraAperture";
  });
  text(
    g,
    "2.8–12 mm  CS",
    0.39,
    0.1,
    [0, 0.0, 0.258],
    [0, 0, 0],
    "#15191b",
    "#c3d0d4",
  );
  screw(g, [0.12, -0.16, 0.27], 0.025);
  g = angled("rods", 1.25, [-1.4, 3.2, -0.65]);
  for (const z of [-0.34, 0.34]) cyl(g, 0.035, 1.72, [0.37, 0, z], chrome);
  box(g, 0.65, 0.08, 0.83, [0.08, 0.73, 0], black);
  box(g, 0.57, 0.12, 0.78, [0.1, -0.58, 0], black);
  cyl(g, 0.065, 0.12, [0.35, -0.58, 0.45], blue, [Math.PI / 2, 0, 0]);
  g = angled("camera", 2.1, [-2.2, 3.9, 0]);
  box(g, 0.62, 0.73, 0.58, [0, 0, 0], blue);
  box(g, 0.66, 0.13, 0.62, [0, 0.42, 0], black);
  box(g, 0.66, 0.09, 0.62, [0, -0.4, 0], black);
  for (let i = 0; i < 8; i++)
    box(g, 0.012, 0.65, 0.01, [-0.26 + i * 0.074, 0, 0.295], chrome);
  cyl(g, 0.07, 0.1, [0.12, 0.54, 0], black);
  const exposureDial = new T.Group();
  g.add(exposureDial);
  cyl(exposureDial, 0.085, 0.08, [0.34, 0.1, 0.14], black, [0, 0, Math.PI / 2]);
  box(exposureDial, 0.025, 0.09, 0.025, [0.39, 0.1, 0.14], ivory);
  exposureDial.traverse((m) => {
    if (m.isMesh) m.userData.part = "cameraExposure";
  });
  cyl(g, 0.055, 0.025, [0.18, -0.23, 0.31], ivory, [
    Math.PI / 2,
    0,
    0,
  ]).userData.part = "cameraPower";
  const unpluggedVideoTail = cable(
    g,
    [
      [0.12, 0.58, 0],
      [0.25, 0.95, 0.2],
      [0.85, 0.95, 0.4],
      [1.5, 0.4, 0.4],
    ],
    0x0879b1,
    0.04,
  );
  unpluggedVideoTail.traverse((m) => {
    if (m.isMesh) m.userData.part = "cameraVideo";
  });
  g = group("objective", [0.52, 1.42, 0.25], [0, 0.7, 0]);
  cyl(g, 0.17, 0.2, [0, 0, 0], black);
  ring(g, 0.16, 0.2, 0.07, [0, 0.07, 0], chrome);
  cyl(g, 0.14, 0.015, [0, -0.11, 0], glass);
  g = group("mirror", [0.52, 1.22, 0.25], [1.25, 0.8, 0]);
  const pivot = new T.Group();
  g.add(pivot);
  pivot.rotation.z = -Math.PI / 4;
  box(pivot, 0.035, 0.38, 0.34, [0, 0, 0], glass);
  box(pivot, 0.06, 0.42, 0.025, [0, 0, 0.18], black);
  box(pivot, 0.06, 0.42, 0.025, [0, 0, -0.18], black);
  box(g, 0.09, 0.08, 0.29, [0, 0.15, -0.15], black);
  g = group("mirrorKnob", [0.52, 1.23, 0.48], [1.5, 0.8, 0.5]);
  knob(g, 0.065);
  g = group("plate", [0.52, 0.73, 0.25], [2.1, 0.2, 0.5]);
  cyl(g, 0.43, 0.05, [0, 0, 0], glass);
  ring(g, 0.42, 0.46, 0.05, [0, 0, 0], black);
  g = group("lens", [0.52, 0.76, 0.25], [2.1, 0.95, 0.5]);
  const profile = [new T.Vector2(0, 0)];
  for (let i = 1; i <= 32; i++) {
    const r = (0.39 * i) / 32;
    profile.push(new T.Vector2(r, 0.045 * (r / 0.39) ** 2));
  }
  profile.push(new T.Vector2(0.39, 0.085), new T.Vector2(0, 0.085));
  mesh(g, new T.LatheGeometry(profile, 64), glass);
  const fringeMat = new T.ShaderMaterial({
    transparent: true,
    side: T.DoubleSide,
    depthWrite: false,
    uniforms: { brightness: { value: 0 }, contrast: { value: 1 } },
    vertexShader:
      "varying vec2 v;void main(){v=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}",
    fragmentShader:
      "varying vec2 v;uniform float brightness;uniform float contrast;void main(){vec2 p=(v-.5)*4.8;float i=mix(.5,.5-.5*cos(6.2831853*dot(p,p)/.5893),contrast);gl_FragColor=vec4(vec3(1.,.75,.22)*i*brightness,brightness*.9);}",
  });
  const fringe = mesh(
    g,
    new T.CircleGeometry(0.37, 64),
    fringeMat,
    [0, 0.087, 0],
    [-Math.PI / 2, 0, 0],
  );
  g = group("ring", [0.52, 0.78, 0.25], [2.1, 1.6, 0.5]);
  ring(g, 0.365, 0.53, 0.16, [0, 0, 0], black);
  g = group("screws", [0.52, 0.9, 0.25], [2.1, 2.2, 0.5]);
  for (let i = 0; i < 3; i++) {
    const a = (i * Math.PI * 2) / 3;
    cyl(g, 0.035, 0.09, [0.46 * Math.cos(a), 0, 0.46 * Math.sin(a)], chrome);
    cyl(
      g,
      0.06,
      0.025,
      [0.46 * Math.cos(a), 0.055, 0.46 * Math.sin(a)],
      chrome,
    );
  }
  g = group("clip", [0.02, 0.71, 0.63], [1.2, 0.2, 1.3]);
  const clip = box(g, 0.94, 0.035, 0.12, [0, 0, 0], chrome);
  clip.rotation.y = 0.35;
  cyl(g, 0.075, 0.06, [-0.41, 0.025, 0.14], silver);
  g = group("baseMirror", [0.52, 0.47, 0.25], [0, -0.15, 1.4]);
  const basePivot = new T.Group();
  g.add(basePivot);
  mesh(
    basePivot,
    new T.CircleGeometry(0.38, 48),
    chrome,
    [0, 0, 0],
    [-Math.PI / 2, 0, 0],
  );
  ring(basePivot, 0.38, 0.42, 0.04, [0, 0, 0], black);
  // The mirror itself is beneath the stage; its front-panel wheel is the handle.
  const baseMirrorDial = new T.Group();
  g.add(baseMirrorDial);
  baseMirrorDial.position.set(0, -0.2, 1.07);
  cyl(baseMirrorDial, 0.15, 0.18, [0, 0, 0], silver, [Math.PI / 2, 0, 0]);
  box(baseMirrorDial, 0.025, 0.1, 0.02, [0, 0.02, 0.102], black);
  cyl(baseMirrorDial, 0.235, 0.23, [0, 0, 0.02], hitMaterial, [
    Math.PI / 2,
    0,
    0,
  ]);
  const baseMirrorHalo = mesh(
    g,
    new T.TorusGeometry(0.19, 0.012, 8, 48),
    new T.MeshBasicMaterial({
      color: 0x6be2cc,
      transparent: true,
      opacity: 0.7,
    }),
    [0, -0.2, 1.18],
  );
  text(
    g,
    "反光镜",
    0.42,
    0.11,
    [0, 0.025, 1.15],
    [0, 0, 0],
    "#16342e",
    "#8ef1d9",
  );
  g.userData.anchor.set(0, -0.2, 1.2);
  g = group("lamp", [3.12, 0, 0.25], [1.7, 0, 0]);
  cyl(g, 0.69, 0.14, [0, 0.1, 0], ivory);
  box(g, 0.67, 2.43, 0.62, [0, 1.5, 0], ivory);
  cyl(g, 0.045, 3.8, [0.18, 2.02, -0.42], chrome);
  box(g, 0.2, 0.18, 0.35, [0.18, 1.7, -0.28], ivory);
  for (let i = 0; i < 3; i++)
    for (let j = 0; j < 3; j++)
      cyl(g, 0.035, 0.012, [-0.2 + i * 0.2, 2.721, -0.19 + j * 0.19], black);
  // Long slots are modeled with capsule geometry, preserving the ivory front shell.
  mesh(
    g,
    new T.CapsuleGeometry(0.11, 0.95, 6, 16),
    black,
    [-0.34, 1.45, 0],
    [0, 0, 0],
  );
  mesh(g, new T.CapsuleGeometry(0.11, 0.95, 6, 16), black, [0, 1.45, 0.319]);
  const bulb = cyl(
    g,
    0.065,
    1.15,
    [-0.36, 1.45, 0],
    new T.MeshStandardMaterial({
      color: 0x8c7751,
      emissive: 0xffc34a,
      emissiveIntensity: 0,
    }),
  );
  for (const y of [0.45, 2.5]) screw(g, [0, y, 0.325]);
  g = group("power", [2.23, 0.34, -1.0], [1.3, 0, -1.5]);
  box(g, 0.86, 0.62, 0.78, [0, 0, 0], ivory);
  for (let i = 0; i < 10; i++)
    box(g, 0.025, 0.37, 0.009, [-0.35 + i * 0.075, 0, 0.395], black);
  box(g, 0.17, 0.12, 0.04, [0.1, 0.0, 0.42], blue);
  cable(
    g,
    [
      [0.2, 0, 0.4],
      [0.7, -0.15, 0.8],
      [1.2, -0.2, 0.7],
      [0.9, 0.5, 0.25],
    ],
    0x151b21,
  );
  g = group("monitor", [-0.45, 1.58, -1.78], [-1.4, 0.5, -1.4]);
  box(g, 3.18, 1.86, 0.13, [0, 0.5, 0], black);
  box(g, 0.13, 0.6, 0.15, [0, -0.7, 0], black);
  box(g, 0.86, 0.07, 0.55, [0, -1.02, 0.1], black);
  const monitorCanvas = document.createElement("canvas");
  monitorCanvas.width = 480;
  monitorCanvas.height = 270;
  const monitorTexture = new T.CanvasTexture(monitorCanvas);
  monitorTexture.colorSpace = T.SRGBColorSpace;
  mesh(
    g,
    new T.PlaneGeometry(3.03, 1.7),
    new T.MeshBasicMaterial({ map: monitorTexture }),
    [0, 0.5, 0.073],
  );
  text(
    g,
    "HDMI / USB   16:9",
    0.65,
    0.07,
    [0.85, -0.39, 0.075],
    [0, 0, 0],
    "#171b1d",
    "#8b9da8",
  );
  // A video link is a separate signal path, not part of the optical ray path.
  const videoCable = new T.Mesh(
    new T.BufferGeometry(),
    material(0x0879b1, 0.2, 0.5),
  );
  videoCable.name = "ccd-to-monitor-video-cable";
  scene.add(videoCable);
  const cameraPlug = box(scene, 0.1, 0.14, 0.1, [0, 0, 0], blue);
  const monitorPlug = box(scene, 0.14, 0.09, 0.12, [0, 0, 0], blue);
  cameraPlug.userData.part =
    monitorPlug.userData.part =
    videoCable.userData.part =
      "cameraVideo";
  const signalBeads = Array.from({ length: 6 }, () => {
    const bead = mesh(
      scene,
      new T.SphereGeometry(0.035, 8, 8),
      new T.MeshBasicMaterial({ color: 0x6aceff }),
    );
    bead.name = "video-signal-pulse";
    return bead;
  });
  let videoCurve = null,
    videoStamp = "";
  const workbench = createWorkbench();
  scene.add(workbench.group);
  const rays = new T.Group();
  scene.add(rays);
  let raySegments = [],
    rayStamp = "";
  function arrow(a, b, color) {
    const start = new T.Vector3(...a),
      end = new T.Vector3(...b),
      d = end.clone().sub(start);
    rays.add(
      new T.ArrowHelper(
        d.clone().normalize(),
        start,
        d.length(),
        color,
        0.09,
        0.045,
      ),
    );
    raySegments.push({ start, end, color });
  }
  const beads = [];
  for (let i = 0; i < 36; i++) {
    const bead = new T.Mesh(
      new T.SphereGeometry(0.025, 8, 8),
      new T.MeshBasicMaterial({ color: 0xffd15a }),
    );
    scene.add(bead);
    beads.push(bead);
  }
  function rebuildRays() {
    for (const child of [...rays.children]) {
      child.traverse((obj) => {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) obj.material.dispose();
      });
      rays.remove(child);
    }
    raySegments = [];
    const shift = (o.reading - 25) * 0.045,
      xx = 0.52,
      zz = 0.25 + shift,
      yy = 1.22 + (o.focus - 50) * 0.002;
    const lampZ = 0.25 + o.lampPosition * 0.012;
    const dev = T.MathUtils.degToRad((o.mirrorAngle - 45) * 2);
    arrow([2.75, yy, lampZ], [xx, yy, zz], 0xffd069);
    arrow([xx, yy, zz], [xx + Math.tan(dev) * 0.46, 0.79, zz], 0xffd069);
    arrow([xx - 0.035, 0.79, zz], [xx - 0.035, yy, zz], 0x6cf0d0);
    arrow([xx + 0.035, 0.765, zz], [xx + 0.035, yy, zz], 0xc1a1ff);
    arrow([xx, yy, zz], [xx, 2.98, zz], 0x6cf0d0);
    const end = opticalOrigin
      .clone()
      .addScaledVector(axis, studentMode && removed.has("camera") ? 0.8 : 2.06);
    end.z += shift;
    arrow([xx, 2.98, zz], end.toArray(), 0x6cf0d0);
    if (!o.baseMirrorFolded)
      arrow([xx, 0.46, zz], [xx + 0.19, 0.81, zz], 0xff8b68);
  }
  const film = new T.Group();
  film.visible = false;
  scene.add(film);
  box(film, 3.5, 0.12, 1.1, [0, 0.3, 0], glass);
  const shape = new T.Shape();
  shape.moveTo(-1.65, 0.93);
  shape.lineTo(1.65, 0.93);
  shape.lineTo(1.65, 0.78);
  for (let i = 32; i >= 0; i--) {
    const x = -1.65 + (3.3 * i) / 32;
    shape.lineTo(x, 0.37 + 0.15 * x * x);
  }
  shape.closePath();
  mesh(
    film,
    new T.ExtrudeGeometry(shape, {
      depth: 0.8,
      bevelEnabled: false,
      curveSegments: 32,
    }),
    glass,
    [0, 0, -0.4],
  );
  text(
    film,
    "PLANO-CONVEX LENS",
    2.1,
    0.18,
    [0, 1.15, 0.42],
    [0, 0, 0],
    "#102735",
    "#b4dbe0",
  );
  text(
    film,
    "AIR FILM  t(r) ~ r² / 2R",
    2.15,
    0.17,
    [0, 0.18, 0.65],
    [0, 0, 0],
    "#102735",
    "#ffd176",
  );
  function filmRay(a, b, color) {
    const s = new T.Vector3(...a),
      e = new T.Vector3(...b),
      d = e.clone().sub(s);
    film.add(
      new T.ArrowHelper(d.normalize(), s, s.distanceTo(e), color, 0.09, 0.04),
    );
  }
  filmRay([0.75, 1.55, 0.12], [0.75, 0.46, 0.12], 0xffd069);
  filmRay([0.79, 0.46, 0.12], [1.2, 1.55, 0.12], 0x6cf0d0);
  filmRay([0.83, 0.36, 0.14], [1.37, 1.55, 0.14], 0xc1a1ff);
  const important = new Set([
    "camera",
    "zoom",
    "prism",
    "tube",
    "objective",
    "mirror",
    "lens",
    "ring",
    "focus",
    "drum",
    "lamp",
  ]);
  for (const p of PARTS) {
    const g = groups[p.id];
    g.traverse((obj) => {
      if (obj.isMesh) {
        obj.userData.part ||= p.id;
        obj.material = obj.material.clone();
        obj.userData.originalOpacity = obj.material.opacity;
      }
    });
    const label = document.createElement("button");
    label.className = "part-label";
    label.textContent = p.name;
    label.onclick = () => onSelect(p.id);
    label.addEventListener(
      "wheel",
      (e) => {
        if (onAdjust(p.id, e.deltaY < 0 ? 1 : -1, e)) {
          e.preventDefault();
          e.stopPropagation();
        }
      },
      { passive: false },
    );
    host.querySelector("#labels").append(label);
    labels[p.id] = label;
  }
  const raycaster = new T.Raycaster();
  function hitPart(e) {
    const r = renderer.domElement.getBoundingClientRect();
    raycaster.setFromCamera(
      new T.Vector2(
        ((e.clientX - r.left) / r.width) * 2 - 1,
        1 - ((e.clientY - r.top) / r.height) * 2,
      ),
      camera,
    );
    return raycaster
      .intersectObjects(
        [...Object.values(groups), cameraPlug, monitorPlug, videoCable],
        true,
      )
      .find((h) => {
        if (!h.object.userData.part) return false;
        for (let obj = h.object; obj; obj = obj.parent)
          if (!obj.visible) return false;
        return true;
      })?.object.userData.part;
  }
  let hoveredPart = "",
    manipulation = null,
    dragFrame = 0;
  const surface = renderer.domElement;
  function flushDrag() {
    if (!manipulation?.moved) return;
    onInteract(manipulation.raw, {
      phase: "drag",
      dx: manipulation.x - manipulation.startX,
      dy: manipulation.y - manipulation.startY,
      shiftKey: manipulation.shiftKey,
    });
  }
  surface.addEventListener(
    "pointerdown",
    (e) => {
      if (!studentMode || filmMode || e.button !== 0 || manipulation) return;
      const raw = hitPart(e);
      if (!raw || !onInteract(raw, { phase: "start", shiftKey: e.shiftKey }))
        return;
      manipulation = {
        raw,
        pointerId: e.pointerId,
        startX: e.clientX,
        startY: e.clientY,
        x: e.clientX,
        y: e.clientY,
        moved: false,
        shiftKey: e.shiftKey,
      };
      targetView = null;
      controls.enabled = false;
      controls.enableDamping = false;
      controls.update();
      surface.setPointerCapture(e.pointerId);
      surface.style.cursor = "grabbing";
      e.preventDefault();
      e.stopImmediatePropagation();
    },
    { capture: true },
  );
  surface.addEventListener(
    "pointermove",
    (e) => {
      if (manipulation && e.pointerId === manipulation.pointerId) {
        Object.assign(manipulation, { x: e.clientX, y: e.clientY });
        manipulation.moved ||=
          Math.hypot(
            e.clientX - manipulation.startX,
            e.clientY - manipulation.startY,
          ) > 3;
        if (!dragFrame)
          dragFrame = requestAnimationFrame(() => {
            dragFrame = 0;
            flushDrag();
          });
        e.preventDefault();
        e.stopImmediatePropagation();
      } else if (studentMode && !filmMode) {
        const raw = hitPart(e) || "";
        hoveredPart = raw;
        surface.dataset.hoverPart = raw;
        surface.style.cursor =
          raw && onInteract(raw, { phase: "hover" })
            ? raw === "baseMirror"
              ? "pointer"
              : "grab"
            : "auto";
      }
    },
    { capture: true },
  );
  function endManipulation(e) {
    if (!manipulation || e.pointerId !== manipulation.pointerId) return;
    if (dragFrame) {
      cancelAnimationFrame(dragFrame);
      dragFrame = 0;
    }
    const cancelled = e.type !== "pointerup";
    if (!cancelled) {
      flushDrag();
      if (!manipulation.moved)
        onInteract(manipulation.raw, {
          phase: "click",
          shiftKey: manipulation.shiftKey,
        });
    }
    onInteract(manipulation.raw, { phase: cancelled ? "cancel" : "end" });
    manipulation = null;
    controls.enabled = true;
    controls.enableDamping = true;
    surface.style.cursor = "grab";
    down = null;
    if (surface.hasPointerCapture(e.pointerId))
      surface.releasePointerCapture(e.pointerId);
    e.preventDefault();
    e.stopImmediatePropagation();
  }
  for (const type of ["pointerup", "pointercancel", "lostpointercapture"])
    surface.addEventListener(type, endManipulation, { capture: true });
  let down;
  renderer.domElement.addEventListener(
    "pointerdown",
    (e) => (down = [e.clientX, e.clientY]),
  );
  renderer.domElement.addEventListener("pointerup", (e) => {
    if (
      filmMode ||
      !down ||
      Math.hypot(e.clientX - down[0], e.clientY - down[1]) > 6
    )
      return;
    const r = renderer.domElement.getBoundingClientRect();
    raycaster.setFromCamera(
      new T.Vector2(
        ((e.clientX - r.left) / r.width) * 2 - 1,
        1 - ((e.clientY - r.top) / r.height) * 2,
      ),
      camera,
    );
    const hit = raycaster
      .intersectObjects(Object.values(groups), true)
      .find((h) => h.object.userData.part);
    if (hit) onSelect(hit.object.userData.part, { source: "model" });
  });
  renderer.domElement.addEventListener(
    "wheel",
    (e) => {
      if (filmMode) return;
      const raw = hitPart(e);
      if (raw && onAdjust(raw, e.deltaY < 0 ? 1 : -1, e)) {
        e.preventDefault();
        e.stopImmediatePropagation();
      }
    },
    { capture: true, passive: false },
  );
  function resize() {
    const r = host.getBoundingClientRect();
    renderer.setSize(r.width, r.height);
    camera.aspect = r.width / r.height;
    camera.updateProjectionMatrix();
  }
  new ResizeObserver(resize).observe(host);
  resize();
  function view(name) {
    filmMode = name === "film";
    document.getElementById("filmExplanation").hidden = !filmMode;
    film.visible = filmMode;
    workbench.group.visible = !filmMode;
    for (const g of Object.values(groups)) g.visible = !filmMode;
    const views = {
      home: [
        [8, 5.5, 10],
        [0.7, 2.1, 0],
      ],
      front: [
        [0.3, 4.3, 11],
        [0.7, 2, 0],
      ],
      top: [
        [0.5, 11, 0.01],
        [0.5, 1.3, 0],
      ],
      optics: [
        [4, 3.3, 5],
        [0.8, 1.7, 0.25],
      ],
      camera: [
        [-3, 5.6, 6],
        [-0.2, 3.7, 0.2],
      ],
      exploded: [
        [11, 9, 15],
        [0.6, 3.3, 0],
      ],
      film: [
        [4, 2.5, 6],
        [0, 0.75, 0],
      ],
    };
    const v = views[name] || views.home;
    targetView = {
      position: new T.Vector3(...v[0]),
      target: new T.Vector3(...v[1]),
    };
  }
  const guideLines = new T.Group();
  scene.add(guideLines);
  for (const p of PARTS) {
    const line = new T.Line(
      new T.BufferGeometry().setFromPoints([new T.Vector3(), new T.Vector3()]),
      new T.LineDashedMaterial({
        color: 0x73a0ab,
        transparent: true,
        opacity: 0.35,
        dashSize: 0.06,
        gapSize: 0.04,
      }),
    );
    line.userData.part = p.id;
    guideLines.add(line);
  }
  const moving = [
    "tube",
    "prism",
    "prismLock",
    "eyeTube",
    "eyeLock",
    "eyepiece",
    "objective",
    "mirror",
    "mirrorKnob",
    "adapter",
    "adapterLock",
    "rods",
    "zoom",
    "camera",
    "heightScale",
    "focus",
  ];
  let last = performance.now();
  function animate(now) {
    requestAnimationFrame(animate);
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    explode = T.MathUtils.damp(explode, targetExplode, 7, dt);
    const r = host.getBoundingClientRect(),
      positions = [];
    for (const p of PARTS) {
      const g = groups[p.id],
        factor = removed.has(p.id) ? 1 : explode,
        at = g.userData.home.clone().addScaledVector(offsets[p.id], factor);
      if (moving.includes(p.id) && factor < 0.02) {
        at.z += (o.reading - 25) * 0.045;
        at.y += (o.focus - (o.focusTarget ?? 50)) * 0.002;
      }
      if (["lens", "plate", "ring", "screws"].includes(p.id) && factor < 0.02)
        at.x += o.center * 0.007;
      if (["lens", "plate", "ring", "screws"].includes(p.id) && factor < 0.02)
        at.z += ((o.ringOrigin ?? 25) - 25) * 0.045;
      if (p.id === "lamp") at.z += o.lampPosition * 0.012;
      g.position.lerp(at, 1 - Math.exp(-8 * dt));
      g.updateMatrixWorld();
      const a = g.userData.anchor
        .clone()
        .applyMatrix4(g.matrixWorld)
        .project(camera);
      const shown =
        labelOn &&
        !filmMode &&
        (important.has(p.id) || p.id === selected) &&
        a.z < 1 &&
        a.z > -1;
      labels[p.id].style.display = shown ? "block" : "none";
      if (shown)
        positions.push({
          el: labels[p.id],
          x: T.MathUtils.clamp((a.x * 0.5 + 0.5) * r.width, 65, r.width - 65),
          y: T.MathUtils.clamp(
            (0.5 - a.y * 0.5) * r.height,
            75,
            r.height - 140,
          ),
        });
    }
    positions.sort((a, b) => a.y - b.y);
    let previous = 60;
    for (const p of positions) {
      p.y = Math.max(previous + 22, p.y);
      previous = p.y;
    }
    const overflow = Math.max(0, previous - (r.height - 120));
    for (const p of positions) {
      p.el.style.left = p.x + "px";
      p.el.style.top = p.y - overflow + "px";
    }
    const videoAttached = o.videoConnected && !removed.has("camera");
    videoCable.visible =
      cameraPlug.visible =
      monitorPlug.visible =
        videoAttached && !filmMode;
    unpluggedVideoTail.visible = !videoAttached;
    if (videoAttached) {
      const start = groups.camera.localToWorld(new T.Vector3(0.12, 0.58, 0));
      const end = groups.monitor.localToWorld(
        new T.Vector3(-1.43, -0.1, -0.12),
      );
      const stamp = [...start.toArray(), ...end.toArray()]
        .map((v) => v.toFixed(2))
        .join("/");
      if (stamp !== videoStamp) {
        videoStamp = stamp;
        videoCurve = new T.CatmullRomCurve3([
          start,
          start.clone().add(new T.Vector3(-0.4, 0.45, 0.1)),
          new T.Vector3(-2.45, 4.9, -0.4),
          new T.Vector3(-2.7, 2.1, -1.6),
          end.clone().add(new T.Vector3(-0.25, 0, -0.25)),
          end,
        ]);
        videoCable.geometry.dispose();
        videoCable.geometry = new T.TubeGeometry(
          videoCurve,
          64,
          0.028,
          8,
          false,
        );
      }
      cameraPlug.position.copy(start);
      monitorPlug.position.copy(end);
    }
    signalBeads.forEach((bead, i) => {
      bead.visible = videoCable.visible && o.cameraOn && o.monitorOn;
      if (bead.visible && videoCurve)
        bead.position.copy(videoCurve.getPoint((now * 0.00013 + i / 6) % 1));
    });
    pivot.rotation.z = -T.MathUtils.degToRad(90 - o.mirrorAngle);
    basePivot.rotation.x = o.baseMirrorFolded ? Math.PI : 0;
    baseMirrorDial.rotation.z = o.baseMirrorFolded ? Math.PI : 0;
    baseMirrorHalo.visible = studentMode;
    baseMirrorHalo.material.opacity =
      hoveredPart === "baseMirror" || selected === "baseMirror" ? 0.9 : 0.55;
    groups.focus.rotation.z = (o.focus - 50) * 0.04;
    groups.drum.quaternion.copy(drumMount);
    groups.drum.rotateX((o.reading - 25) * Math.PI * 2);
    groups.mirrorKnob.rotation.z = (o.mirrorAngle - 45) * 0.1;
    eyepieceDial.rotation.y = (o.reticle - 50) * 0.08;
    cameraFocusDial.rotation.y = (o.cameraFocus - 50) * 0.08;
    apertureDial.rotation.y = (o.aperture - 70) * 0.06;
    exposureDial.rotation.x = (o.exposure - 100) * 0.02;
    const illuminated =
      o.lightOn &&
      !removed.has("lamp") &&
      !removed.has("mirror") &&
      !removed.has("lens") &&
      !removed.has("plate");
    const metrics = opticalMetrics(o, illuminated);
    bulb.material.emissiveIntensity = o.lightOn && !removed.has("lamp") ? 2 : 0;
    fringe.material.uniforms.brightness.value =
      illuminated && explode < 0.03 ? Math.min(1, metrics.throughput) : 0;
    fringe.material.uniforms.contrast.value = metrics.contrast;
    const stamp = [
      o.reading,
      o.focus,
      o.mirrorAngle,
      o.lampPosition,
      o.baseMirrorFolded,
      removed.has("camera"),
    ].join("/");
    if (stamp !== rayStamp) {
      rayStamp = stamp;
      rebuildRays();
    }
    rays.visible =
      !filmMode &&
      o.lightOn &&
      !removed.has("lamp") &&
      o.showRays &&
      (studentMode || removed.size === 0) &&
      explode < 0.03;
    if (studentMode) {
      const available = [
        true,
        !removed.has("mirror"),
        illuminated,
        illuminated,
        illuminated && !removed.has("objective"),
        illuminated && !removed.has("objective") && !removed.has("eyepiece"),
        !o.baseMirrorFolded,
      ];
      rays.children.forEach((ray, i) => (ray.visible = !!available[i]));
    }
    rays.traverse((obj) => {
      if (obj.material) {
        obj.material.depthTest = !o.xray;
        obj.renderOrder = 10;
      }
    });
    for (let i = 0; i < beads.length; i++) {
      const s = raySegments[i % raySegments.length];
      beads[i].visible =
        rays.visible && rays.children[i % raySegments.length]?.visible;
      beads[i].material.depthTest = !o.xray;
      beads[i].renderOrder = 11;
      if (s) {
        beads[i].position.lerpVectors(
          s.start,
          s.end,
          (now * 0.00032 + i * 0.13) % 1,
        );
        beads[i].material.color.setHex(s.color);
      }
    }
    guideLines.visible =
      !filmMode && (explode > 0.05 || (!studentMode && removed.size > 0));
    if (guideLines.visible)
      for (const line of guideLines.children) {
        const g = groups[line.userData.part],
          attr = line.geometry.attributes.position;
        attr.setXYZ(0, ...g.userData.home.toArray());
        attr.setXYZ(1, ...g.position.toArray());
        attr.needsUpdate = true;
        line.computeLineDistances();
      }
    if (targetView) {
      camera.position.lerp(targetView.position, 0.09);
      controls.target.lerp(targetView.target, 0.09);
      if (camera.position.distanceTo(targetView.position) < 0.02)
        targetView = null;
    }
    controls.update();
    workbench.update(now);
    renderer.render(scene, camera);
  }
  requestAnimationFrame(animate);
  const thumbs = {},
    tr = new T.WebGLRenderer({ alpha: true, antialias: true });
  tr.setSize(150, 100);
  tr.toneMapping = T.ACESFilmicToneMapping;
  for (const p of PARTS) {
    const s = new T.Scene();
    s.add(new T.HemisphereLight(0xffffff, 0x4c5d6c, 3));
    const l = new T.DirectionalLight(0xffffff, 3);
    l.position.set(3, 5, 5);
    s.add(l);
    const clone = groups[p.id].clone();
    clone.position.set(0, 0, 0);
    s.add(clone);
    const b = new T.Box3().setFromObject(clone),
      c = b.getCenter(new T.Vector3()),
      size = b.getSize(new T.Vector3()),
      distance = Math.max(size.x, size.y, size.z) * 1.7,
      cam = new T.PerspectiveCamera(38, 1.5, 0.01, 100);
    cam.position.copy(c).add(new T.Vector3(distance, distance * 0.6, distance));
    cam.lookAt(c);
    tr.render(s, cam);
    thumbs[p.id] = tr.domElement.toDataURL();
  }
  tr.dispose();
  return {
    thumbs,
    removed,
    configureStudentUnits(units, installed = [], snap = false) {
      studentMode = true;
      important.clear();
      removed.clear();
      Object.values(labels).forEach(
        (label) => (label.style.pointerEvents = "none"),
      );
      for (const unit of units) {
        important.add(unit.model);
        labels[unit.model].textContent = unit.name;
        if (unit.id === "baseMirror")
          labels[unit.model].textContent = "底座反光镜 · 前侧旋钮";
        for (const id of unit.parts) {
          if (unit.install) {
            offsets[id].set(...unit.offset);
            if (!installed.includes(unit.id)) removed.add(id);
            if (snap)
              groups[id].position
                .copy(groups[id].userData.home)
                .addScaledVector(offsets[id], removed.has(id) ? 1 : 0);
          }
        }
      }
    },
    view,
    explode(v) {
      targetExplode = v;
    },
    labels(v) {
      labelOn = v;
    },
    rotate(v) {
      controls.autoRotate = v;
      controls.autoRotateSpeed = 0.5;
    },
    select(id) {
      selected = id;
      for (const p of PARTS) {
        labels[p.id].classList.toggle("selected", id === p.id);
        groups[p.id].traverse((m) => {
          if (m.isMesh && m.material.emissive && p.id !== "lamp") {
            m.material.emissive.setHex(id === p.id ? 0x183a45 : 0);
            m.material.emissiveIntensity = id === p.id ? 0.28 : 0;
          }
        });
      }
    },
    optics(values) {
      o = { ...o, ...values };
      xray = o.xray;
      for (const id of [
        "tube",
        "prism",
        "eyeTube",
        "zoom",
        "adapter",
        "camera",
        "objective",
      ])
        groups[id].traverse((m) => {
          if (m.isMesh && m.material.isMeshStandardMaterial) {
            m.material.transparent = xray;
            m.material.opacity = xray ? 0.18 : m.userData.originalOpacity;
            m.material.depthWrite = !xray;
          }
        });
    },
    updateMonitor(source) {
      monitorCanvas.getContext("2d").drawImage(source, 0, 0, 480, 270);
      monitorTexture.needsUpdate = true;
    },
    screenshot() {
      renderer.render(scene, camera);
      return renderer.domElement.toDataURL("image/png");
    },
    get canvas() {
      return renderer.domElement;
    },
    get scene() {
      return scene;
    },
  };
}
