---
experiment_code: tensile_steel
experiment_name: 低碳钢拉伸实验
category: mechanics
kb_type: correction_rule
doc: tensile_steel/visual-rules.md
sectionKey: tensile_steel
rule_type: mixed_correction
version: v1.0
coordinate_system: 0-1000 normalized bounding box
---

# 低碳钢拉伸实验纠错规则

本文档用于 Dify 纠错规则库召回。视觉纠错只判断图片中可见的装夹、对中、仪表、曲线与安全问题；数据纠错核对 `data_json`、记录表与计算结果。不可见或无法确认的问题不得臆测。

## 通用输出要求

视觉模型应输出结构化 JSON，至少包含：

```json
{
  "experiment": "低碳钢拉伸实验",
  "experiment_code": "tensile_steel",
  "step_id": "2",
  "step_title": "装夹与引伸计安装",
  "is_step_related": true,
  "summary": "一句话概括",
  "issues": [],
  "regions": [],
  "next_action": "建议下一步"
}
```

`issues` 每项建议含：`issue_id`、`error_type`、`severity`、`description`、`evidence`、`fix_instruction`、`confidence`、`region_ref`。

## 全实验通用规则

### TS_GENERAL_NOT_RELATED：图片与拉伸实验无关

- severity: 低
- error_type: 图片内容不匹配
- 适用步骤: Step 2, 3
- 可见证据:
  - 未看到万能试验机、试样、夹头、引伸计或曲线屏幕
  - 图片为无关场景或完全无法辨认实验装置
- 纠正建议:
  - 请上传装夹状态、对中检查或曲线屏幕的清晰照片
- 不应误判:
  - 仅背景杂乱但试样与夹头清晰可见

### TS_GENERAL_NO_GUARD：未装防护罩或危险加载

- severity: 高
- error_type: 安全风险
- 适用步骤: Step 2, 3, 4
- 可见证据:
  - 试验机高速加载时防护罩打开或缺失
  - 人员手伸入夹头/移动横梁危险区域
  - 接近断裂时无人保持安全距离
- 纠正建议:
  - 关闭防护罩、保持安全距离；接近屈服/断裂段降低加载速率并监护
- 标注建议:
  - 防护罩、夹头危险区域、未保持距离的站位

### TS_GENERAL_INSTRUMENT_ZERO：力/位移通道未归零

- severity: 中
- error_type: 仪表零点
- 适用步骤: Step 3, 4
- 可见证据:
  - 屏幕显示载荷/应变非零但试样明显未受力
  - 预载前后曲线起点不在零附近
- 纠正建议:
  - 卸载至零后确认各通道归零，再开始正式加载
- 不应误判:
  - 预载后故意保留小载荷时，需结合步骤说明判断

## Step 1：试样测量与编号

> **section_key:** `tensile_steel.step.1.rules`

### 适用条件

- experiment_code: tensile_steel
- step_id: 1
- step_title: 试样测量与编号
- correction_mode: data

### 步骤目标

测量标距段直径 d、原始标距 L0，记录编号、材料与环境条件；数据应满足有效数字与单位规范。

### 常见错误规则

#### TS_STEP1_DIAMETER_SINGLE：直径只测一处

- severity: 高
- error_type: 尺寸测量
- 可见/数据证据:
  - 记录表只有一组直径 d，无三处互垂直测量
  - data_json 中 diameter 数组长度 < 3
- 纠正建议:
  - 在标距段三处互垂直测量直径并取平均
- 不应误判:
  - 讲义允许两处测量时，以讲义为准

#### TS_STEP1_DIAMETER_UNIT：直径单位错误或缺单位

- severity: 高
- error_type: 单位
- 可见/数据证据:
  - d 写成 m 量级（应为 mm）
  - 数值无单位或混用 mm/cm
- 纠正建议:
  - 统一用 mm，表头注明单位

#### TS_STEP1_L0_WRONG：标距 L0 与试样不符

- severity: 中
- error_type: 标距
- 可见/数据证据:
  - L0 与试样刻线明显不一致
  - L0 未记录或为零
- 纠正建议:
  - 核对试样标距刻线，重新测量 L0 并记录

#### TS_STEP1_SURFACE_DEFECT：未记录表面缺陷

- severity: 低
- error_type: 记录规范
- 可见/数据证据:
  - 试样明显锈蚀、划痕、颈缩预损伤但未备注
- 纠正建议:
  - 在记录表备注缺陷情况，必要时更换试样

#### TS_STEP1_SIG_FIG：有效数字与仪器不符

- severity: 中
- error_type: 有效数字
- 可见/数据证据:
  - 游标卡尺读数精度超过 0.02 mm
  - 螺旋测微器读数超过 0.01 mm
