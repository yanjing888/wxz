---
kb_type: teaching
experiment_code: tensile_steel
experiment_name: 低碳钢拉伸实验
category: mechanics
doc: tensile_steel/teaching-knowledge.md
sectionKey: tensile_steel
document_type: lecture_and_report_guide
version: v1.0
---

# 低碳钢拉伸实验教学知识库

本文档用于 Dify 教学知识库：原理、步骤、数据处理与报告指导。纠错规则见 `docs/visual-rules/tensile_steel.md`。

## 一、实验目的

1. 测定低碳钢拉伸时的力学性能指标。
2. 观察弹性、屈服、强化与颈缩断裂过程。
3. 掌握 σ-ε 曲线记录与 σs、σb、δ、ψ 的计算。
4. 训练试样测量、装夹对中与数据处理能力。

## 二、实验原理

试样在轴向拉力 F 作用下，横截面上产生正应力 σ = F / A0，应变 ε = ΔL / L0（工程应变，引伸计测得为局部应变）。

低碳钢典型 σ-ε 曲线含：弹性段、屈服平台（上/下屈服）、强化段、颈缩与断裂。

常用指标：

- 屈服强度 σs（规定非比例延伸或屈服平台应力）
- 抗拉强度 σb = Fb / A0
- 延伸率 δ = (Lu - L0) / L0 × 100%
- 断面收缩率 ψ = (A0 - A1) / A0 × 100%

## 三、仪器与材料

- 万能材料试验机
- 低碳钢圆棒或平板试样
- 游标卡尺、螺旋测微器
- 引伸计（标距与试样 L0 匹配）
- 数据记录表或采集软件

## 四、实验步骤

### Step 1：试样测量与编号

> **section_key:** `tensile_steel.step.1.teaching`

在标距段三处互垂直测量直径取平均 d，测量原始标距 L0，记录编号、材料与环境。

### Step 2：装夹与引伸计安装

> **section_key:** `tensile_steel.step.2.teaching`

对称旋紧夹头，引伸计刀口对准标距刻线，检查轴线对中，关闭防护罩。

### Step 3：预加载与对中检查

> **section_key:** `tensile_steel.step.3.teaching`

小载荷消除间隙后卸载至零，确认力/应变通道归零，观察有无弯曲或异响。

### Step 4：分级加载与曲线记录

> **section_key:** `tensile_steel.step.4.teaching`

恒定速率加载，记录 σ-ε 曲线；屈服段加密测点；接近引伸计量程前取下引伸计。

### Step 5：断后测量与指标计算

> **section_key:** `tensile_steel.step.5.teaching`

颈缩处测最小直径 d1，断口对齐后测 Lu，计算 σs、σb、δ、ψ 及不确定度。

## 五、数据处理

- 原始截面积 A0 = π d² / 4（圆棒）
- 应力 σ = F / A0，注意单位 MPa
- 延伸率、断面收缩率按讲义公式
- 结果有效数字与测量精度一致

## 六、常见误差来源

1. 装夹偏载导致弯曲
2. 引伸计未对准标距
3. 预载未回零
4. 屈服点判读不一致
5. 断后测量位置错误

## 七、报告建议结构

目的、原理、仪器、步骤、原始数据、σ-ε 曲线、指标计算、误差分析、结论。

## 八、检索建议

```text
tensile_steel 低碳钢拉伸 {{step_title}} 原理 装夹 屈服 数据处理 {{query}}
```
