---
kb_type: correction_rule
experiment_code: microscope_length_measurement
experiment_name: 读数显微镜微小长度直接测量练习
category: optics
doc: microscope_length_measurement/visual-rules.md
sectionKey: microscope_length_measurement
version: v1.0
coordinate_system: 0-1000 normalized bounding box
---

# 读数显微镜微小长度直接测量练习视觉纠错规则

视觉纠错只判断图片中可见的操作、仪器、读数与安全问题；不可见或无法确认的问题不得臆测。

## 全实验通用规则

- 判断画面是否包含 JCD-3 读数显微镜、待测刻尺/样品、十字叉丝或读数区域。
- 检查样品是否被压片压稳，读数视场是否清晰。
- 检查被测长度方向是否与显微镜移动方向平行。

## Step 1：样品安放与照明

> **section_key:** `microscope_length_measurement.step.1.rules`

### 常见错误规则

#### MLM_STEP1_SAMPLE_LOOSE：样品未压牢

- severity: 中
- error_type: 样品安放
- 可见证据: 刻尺或样品明显可移动，压片未压紧或样品边缘翘起。
- 纠正建议: 将样品放在工作台并用压片压紧，根据透光情况调整底部照明。

#### MLM_STEP1_LIGHTING：照明与样品不匹配

- severity: 低
- error_type: 照明
- 可见证据: 视场过暗或过曝，无法看清被测边沿。
- 纠正建议: 普通长度观察时可打开底座反光镜作底部照明，并调整观察角度。

## Step 2：调焦与方向校正

> **section_key:** `microscope_length_measurement.step.2.rules`

### 常见错误规则

#### MLM_STEP2_NOT_PARALLEL：被测方向未与导轨平行

- severity: 高
- error_type: 方向校正
- 可见证据: 被测边与叉丝或移动方向明显成角，测得的是斜向长度。
- 纠正建议: 旋转工作台或样品，使被测长度方向与显微镜移动导轨严格平行。

#### MLM_STEP2_RETICLE_BLUR：叉丝未先调清

- severity: 中
- error_type: 调焦
- 可见证据: 十字叉丝发虚但样品边缘相对更清楚。
- 纠正建议: 先调节目镜使叉丝清晰，再调焦使样品像清晰。

## Step 3：起终点读数

> **section_key:** `microscope_length_measurement.step.3.rules`

### 常见错误规则

#### MLM_STEP3_READING_COMPOUND：主尺鼓轮合读错误

- severity: 高
- error_type: 显微镜读数
- 可见证据: 记录中主尺整数与鼓轮小数组合明显不合理，或 L ≠ A′ - A。
- 纠正建议: 主尺读整数毫米，鼓轮读小数；用竖叉丝对准起终点并保持鼓轮单向转动。

#### MLM_STEP3_BACKLASH：鼓轮中途反向

- severity: 高
- error_type: 读数
- 可见证据: 读数过程中鼓轮转向改变。
- 纠正建议: 保持鼓轮转向不变，复核 L = A′ - A。

## Step 4：重复测量与平均

> **section_key:** `microscope_length_measurement.step.4.rules`

### 常见错误规则

#### MLM_STEP4_TOO_FEW_TRIALS：重复次数不足

- severity: 中
- error_type: 重复测量
- 可见证据: 记录表只有 1～2 次读数，未求平均或未说明离散程度。
- 纠正建议: 同一位置重复测量 6 次并求平均值，检查样品是否压牢、方向是否平行。
