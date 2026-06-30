# 物小智智能体平台

Vue 3 + Spring Boot 大学物理实验多模态智能指导平台。AI 能力通过自建 Dify 工作流对接（未配置 API Key 时使用 JSON 内置 Mock 响应）。

## 项目结构

```
物小智-项目/
├── config/
│   ├── ports.env       # 前后端端口（唯一入口）
│   └── dify.env        # Dify 全部配置（唯一入口）
├── index.html          # 原始 HTML 原型（参考）
├── backend/            # Spring Boot 后端
└── frontend/           # Vue 3 前端
```

## 快速启动

### 端口配置（唯一入口）

前后端端口统一写在项目根目录 `config/ports.env`：

```env
BACKEND_PORT=8082
FRONTEND_PORT=5174
```

部署到其他服务器时，若端口被占用，**只需修改该文件后重新启动**。前端开发服务器会自动将 `/api`、`/uploads` 代理到配置的后端端口。

### 数据库存储

本地开发默认使用 H2 文件库：`backend/data/wuxiaozhi`，无需额外安装数据库。

学校实验室正式部署建议使用 MySQL。配置入口为 `config/mysql.env`：

```env
MYSQL_ENABLED=true
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_DATABASE=wuxiaozhi
MYSQL_USER=root
MYSQL_PASSWORD=你的密码
```

启用后，`scripts/run-backend.bat` 和 `scripts/start.bat` 会自动设置 `SPRING_PROFILES_ACTIVE=mysql`，后端使用 `application-mysql.yml` 连接 MySQL。首次部署前请先在 MySQL 中创建数据库：

```sql
CREATE DATABASE wuxiaozhi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 一键启动

```bat
rem Windows（双击或在 cmd 中运行）
scripts\start.bat
```

```bash
# Linux / macOS
chmod +x scripts/start.sh
./scripts/start.sh
```

脚本会读取 `config/ports.env` 并检查端口是否空闲。

### 分别启动

```bat
rem Windows（推荐，会自动读取 config/ports.env）
scripts\run-backend.bat
scripts\run-frontend.bat
```

```bash
# 前端（vite.config.js 自动读取 config/ports.env）
cd frontend && npm install && npm run dev

