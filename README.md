# 物小智智能体平台

Vue 3 + Spring Boot 大学物理实验多模态智能指导平台。AI 能力通过自建 Dify 工作流对接（未配置 API Key 时使用 JSON 内置 Mock 响应）。

## 项目结构

```
物小智-项目/
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
| 环境巡检 UI | 右上摄像头为 UI 演示（无真实 getUserMedia），定期检查调用后端 Mock/Dify |
| 实验报告 | 页面展示 + DOCX 下载 |

## Dify 配置（自建部署）

已在 `application-local.yml` 写入本地密钥（该文件已加入 `.gitignore`）。

默认连接：

- Base URL: `http://188.18.18.149:5001/v1`
- App Mode: `chat`（对话型 / Chatflow 应用，走 `/chat-messages`）

若你的应用是纯 Workflow 类型，将 `app-mode` 改为 `workflow`。

**重要：** Dify 工作流开始节点需包含输入变量 `query`（后端会自动将用户问题写入 `inputs.query`）。若调用失败，系统会回退到 JSON Mock。

```yaml
wuxiaozhi:
  dify:
    base-url: http://你的dify地址/v1
    api-key: ${DIFY_API_KEY:}          # 全局 Key（可选，作为各工作流未单独配置时的回退）
    workflows:
      vision-correction: app-视觉纠错Key
      text-assist: app-文字问答Key
      env-check: app-环境巡检Key
      report-generate: app-报告生成Key
```

环境变量示例（每个 Dify 工作流应用各有一个 API Key）：

```bash
set DIFY_API_KEY=app-xxxx
set DIFY_WF_VISION_KEY=app-vision-xxxx
set DIFY_WF_TEXT_KEY=app-text-xxxx
set DIFY_WF_ENV_KEY=app-env-xxxx
```

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

知识库检索 API Key 可在 `wuxiaozhi.dify.knowledge.api-key` 单独配置，留空则使用 `text-assist` Key。

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
