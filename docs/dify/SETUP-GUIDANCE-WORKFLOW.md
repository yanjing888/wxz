# 物小智实验指导 Dify 工作流 — 导入与绑定步骤

对应 DSL：`docs/dify/物理实验智能纠错与指导-丰富节点完整版.yml`（由 `scripts/build_step_aware_dify_workflow.py` 生成）

## 第一步：生成最新 DSL

修改提示词或工作流逻辑后执行：

```bash
python scripts/build_step_aware_dify_workflow.py
```

（知识库内容变更则先 `python scripts/sync_kb.py`）

## 第二步：导入 Dify

1. 打开 Dify **工作室** → **导入 DSL**
2. 选择 `docs/dify/物理实验智能纠错与指导-丰富节点完整版.yml`
3. 若已有旧版应用，建议新建应用导入，避免节点 ID 冲突

## 第三步：配置环境变量

在应用 **环境变量** 中设置：

| 变量 | 示例 | 说明 |
|------|------|------|
| `WXZ_BACKEND_BASE_URL` | `http://188.18.66.215:8082` | 物小智后端根地址（Dify 能访问） |

HTTP 节点会请求：`GET {WXZ_BACKEND_BASE_URL}/api/public/experiments/{experiment_code}`（无需登录）

## 第四步：创建并导入知识库

1. 新建数据集 **物理实验教学知识点通用库**（teaching）
2. 新建数据集 **物理实验纠错规则通用库**（correction_rules）
3. 从下面目录取 **`.md` 文件**导入（每个 md 旁有同名 `.metadata.json`，供填 Dify 文档 metadata 时参考）

### 4.1 教学库 — 具体导入文件

目录：`docs/kb/export/teaching/`

**三个上线实验（必导）：**

| 导入这个文件 | 实验 | metadata 对照 |
|-------------|------|---------------|
| `newton_rings.md` | 牛顿环 | `newton_rings.metadata.json` |
| `air_wedge_thickness.md` | 空气劈尖 | `air_wedge_thickness.metadata.json` |
| `microscope_length_measurement.md` | 读数显微镜 | `microscope_length_measurement.metadata.json` |

**可选：** `general.md`、`tensile_steel.md`（三实验主流程不依赖）

### 4.2 纠错规则库 — 具体导入文件

目录：`docs/kb/export/correction_rules/`

**三个上线实验（必导）：**

| 导入这个文件 | 实验 | metadata 对照 |
|-------------|------|---------------|
| `newton_rings.md` | 牛顿环 | `newton_rings.metadata.json` |
| `air_wedge_thickness.md` | 空气劈尖 | `air_wedge_thickness.metadata.json` |
| `microscope_length_measurement.md` | 读数显微镜 | `microscope_length_measurement.metadata.json` |

**可选：** `general.md`、`tensile_steel.md`

### 4.3 每个文档要设的 metadata（Dify 文档属性）

教学库 `air_wedge_thickness.md` 示例（见 `air_wedge_thickness.metadata.json`）：

```json
{
  "experiment_code": "air_wedge_thickness",
  "kb_type": "teaching",
  "doc": "air_wedge_thickness/teaching-knowledge.md",
  "sectionKey": "air_wedge_thickness"
}
```

纠错库同实验文件示例：

```json
{
  "experiment_code": "air_wedge_thickness",
  "kb_type": "correction_rule",
  "doc": "air_wedge_thickness/visual-rules.md",
  "sectionKey": "air_wedge_thickness"
}
```

正文里的 `> **section_key:** \`xxx.step.N.teaching|rules\`` 用于步骤级检索；文档级 metadata 填实验级 `sectionKey` 即可。

### 4.4 为什么用 Markdown，不用 PDF / Excel？

| 格式 | 建议 | 原因 |
|------|------|------|
| **Markdown** | ✅ 主库用这个 | 与 `docs/teaching-knowledge/`、`docs/visual-rules/` 源文件一致；`sync_kb.py` 一键导出；步骤 `section_key` 可被工作流 metadata 过滤 |
| **PDF** | ⚠️ 仅补充 | Dify 能导，但难保留章节锚点；扫描版/公式解析差；无法走现有同步脚本 |
| **Excel** | ❌ 不适合主库 | 适合「一行一条 rule_id」的表，不适合长文教学；分段按单元格切，检索易断章 |

