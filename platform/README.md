# 物小智智能体平台

实验步骤完成确认、教师检查评分、整改复查及过程成绩配置见 [实验过程考核](docs/process-assessment.md)。

本目录为教学平台。独立设备采集服务位于同级 [acquisition-service](../acquisition-service/README.md)，接口契约位于 [contracts](../contracts/acquisition-v1.openapi.yaml)。平台通过 HTTP 调用设备服务，不安装 Python 依赖、不管理采集进程。本文命令均以 `platform` 为工作目录。

实验室正式部署、摄像头接入及“管理员配置工位 → 教师直接巡视”最小流程见 [实验室部署说明](docs/lab-deployment.md)。正式发行包使用 `scripts/build-lab-release.ps1` 构建，前端、API 和视频由一个 JAR 提供。

学生“我的学习”、教师“学习统计”、积分、能力雷达与班级排行的规则和部署说明见 [学习统计说明](docs/learning.md)。

Vue 3 + Spring Boot 大学物理实验多模态智能指导平台。AI 能力通过自建 Dify 工作流对接（未配置 API Key 时使用 JSON 内置 Mock 响应）。

## 项目结构

```
物小智-项目/
├── config/
│   ├── application.yml # 数据库、端口、管理员、摄像头及采集连接
│   └── dify.env        # Dify 全部配置（唯一入口）
├── scripts/            # 启动、部署和知识库维护脚本
├── docs/               # 开发、部署及 Dify 接入说明
├── backend/            # Spring Boot 后端
└── frontend/           # Vue 3 前端
```

## 快速启动

### 端口配置（唯一入口）

源码启动和正式部署均读取外部 `config/application.yml`。首次拉取源码后，将 `application.yml.example` 复制为 `application.yml` 并填写实际值。前后端端口配置示例：

```yaml
server:
  port: 8082
wuxiaozhi:
  frontend:
    port: 5174
```

部署到其他服务器时，若端口被占用，**只需修改该文件后重新启动**。前端开发服务器会自动将 `/api`、`/uploads` 代理到配置的后端端口。

### 数据库存储

