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
| 实验元数据 | `backend/src/main/resources/experiments/*.json` |
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
DIFY_WF_REPORT_GENERATE=

DIFY_KB_API_KEY=
DIFY_KB_TOP_K=5
```

**说明：** `DIFY_BASE_URL` 所有接口共用；`DIFY_API_KEY` 是原有默认 Key。安全监测（`DIFY_WF_ENV_CHECK`）是**另一个 Dify 应用**，Key 与默认不同，需单独填写。

若应用是纯 Workflow 类型，将 `app-mode` 改为 `workflow`。

**重要：** Dify 工作流开始节点需包含输入变量 `query`（后端会自动将用户问题写入 `inputs.query`）。若调用失败，系统会回退到 JSON Mock。

启动后可通过 `GET /api/system/dify-status` 查看各工作流是否已配置 Key。

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

在 `backend/src/main/resources/experiments/` 新增 JSON 文件，格式参考 `tensile_steel.json`，重启后端即可。

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
