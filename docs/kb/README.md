# 物小智知识库建设指南

物小智使用 **双库** 架构，与 `backend/src/main/resources/knowledge-map.yml` 和工作流中的 4 个知识检索节点对应。

| 库 | 目录 | Dify 环境变量 |
|---|---|---|
| 教学库 | `docs/teaching-knowledge/` | `DIFY_KB_TEACHING_DATASET_ID` |
| 纠错规则库 | `docs/visual-rules/` | `DIFY_KB_CORRECTION_RULES_DATASET_ID` |

## 第一步：编写 Markdown

- **纠错库**：只写「什么算错、可见证据、怎么改」，每条规则带稳定 `rule_id`
- **教学库**：原理、步骤、公式、报告写法（不写判错规则）

源文件命名与 `knowledge-map.yml` 中 `doc` 字段一致，例如 `newton_rings/visual-rules.md`。

每份文档 frontmatter 至少包含：

```yaml
---
experiment_code: newton_rings
kb_type: correction_rule   # 或 teaching
doc: newton_rings/visual-rules.md
sectionKey: newton_rings
---
```

每个实验步骤章节建议标注 `section_key`（与 knowledge-map 中 `rulesSection` 对齐）：

```markdown
## Step 1：仪器检查与光路调节

> **section_key:** `newton_rings.step.1.rules`
```

## 第二步：同步到后端

```bash
python scripts/sync_kb.py
```

会完成：

1. `docs/` → `backend/src/main/resources/experiments/*/`
2. 生成 `docs/kb/export/{teaching|correction_rules}/` 供 Dify 导入

## 第三步：在 Dify 创建知识库

1. 新建数据集 **物理实验教学知识点通用库**（teaching）
2. 新建数据集 **物理实验纠错规则通用库**（correction_rules）
3. 从 `docs/kb/export/` 分别导入各实验的 `.md` 文件
4. 为每个文档设置元数据（与同名 `.metadata.json` 一致）：
   - `experiment_code`
   - `kb_type`
   - `doc`
   - `sectionKey`

## 第四步：导入并绑定工作流

```bash
python scripts/build_step_aware_dify_workflow.py
```

导入 `docs/dify/wuxiaozhi-correction-guidance-rich.yml`，然后在 Dify 界面：

1. 配置环境变量 `WXZ_BACKEND_BASE_URL`（物小智后端地址）
2. 为 4 个「知识检索」节点绑定对应 dataset_id：
   - 教学库（文本）
   - 纠错规则库（文本）
   - 教学库（视觉辅助）
   - 纠错规则库（视觉）
3. 将 dataset UUID 写入 `config/dify.env`：
   - `DIFY_KB_TEACHING_DATASET_ID=...`
   - `DIFY_KB_CORRECTION_RULES_DATASET_ID=...`

## 第五步：配置后端

`config/dify.env` 中确保：

- `DIFY_WF_TEXT_ASSIST` — 绑定 rich 工作流 App Key
- `DIFY_WF_VISION_CORRECTION` — 可与 text 共用同一工作流（后端按有无图片分流）

## 当前进度

| 实验 | 纠错库 | 教学库 |
|------|--------|--------|
| general | `docs/visual-rules/general.md` | 待完善 |
| newton_rings | ✅ 完整 | ✅ 完整 |
| tensile_steel | 建设中 | 建设中 |

## 规则 ID 命名

`{实验缩写}_STEP{步号}_{问题}` 或 `{实验缩写}_GENERAL_{问题}`

示例：`NR_STEP1_RING_BLUR`、`TS_STEP2_CLAMP_MISALIGN`