# 后端（需与 ports.env 中 BACKEND_PORT 一致）
export BACKEND_PORT=8082   # 与 config/ports.env 保持一致
cd backend && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=$BACKEND_PORT
```

## 功能说明

| 功能 | 说明 |
|------|------|
| 独立账号 | 注册 / 登录，JWT 鉴权 |
| 实验元数据 | `backend/src/main/resources/experiments/*/manifest.json` |
| 步骤引导 | 5 步流程、教程弹窗 |
| 多模态纠错 | 上传图片 + 求助，对接 Dify 视觉工作流 |
| 环境巡检 UI | 左下安全监测：摄像头抽帧 + Dify 环境巡检（`config/dify.env` → `DIFY_WF_ENV_CHECK`） |
| 实验报告 | 页面展示 + DOCX 下载 |

## Dify 配置（唯一入口）

所有 Dify 相关配置集中在项目根目录 **`config/dify.env`**（与 `config/ports.env` 同级，格式相同）。部署时实施人员只需修改该文件并重启后端。

```env
# config/dify.env
DIFY_BASE_URL=http://你的dify地址/v1
DIFY_APP_MODE=chat

# 默认 Key（AI 助手、视觉纠错等）
DIFY_API_KEY=app-文字问答Key

# 各工作流独立 Key（留空则回退 DIFY_API_KEY）
DIFY_WF_VISION_CORRECTION=
DIFY_WF_TEXT_ASSIST=
DIFY_WF_ENV_CHECK=app-环境巡检Key
DIFY_WF_ENV_CHECK_MODE=workflow
DIFY_WF_REPORT_GENERATE=

DIFY_KB_API_KEY=
DIFY_KB_TOP_K=5
```

**说明：** `DIFY_BASE_URL` 所有接口共用；`DIFY_API_KEY` 是原有默认 Key。默认助手 / 视觉纠错是 Chatflow 应用，因此全局 `DIFY_APP_MODE=chat`。安全监测（`DIFY_WF_ENV_CHECK`）是**另一个 Dify Workflow 应用**，Key 与默认不同，需单独填写，并通过 `DIFY_WF_ENV_CHECK_MODE=workflow` 单独覆盖调用模式。

如果后续某个独立 Dify 应用也是 Workflow 类型，给它增加对应的 `DIFY_WF_XXX_MODE=workflow` 配置，不需要把全局 `DIFY_APP_MODE` 改成 workflow。

**重要：** Dify 工作流开始节点需包含输入变量 `query`（后端会自动将用户问题写入 `inputs.query`）。若调用失败，系统会回退到 JSON Mock。

启动后可通过 `GET /api/system/dify-status` 查看各工作流是否已配置 Key。

### 知识库映射

实验编号、步骤编号与 Dify 通用知识库文档/章节的对应关系维护在：

```text
backend/src/main/resources/knowledge-map.yml
```

推荐使用两个通用知识库：

- `teaching`：所有物理实验教学知识点
- `correction_rules`：所有物理实验纠错规则

每个实验在映射文件中固定 `experiment_code`、`category`、知识文档路径，以及每个 `step_id` 对应的 `teachingSection`、`rulesSection` 和检索标签。后续在 Dify 知识库导入 Markdown 时，建议把这些标识写入文档元信息或标题中，保证系统传参、Dify 检索和知识库内容三者稳定对齐。

系统调用 Dify 时会根据当前实验与步骤自动补齐这些映射字段：

```text
teaching_dataset
rules_dataset
teaching_doc
rules_doc
teaching_section
rules_section
retrieval_tags
retrieval_tags_list
knowledge_type
```

如果需要在 Dify 工作流里使用这些变量，请在 Dify 应用开始节点中添加同名输入变量；知识检索节点可用 `experiment_code`、`knowledge_type`、`teaching_doc` / `rules_doc` 等字段做 metadata filter。

### Dify 工作流输出字段约定

**视觉纠错 / 文字问答：**

- `type`: `vision_correction` 或 `text_assist`
- `feedback`: Markdown 文本
- `error_type`, `detail`: 可选
- `marks`: JSON 数组 `[{x,y,w,h,n}]` 或 `marks_json` 字符串

**环境巡检：**

- `level`: `L0` ~ `L3`
- `summary`, `suggestion`

未配置 API Key 时，系统自动使用实验 JSON 中的 `assistMock` 与随机环境等级 Mock。

## 新增实验

在 `backend/src/main/resources/experiments/` 下新增一个实验目录，推荐结构如下：

```text
experiments/
  your_experiment_code/
    manifest.json          # 实验流程骨架：步骤、数据字段、设备类型、Dify 知识库绑定
    steps/
      1.md                 # 第 1 步说明：Description / Steps / Warnings
      2.md
    report-guide.md        # 报告知识巩固与后续学习路径
    teaching-knowledge.md  # 给 Dify 知识库或人工维护使用的教学知识
    visual-rules.md        # 给视觉纠错工作流使用的规则说明
```

新增实验后，也要同步更新 `backend/src/main/resources/knowledge-map.yml`，为该实验登记教学知识文档、纠错规则文档和每个步骤的稳定章节标识。

后端会自动读取 `*/manifest.json`，并根据 `guidePath`、`reportGuidePath` 将 Markdown 内容装配回前端需要的实验配置。旧版 `experiments/*.json` 平铺结构仍兼容，但后续建议统一使用目录结构。

`manifest.json` 中步骤示例：

```json
"steps": {
  "1": {
    "title": "仪器检查与光路调节",
    "correctionMode": "vision",
    "guidePath": "steps/1.md"
  }
}
```

步骤 Markdown 使用固定二级标题：

```md
# 仪器检查与光路调节

## Description

本步骤的简短说明。

## Steps

- 第一步操作
- 第二步操作

## Warnings

- 注意事项
```

每个实验可配置 Dify 知识库（一实验一库）：

```json
"dify": {
  "datasetId": "Dify 知识库 UUID"
}
```

求助时（**含带图**）后端会先调用 `/datasets/{datasetId}/retrieve`，将召回内容写入 `inputs.kb_context` 再调 Chatflow。Dify 工作流开始节点需增加变量：`experiment_code`、`kb_context`、`dataset_id`（可选）、`step_title` 等；**带图分支的 LLM 也要引用 `kb_context`**，不要只做视觉分析。

知识库检索 API Key 可在 `config/dify.env` 的 `DIFY_KB_API_KEY` 单独配置，留空则使用 `DIFY_API_KEY`。

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/experiments` | 实验列表 |
| POST | `/api/sessions` | 开始实验会话 |
| POST | `/api/sessions/{id}/assist` | 求助 / 纠错 |
| POST | `/api/sessions/{id}/env-check` | 环境巡检 |
| GET | `/api/sessions/{id}/report` | 报告 JSON |
| GET | `/api/sessions/{id}/report/docx` | 报告 DOCX 下载 |
