# 物小智设备采集服务

独立运行的设备接入服务。当前 Python 实现接入 Windows DirectShow UVC 显微镜，提供真实 JPEG 成像，不自动使用笔记本内置摄像头，不生成演示画面。没有设备也可启动，健康检查保持可用，设备列表显示离线。

## 安装和启动

Windows，在本目录执行：

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item config/service.json.example config/service.json
.\start.bat
```

已有 `config/service.json` 时保留原配置。也可直接运行 `.venv\Scripts\python.exe run.py --config config/service.json`；配置相对路径始终相对于本服务目录。

停止：在运行窗口按 Ctrl+C，或执行 `powershell -ExecutionPolicy Bypass -File stop.ps1`。停止脚本只处理命令行匹配本目录 `run.py` 的 Python 进程，不按端口结束其他应用。

运行测试：`python -m unittest discover -s tests -v`。接口测试不要求连接硬件。

`start.sh` 使用 `.venv/bin/python` 启动接口进程；当前 UVC 适配器的设备枚举依赖 Windows DirectShow，Linux 接入真实设备需要实现对应适配器，不能直接以进程启动成功判断硬件可用。

## 配置

配置文件为 `config/service.json`，模板为同目录 `.example`。真实配置不提交 Git。

| 字段 | 用途 |
| --- | --- |
| `host`、`port` | 监听地址和端口，默认仅本机 127.0.0.1:8765 |
| `apiToken` | 平台访问密钥，也可通过 `ACQUISITION_API_TOKEN` 覆盖 |
| `devices[].id` | 稳定设备编号，与平台 `device-id` 一致 |
| `devices[].name` | 设备显示名称 |
| `devices[].nameKeyword` | 系统设备名匹配关键字，默认 UVC |
| `devices[].backend` | OpenCV 后端：dshow、msmf 或 any |
| `devices[].width/height/fps/warmup` | 成像参数，只在采集服务配置 |

采集服务部署在设备连接的电脑上。平台在其他电脑时，将 `host` 配为 `0.0.0.0`，设置非空访问密钥并允许平台访问该端口；平台填采集电脑实际 IP，不填 `0.0.0.0`。跨不可信网络通过 HTTPS 反向代理访问。

当前按设备名称关键字匹配第一台合格显微镜。如果多台同名设备接在同一台电脑上，需先实现稳定设备路径/序列号选择；不能仅靠修改显示名称或设备 id 区分。首版以每个采集节点一台显微镜部署为宜。

## 与平台通信

接口契约：[OpenAPI v1](../contracts/acquisition-v1.openapi.yaml)。

- `GET /api/v1/health`：进程健康，不代表设备已连接。
- `GET /api/v1/devices`：设备编号、能力及发现状态，鉴权后访问。
- `POST /api/v1/devices/{deviceId}/captures`：请求体 `{"requestId":"UUID"}`，返回 JPEG 的 Base64 内容、尺寸、设备编号、采集时间及原请求编号。

鉴权头为 `Authorization: Bearer <密钥>`。当前采集同步完成；相同请求在 60 秒内且仍在最近 8 个缓存结果中时复用成功结果。缓存仅在内存，重启后失效；设备占用返回 409，设备不可用返回 503。平台不自动重试采集，也不因请求失败重启采集进程。

平台的 `config/lab.yml` 配置示例：

```yaml
wuxiaozhi:
  acquisition:
    enabled: true
    default-device: microscope
    experiments:
      newton_rings: microscope
    devices:
      microscope:
        base-url: http://采集电脑IP:8765
        capture-path: "/api/v1/devices/{deviceId}/captures"
        device-id: microscope-01
        api-token: "与采集服务相同的密钥"
```

以上连接配置统一写在平台 `config/lab.yml`，修改后重启平台后端，无需修改源码或重新打包。`capture-path` 与采集服务实际接口一致，`{deviceId}` 由平台自动替换。`base-url` 可带反向代理前缀，最终请求地址为 `base-url` 加上替换后的 `capture-path`。`contracts` 是请求和响应格式的说明，不承担部署配置。

新增平台连接节点时，在 `devices` 下增加配置，并在 `experiments` 下指定实验与配置名的对应关系。各节点可独立配置地址、设备编号、访问密钥和超时；本 Python 服务自己的设备参数仍在 `config/service.json` 维护。

平台保留现有学生成像入口，校验用户权限后发起采集，核对返回的请求编号、设备编号及图像，将结果保存至平台上传目录。采集服务不访问平台数据库，也不保存学生身份或成绩。

## 后续接入 Java

Java 服务实现相同的 API v1、鉴权和错误码，平台只需修改服务地址、设备编号及密钥。驱动、SDK 和运行环境由采集服务维护，平台无需引入设备 SDK。以后增加数值或连续曲线采集时扩展新接口和能力标识，保持现有图像接口兼容；目前平台旧仪器读数/拉伸曲线中的演示数据仍属于演示逻辑，并未因此成为真实设备数据。

Python 新设备适配器放在 `src/acquisition/adapters/`；当前运行框架位于 `src/acquisition/server.py`。不要在平台内添加 Python 进程控制，也不要让 Java 实现依赖 Python 包。

可选的 USB 工位巡视推流工具位于 `tools/workstation_camera_agent.py`，沿用 `WXZ_SERVER`、`WXZ_STATION_ID`、`WXZ_STATION_KEY` 配置，独立运行；它与仪器显微镜采集是不同用途，不随本服务自动启动。

## 发布

Windows 在源码目录执行 `powershell -ExecutionPolicy Bypass -File build-release.ps1`，输出到本目录 `dist/`。发布包包含源码、依赖清单、空配置模板、启动入口及接口契约，不包含本机密钥、虚拟环境、平台数据库或历史采集结果。解压后按上述步骤创建虚拟环境、安装依赖并配置设备。