- 纠正建议:
  - 读数不超过仪器分度值

### Step 1 反馈话术参考

请以三处直径平均值和 L0 为基础检查记录完整性；确认单位 mm、有效数字与仪器分度一致，并填写编号与环境条件。

## Step 2：装夹与引伸计安装

> **section_key:** `tensile_steel.step.2.rules`

### 适用条件

- experiment_code: tensile_steel
- step_id: 2
- step_title: 装夹与引伸计安装
- correction_mode: vision

### 步骤目标

试样对称装入上下夹头，引伸计刀口对准标距刻线，轴线与加载方向一致，夹持可靠。

### 视觉检查项

1. 试样轴线是否与加载方向一致
2. 上下夹头是否对称旋紧、有无偏载弯曲
3. 引伸计是否对准标距、安装是否牢固
4. 防护罩是否就位
5. 夹头区域是否有明显滑移风险

### 常见错误规则

#### TS_STEP2_CLAMP_MISALIGN：试样轴线偏斜

- severity: 高
- error_type: 装夹对中
- 可见证据:
  - 试样明显弯曲或轴线与横梁不平行
  - 上下夹口不对中，试样呈 S 形
- 纠正建议:
  - 重新装夹，使试样轴线与加载方向一致；对称旋紧夹头
- 标注建议:
  - 试样弯曲段、上下夹头不对中区域

#### TS_STEP2_CLAMP_ASYMMETRIC：夹头不对称旋紧

- severity: 中
- error_type: 装夹
- 可见证据:
  - 一侧夹头明显松于另一侧
  - 试样在夹口处偏转
- 纠正建议:
  - 交替对称旋紧，避免偏载

#### TS_STEP2_EXTENSOMETER_OFF_GAUGE：引伸计未对准标距

- severity: 高
- error_type: 引伸计安装
- 可见证据:
  - 引伸计刀口未对准标距刻线
  - 引伸计标距与试样 L0 不一致
  - 引伸计倾斜或仅单侧接触
- 纠正建议:
  - 刀口对准标距刻线，轻夹持并确认无滑动
- 标注建议:
  - 引伸计刀口与标距刻线位置

#### TS_STEP2_EXTENSOMETER_OVER_RANGE：引伸计超量程风险

- severity: 中
- error_type: 引伸计
- 可见证据:
  - 引伸计已拉至极限位置仍继续加载
  - 大变形段未取下引伸计
- 纠正建议:
  - 接近引伸计量程前取下，改用横梁位移计继续

#### TS_STEP2_NO_GUARD：防护罩未关闭

- severity: 高
- error_type: 安全
- 可见证据:
  - 试验机防护罩打开或未安装
- 纠正建议:
  - 装夹完成后关闭防护罩再加载

### Step 2 反馈话术参考

请先确认试样轴线与加载方向一致、夹头对称旋紧，引伸计刀口对准标距；防护罩关闭后再进行预加载。

## Step 3：预加载与对中检查

> **section_key:** `tensile_steel.step.3.rules`

### 适用条件

- experiment_code: tensile_steel
- step_id: 3
- step_title: 预加载与对中检查
- correction_mode: vision

### 步骤目标

小载荷消除间隙，检查对中与仪表零点，确认曲线记录通道正常。

### 常见错误规则

#### TS_STEP3_SKIP_PRELOAD：跳过预加载

- severity: 中
- error_type: 预加载
- 可见证据:
  - 直接大载荷加载，曲线起点有跳跃
  - 用户描述未做预载卸载
- 纠正建议:
  - 低速小载荷加载后卸载至零，再正式加载

#### TS_STEP3_BENDING_UNDER_LOAD：加载后试样弯曲

- severity: 高
- error_type: 对中
- 可见证据:
  - 预载后试样明显弯曲或偏心
  - 曲线线性段异常或非对称
- 纠正建议:
  - 停机卸载，重新装夹对中

#### TS_STEP3_ZERO_NOT_CHECKED：未确认通道归零

- severity: 中
- error_type: 仪表
- 可见证据:
  - 卸载后力/应变显示明显非零
  - 曲线未回零就开始正式加载
- 纠正建议:
  - 卸载至零，确认各通道归零后再记录

#### TS_STEP3_ABNORMAL_NOISE：异响或滑移

- severity: 高
- error_type: 装夹
- 可见证据:
  - 夹口处有滑移痕迹
  - 加载时伴随异常声响（若视频/描述提及）
- 纠正建议:
  - 停止加载，检查夹持与对中，必要时重新装夹

### Step 3 反馈话术参考

预载不宜超过比例极限；确认卸载回零、曲线通道正常且无偏载后再进入分级加载。