问答图片、采集图片、标注图和资料附件均作为二进制存入 MySQL 的 `stored_uploads` 表，访问地址保持 `/uploads/文件名`。首次升级会从 `wuxiaozhi.upload.dir` 指定的旧目录自动导入历史文件，确认启动日志导入完成后，迁移完整数据库即可一并迁移图片，无需再迁移 uploads 目录。详见[升级和历史数据](docs/lab-deployment.md#升级和历史数据)。

对话图片在点击“发送”时才保存；获取图像或选择照片后仅在浏览器预览，未发送即清除或离开页面的图片不入库。

本地开发、测试和正式部署均使用 MySQL。在 `config/application.yml` 的现有 `spring.datasource` 中填写连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/wuxiaozhi?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: wuxiaozhi
    password: "数据库密码"
```

MySQL 是唯一数据库，无需额外启用 profile。程序不再读取 `mysql.env`、`ports.env`、`admin.env` 或 `lab.yml`，JAR 内不再保留同名数据库默认配置。首次部署前请先创建数据库，并为专用账号授权：

```sql
CREATE DATABASE wuxiaozhi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 后端自动化测试

集成测试也使用 MySQL，每个测试上下文创建独立的 `wxz_test_...` 随机数据库，结束后自动删除，不连接教学数据库。测试账号需拥有创建、删除测试库及库内建表读写权限。PowerShell 中设置仅供测试进程使用的连接信息后执行：

```powershell
$env:WXZ_TEST_MYSQL_SERVER = 'jdbc:mysql://127.0.0.1:3306/'
$env:WXZ_TEST_MYSQL_USER = '测试专用账号'
$env:WXZ_TEST_MYSQL_PASSWORD = '测试专用密码'
Set-Location backend
mvn test
```

测试连接未配置或 MySQL 不可用时测试会失败，不使用运行环境的教学数据库或其他数据库替代。

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

脚本会读取 `config/application.yml` 并检查端口是否空闲。

| 操作 | 入口 |
|---|---|
| 开发环境启动前后端 | `scripts/start.bat` |
| 单独启动前端 / 后端 | `scripts/run-frontend.bat` / `scripts/run-backend.bat` |
| 停止服务 | `scripts/stop.bat` |
| 安装前端依赖 | `scripts/npm-install.bat` |
| 生成正式部署包 | `powershell -ExecutionPolicy Bypass -File scripts/build-lab-release.ps1` |
| 启动正式服务 | 解压发布包后运行包内 `start.bat`，详见[部署说明](docs/lab-deployment.md) |
| 数据库连接检查 | `scripts/test-mysql-connect.bat` |
| 收集启动诊断 | `scripts/diagnose.bat` |

`scripts/_read-*.bat` 是源码启动脚本读取配置的内部依赖。`start-lab-release.bat` 和 `start-lab-release.sh` 是正式发布包的启动模板，由构建脚本复制到包内使用。

### 分别启动

```bat
rem Windows（推荐，会自动读取 config/application.yml）
scripts\run-backend.bat
scripts\run-frontend.bat
```

```bash
# 前端（vite.config.js 自动读取 config/application.yml）
cd frontend && npm install && npm run dev

# 后端（读取 ../config/application.yml）
cd backend && mvn spring-boot:run
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

所有 Dify 相关配置集中在项目根目录 **`config/dify.env`**，与 `application.yml` 同目录，保留独立的 env 格式。修改后重启后端。

仓库同时提供 `config/dify.env.example` 作为配置模板。独立部署时，可参考模板修改 `config/dify.env` 中的地址与 API Key。

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

普通对话工作流使用输入变量 `query`（后端将用户问题写入 `inputs.query`）。教师报告预评使用独立 Workflow，输入为报告文件等五个字段，输出仅为百分制 `score` 和 `comment`，配置步骤见 [教师报告预评说明](docs/dify/SETUP-REPORT-REVIEW-WORKFLOW.md)。教师预评失败会显示错误，不生成 Mock 成绩。

启动后可通过 `GET /api/system/dify-status` 查看各工作流是否已配置 Key。

### 知识库映射

实验编号、步骤编号与 Dify 通用知识库文档/章节的对应关系维护在：

```text
backend/src/main/resources/knowledge-map.yml
```

教学知识与纠错规则统一维护在 `backend/src/main/resources/experiments/<实验编号>/` 下的 `teaching-knowledge.md` 和 `visual-rules.md`。运行 `python scripts/sync_kb.py` 可生成 Dify 导入包；导出目录 `docs/kb/export/` 不提交到 Git。维护方式见 [知识库说明](docs/kb/README.md)。

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

### 前端启动与日志

Windows 双击 `scripts/start.bat` 分别打开前后端窗口，前端直接在自己的窗口中运行 Vite；请保留窗口（可以最小化），关闭前端运行窗口会停止前端。后端已运行时显示后端状态窗口，不会强制结束已有服务。默认访问 `http://localhost:5174/`，后端端口为 `8082`，均以 `config/application.yml` 为准。

此前改成的 WMI 隐藏后台监护方式已从默认启动链路移除：现场出现过监护进程与 Vite 一起消失、状态文件仍写着 running 的情况。单独验证“杀掉子进程后重启”无法覆盖这一问题。历史日志没有进程终止审计，无法确认具体结束者，不能将它说成 Vite 页面异常或已证实的 Windows 内存回收。

可单独运行 `scripts/run-frontend.bat`；`scripts/start-frontend.ps1` 兼容入口也在前台直接运行，不创建 WMI worker。重复启动只复用该项目的实际端口进程，不依赖旧的 JSON 状态。前端避免标准输入关闭引发 Vite 自动退出。

Windows 启动脚本在 Node 22 及以上使用 `--no-maglev`，规避本机 Windows 26200 / Node 24 上疑似 Maglev 原生崩溃兼容问题（已观察退出码 `-1073740791 / 0xC0000409`；相关记录见 [Node #62260](https://github.com/nodejs/node/issues/62260)）。若原生异常退出码为 `0xC0000409` 或 `0xC0000005`，同一窗口最多重试 3 次，每次间隔 2 秒；正常退出、端口冲突、主动停止均不重试。退出码及重试次数写入 `logs/frontend-launcher.log`，实际 Node 参数写入生命周期日志。此兼容措施不等同于已通过转储确认具体崩溃模块。

`logs/frontend-lifecycle.jsonl` 记录前端启动、退出、异常和每分钟心跳（含进程号与内存使用），日志写入失败不会影响服务。旧的 `frontend-service*.json` 只作为历史记录保留。停止服务用 `scripts/stop.bat`；只停止前端用 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/stop-frontend.ps1`。验证启动链路可运行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-frontend-startup.ps1`，它使用独立临时端口，不会停止正在使用的服务。

## 管理端与工位

- 管理员进入“实验室与工位”，维护工位编号、名称、视频流地址或摄像头 IP、启用状态。
- 教师进入“课堂巡视”，选择实验室即可看到各工位视频，支持放大查看。观看不依赖实验项目、教学班、课次、学生登录或终端配对。
- 教师可在工位上选择教学班、勾选学生并保存当前座位绑定；支持多人同组、调整和整室清空。同一学生在同一实验室只能绑定一个工位，教师只能管理本班名单。课前核对、课后清空，不需要学生扫码或登录。
- 学生端不显示入座和摄像头绑定入口。当前巡视不做自动身份识别和 AI 巡检，已有历史数据保留。
- IP 模式按 `config/application.yml` 的 `wuxiaozhi.lab.ip-stream-template` 展开该型号实际播放路径；不同型号可直接填写完整 FLV 视频地址。RTSP 需先由视频网关转换。
- 教师与管理员可观看巡视画面；学生无权查看，视频仍通过后端鉴权转发。
- 部署配置和正式包构建见 [实验室摄像头部署说明](docs/lab-deployment.md)。

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

## 维护文档

- [教学班、实验任务与报告流程](docs/teaching-workflow.md)
- [实验室部署与工位巡视](docs/lab-deployment.md)
- [学生学习与教师统计](docs/learning.md)
- [Dify 实验指导工作流](docs/dify/SETUP-GUIDANCE-WORKFLOW.md)
- [Dify 教师报告预评](docs/dify/SETUP-REPORT-REVIEW-WORKFLOW.md)
- [Dify 学生报告助手](docs/dify/SETUP-REPORT-ASSIST-WORKFLOW.md)
- [Android 平板打包](docs/android-apk.md)