**结论：** 继续导入 `docs/kb/export/` 下的 md。原始 PDF 实验指导书可作**额外附录库**，不要替代带 metadata 的结构化 md，否则按实验/步骤过滤会失效。

修改内容流程：编辑 `docs/` 下 md → `python scripts/sync_kb.py` → 在 Dify 更新或重新导入对应文档。

## 第五步：绑定工作流内 4 个知识检索节点

| 节点名称 | 绑定数据集 |
|----------|-----------|
| 知识检索：教学库（文本） | teaching |
| 知识检索：纠错规则库（文本） | correction_rules |
| 知识检索：教学库（视觉辅助） | teaching |
| 知识检索：纠错规则库（视觉） | correction_rules |

节点已配置 metadata 过滤：

- `experiment_code` = 当前实验
- `kb_type` = teaching / correction_rule
- `sectionKey` contains 当前步骤 section（如 `newton_rings.step.3.rules`）

## 第六步：发布并写入后端配置

1. Dify 应用 **发布** → 复制 API Key
2. 编辑 `config/dify.env`：

```env
DIFY_WF_TEXT_ASSIST=app-你的Key
DIFY_KB_TEACHING_DATASET_ID=...
DIFY_KB_CORRECTION_RULES_DATASET_ID=...
```

3. 重启后端：`scripts/run-backend.bat`

## 第七步：Dify 内调试（手动传参）

在预览中填写：

```json
{
  "experiment_code": "air_wedge_thickness",
  "experiment_type": "空气劈尖干涉测量薄片厚度",
  "step_id": "3",
  "step_title": "测量数据采集",
  "teaching_section": "air_wedge_thickness.step.3.teaching",
  "rules_section": "air_wedge_thickness.step.3.rules",
  "retrieval_tags": "air_wedge_thickness step:3 条纹计数"
}
```

用户问题（sys.query）：`条纹数 n′ 和 L′ 怎么对应？`

带图测试：上传当前步骤照片，应走视觉路径并返回 regions。

## 第八步：物小智前端联调

1. 学生进入三个实验之一，切换到对应步骤
2. **纯文字提问** → 文本路径，结合步骤 + 双库
3. **上传图片** → 视觉路径，返回文字 + 红框 marks
4. **数据步骤填表提交** → 仍走文本路径（带 `data_json`）

后端会自动传入：`step_id`、`step_title`、`step_desc`、`step_guide`、`teaching_section`、`rules_section`、`retrieval_tags` 等。

## 常见问题

| 现象 | 处理 |
|------|------|
| **物小智页面空白、Dify 预览却有回复** | 多为 HTTP manifest 失败导致工作流中断。Dify 里 `WXZ_BACKEND_BASE_URL` 默认连 `127.0.0.1:8082`（Dify 本机），不是物小智后端。见下方「空白回复」 |
| **空白回复（无文字只有评价按钮）** | 1）Dify 应用环境变量：`WXZ_BACKEND_BASE_URL=http://后端IP:8082`（须从 Dify 服务器能 curl 通）；**或** 2）节点「HTTP：拉取实验 manifest」→ **异常处理** → **默认值**，输出 `body` 设为 `{}`，失败时继续（后端已传 `step_title` / `step_guide` / `teaching_section` 等，可不依赖 HTTP） |
| HTTP 拉 manifest 失败 | 检查 `WXZ_BACKEND_BASE_URL`、后端是否启动、`/api/public/experiments/{code}` 是否可访问 |
| 检索结果为空 | 知识库未绑定 dataset_id；或文档 metadata 缺少 `experiment_code` |
| 三个实验答案串了 | 检查 metadata 过滤；重新导入带 step section 的 md |
| 有图无框 | 看视觉 LLM 是否输出 regions；后端会从「Code：视觉 JSON 清洗」节点解析 |

### 空白回复：原因说明

物小智前端走 **流式** `/chat-messages`（`response_mode=streaming`）。当 HTTP 节点连不上后端时，Chatflow 在 `workflow_finished` 处 **failed**，不会走到 Answer 节点，因此 SSE 里没有 `message` 正文，页面就只剩空气泡。

在 Dify **预览**里若手动填了步骤参数、或只测了后半段节点，可能仍能看到回复，所以会出现「Dify 里有、页面上没有」的差异。