## Step 4：分级加载与曲线记录

> **section_key:** `tensile_steel.step.4.rules`

### 适用条件

- experiment_code: tensile_steel
- step_id: 4
- step_title: 分级加载与曲线记录
- correction_mode: data

### 步骤目标

按分级载荷记录 σ-ε 曲线，识别屈服段、抗拉强度附近特征，加载速率稳定。

### 常见错误规则

#### TS_STEP4_LOAD_RATE_UNSTABLE：加载速率不稳定

- severity: 中
- error_type: 加载控制
- 可见/数据证据:
  - 曲线阶梯间距不均匀且无说明
  - 屈服段测点过疏
- 纠正建议:
  - 保持恒定加载速率；屈服段适当加密测点

#### TS_STEP4_YIELD_MISSED：屈服特征未记录

- severity: 中
- error_type: 曲线分析
- 可见/数据证据:
  - 低碳钢明显屈服平台但 data_json 无屈服点标记
  - 报告未区分上/下屈服（若讲义要求）
- 纠正建议:
  - 在曲线上标出屈服点/平台，记录对应应力应变

#### TS_STEP4_STRESS_UNIT：应力单位错误

- severity: 高
- error_type: 单位
- 可见/数据证据:
  - σ 用 Pa 表示但数值为 MPa 量级
  - 力 N 未除以原始截面积
- 纠正建议:
  - σ = F / A0，统一 MPa 或讲义规定单位

#### TS_STEP4_STRAIN_DEFINITION：应变定义混用

- severity: 中
- error_type: 应变
- 可见/数据证据:
  - 工程应变与真应变混用无说明
  - 引伸计应变与横梁位移应变混用
- 纠正建议:
  - 按讲义统一应变定义并在报告中注明

#### TS_STEP4_EXTENSOMETER_NOT_REMOVED：大变形未取引伸计

- severity: 中
- error_type: 引伸计
- 可见证据:
  - 颈缩后引伸计仍安装
  - 曲线在颈缩段异常尖峰
- 纠正建议:
  - 接近引伸计量程前取下，改用位移计

### Step 4 反馈话术参考

检查 σ-ε 曲线是否覆盖弹性段、屈服段与强化段；确认应力单位、加载速率与屈服特征记录完整。

## Step 5：断后测量与指标计算

> **section_key:** `tensile_steel.step.5.rules`

### 适用条件

- experiment_code: tensile_steel
- step_id: 5
- step_title: 断后测量与指标计算
- correction_mode: data

### 步骤目标

测量断后最小直径与标距 Lu，计算 σs、σb、δ、ψ 及不确定度。

### 常见错误规则

#### TS_STEP5_NECK_WRONG_PLACE：未在颈缩处测最小直径

- severity: 高
- error_type: 断后测量
- 可见/数据证据:
  - d1 不在颈缩最小处
  - 用原始直径代替 d1
- 纠正建议:
  - 在颈缩最小截面测量 d1

#### TS_STEP5_LU_MISALIGN：断后标距 Lu 测量不规范

- severity: 高
- error_type: 标距
- 可见/数据证据:
  - 断口未对齐就测 Lu
  - Lu 测量方法与讲义规定不一致
- 纠正建议:
  - 断口对齐后按讲义方法测 Lu

#### TS_STEP5_ELONGATION_FORMULA：延伸率公式错误

- severity: 高
- error_type: 计算
- 可见/数据证据:
  - δ 公式用错（应为 (Lu-L0)/L0×100%）
  - ψ 用错截面积（应用 A0 与 A1）
- 纠正建议:
  - 核对讲义公式与代入数据

#### TS_STEP5_STRENGTH_CONFUSION：σs 与 σb 混淆

- severity: 中
- error_type: 指标
- 可见/数据证据:
  - 抗拉强度取成屈服强度
  - 断裂后应力下降段误作 σb
- 纠正建议:
  - σs 取屈服平台/规定非比例延伸；σb 取最大载荷对应应力

#### TS_STEP5_UNCERTAINTY_MISSING：未做不确定度分析

- severity: 低
- error_type: 报告规范
- 适用场景: 讲义要求时
- 可见证据:
  - 结果无不确定度或误差分析
- 纠正建议:
  - 按讲义完成不确定度估算

### Step 5 反馈话术参考

断后先在颈缩处测 d1，断口对齐后测 Lu；核对 σs、σb、δ、ψ 公式与单位，补全不确定度分析。

## Dify 检索提示

- experiment_code: tensile_steel
- 检索标签: 拉伸、低碳钢、装夹、引伸计、屈服、σ-ε、断后测量
- 视觉步骤优先召回 Step 2/3 规则；数据步骤优先 Step 1/4/5 规则
