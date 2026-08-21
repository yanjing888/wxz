---
kb_type: correction_rule
experiment_code: air_wedge_thickness
experiment_name: 空气劈尖干涉测量薄片厚度
category: optics
doc: air_wedge_thickness/visual-rules.md
sectionKey: air_wedge_thickness
version: v1.0
coordinate_system: 0-1000 normalized bounding box
---

# 空气劈尖干涉测量薄片厚度视觉纠错规则

视觉纠错只判断图片中可见的操作、仪器、读数与安全问题；不可见或无法确认的问题不得臆测。

## 全实验通用规则

- 判断画面是否包含 JCD-3 读数显微镜、空气劈尖装置、钠光灯、45° 半反镜或直干涉条纹。
- 底座反光镜应向内收起，避免底部杂散光。
- 光学玻璃表面不得用手触碰。

## Step 1：器件安放与光路调节

> **section_key:** `air_wedge_thickness.step.1.rules`

### 常见错误规则

#### AW_STEP1_WEDGE_UNSTABLE：劈尖未压稳

- severity: 中
- error_type: 劈尖安放
- 可见证据: 空气劈尖装置明显倾斜、压片未压紧或器件可随手移动。
- 纠正建议: 将劈尖放在工作台并用压片轻压稳，保持钠光经 45° 半反镜入射。
- 不应误判: 看不到劈尖整体时，不直接判未压稳。

#### AW_STEP1_STRAY_LIGHT：底座反光镜未收起

- severity: 中
- error_type: 光路调节
- 可见证据: 底座反光镜向外翻开，视场杂散光明显、条纹对比度低。
- 纠正建议: 将底座反光镜向内收起，微调半反镜使视场亮度均匀。

## Step 2：调焦与条纹校正

> **section_key:** `air_wedge_thickness.step.2.rules`

### 常见错误规则

#### AW_STEP2_FRINGE_TILT：条纹与叉丝不平行

- severity: 中
- error_type: 条纹校正
- 可见证据: 直条纹相对水平叉丝明显倾斜，学生却旋转显微镜本体。
- 纠正建议: 先调清叉丝，再调劈尖夹座四颗螺钉使条纹与水平叉丝平行；不要旋转显微镜主体。
- 不应误判: 条纹略虚属于正常，不等于调焦错误。

#### AW_STEP2_FOCUS_ORDER：调焦顺序错误

- severity: 中
- error_type: 调焦
- 可见证据: 样品像清楚但十字叉丝明显发虚。
- 纠正建议: 先调节目镜使叉丝清晰，再由低到高抬升镜筒找直条纹。

## Step 3：测量数据采集

> **section_key:** `air_wedge_thickness.step.3.rules`

### 常见错误规则

#### AW_STEP3_FRINGE_MIX：暗纹明纹混数

- severity: 高
- error_type: 条纹计数
- 可见证据: 记录表或口述中暗纹、明纹混用；起终点落在条纹中间仍计入 n′。
- 纠正建议: 统一数暗纹或统一数明纹，起终点应落在条纹中间位置。

#### AW_STEP3_BACKLASH：测微鼓轮中途反向

- severity: 高
- error_type: 读数
- 可见证据: 读数过程中鼓轮转向改变，或左右读数方向不一致。
- 纠正建议: 全程保持测微鼓轮同一转向，并复核 L′ = |x₂ - x₁|。

## Step 4：薄片厚度计算

> **section_key:** `air_wedge_thickness.step.4.rules`

### 常见错误规则

#### AW_STEP4_FORMULA_SWAP：L 与 L′ 位置放反

- severity: 高
- error_type: 厚度计算
- 可见证据: 记录中 L 用了区间长度、L′ 用了棱边到薄片距离，或公式代入颠倒。
- 纠正建议: L 是棱边到薄片的总距离，L′ 是选定条纹区间长度，按 d = (L/L′)(n′λ/2) 计算。

## Step 5：仪器复原

> **section_key:** `air_wedge_thickness.step.5.rules`

### 常见错误规则

#### AW_STEP5_POWER_ON：光源或示教设备未关闭

- severity: 低
- error_type: 仪器复原
- 可见证据: 钠光灯、CCD 或监视器仍开启，实验结束台面未整理。
- 纠正建议: 关闭光源、CCD 与监视器，取下劈尖装置并整理附件线缆。
