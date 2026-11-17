# 物小智配套专家网关源码

`app.py` 对应现有远端 `octop_expert_gateway.app` 服务。本次改动仅补上经应用鉴权后传入的 `thread_id` 转发，使物小智可以明确指定本次对话，避免发送到服务账号的默认会话。

这份源码保存在当前项目内。服务部署时将它安装为 `octop_expert_gateway/app.py`，沿用现有网关的 FastAPI、httpx、websockets 运行环境和外置 `config.json`。不要把实际网关配置或应用密钥提交到仓库。

学生账号与会话归属校验由物小智后端负责；不要允许浏览器直接持有网关应用密钥。会话正文由 Octop 保存，物小智通过会话编号读取对应历史。
