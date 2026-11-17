# 专家顾问连接

“物小智”与“原理答疑”中的回复助手可切换为实验助教或专家顾问。

本机使用 SSH 隧道访问专家网关。相关脚本、配置及依赖全部位于本项目中，不依赖其他工作目录。

首次在 Windows 部署时，在 platform 目录运行：

```powershell
python -m venv .expert-relay-venv
.expert-relay-venv/Scripts/python.exe -m pip install -r scripts/expert-relay-requirements.txt
Copy-Item config/expert-relay.json.example config/expert-relay.json
```

填写 `config/expert-relay.json` 中的实际连接信息和 SSH 主机公钥。现有本机配置已恢复，无需覆盖。

日常使用 `scripts/start.bat` 或 `scripts/run-backend.bat`。后端启动前会启动隧道，读取远端应用密钥，生成 `config/expert.env`；后端自动加载该文件。重复启动复用本项目已有隧道；连接失败会明确报错。`scripts/stop.bat` 同时停止隧道。

没有隧道配置文件时，后端按原有方式启动。使用可直接访问的专家网关时，也可单独配置 `config/expert.env` 中的 `octop.preview.enabled`、`octop.preview.base-url`、`octop.preview.expert-id` 和 `octop.preview.gateway-key`。

实际 SSH 配置、生成的密钥配置及虚拟环境均被 Git 忽略。迁移机器时需安全迁移实际配置，并重新创建虚拟环境。不要将密钥放入前端代码。

连接日志：`logs/expert-relay.log`、`logs/expert-relay.err`。独立管理隧道可运行 `scripts/start-expert-relay.ps1` 与 `scripts/stop-expert-relay.ps1`。

## 会话恢复

每次“新对话”使用独立编号，对应 Octop 的一个 `thread_id`。MySQL 的 `expert_conversation_links` 只保存学生账号、会话编号、专家会话编号、标题及模式，消息内容仍保存在 Octop。刷新后按账号恢复最近的会话，后续提问继续使用相同 `thread_id`；只有点击“新对话”并发送消息才建立新会话。

项目内 `scripts/expert_history_proxy.py` 通过现有 SSH 连接读取 Octop 的历史接口，默认监听本机 18090，仅接受应用密钥鉴权。浏览器通过物小智后端访问；后端先验证会话归属，不向学生暴露服务账号或密钥。

`expert-gateway/app.py` 是配套专家网关源码，已补上显式 `thread_id` 转发。部署的网关也必须包含这一改动，否则旧网关可能丢弃编号并回到默认会话。旧测试阶段未保存对应关系的对话，不会被自动混入学生个人历史。
