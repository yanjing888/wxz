# 物小智工程

本仓库包含两个独立运行的服务，通过 HTTP API 通信。

| 目录 | 职责 | 启动入口 |
| --- | --- | --- |
| [platform](platform/README.md) | Vue 前端、Spring Boot 教学后端、教学数据与报告 | `platform/scripts/start.bat` |
| [acquisition-service](acquisition-service/README.md) | 设备驱动、显微镜成像及采集接口；当前使用 Python | `acquisition-service/start.bat` |
| [contracts](contracts/acquisition-v1.openapi.yaml) | 与实现语言无关的接口契约 | OpenAPI 3.0 |

Windows 源码环境可双击根目录 `start.bat`，分别打开平台后端、前端、采集服务三个窗口。平台访问地址默认 `http://localhost:5174/`；平台后端为 8082，采集服务为 8765。平台可以在采集服务离线时独立使用。

首次启动采集服务需安装依赖，操作见其 README。日常也可分别使用两个目录自己的启动入口；关闭或重启平台不会结束采集服务。根目录 `stop.bat` 停止本工程的三个服务。

平台访问采集服务的 IP、端口、接口路径、设备编号、密钥和超时，统一在 `platform/config/application.yml` 中修改。正式包对应解压目录的 `config/application.yml`，修改后重启平台后端即可，不需要修改源码或重新打包。

开发、测试与正式部署均使用 MySQL。平台数据库连接、端口、管理员初始化及采集地址统一配置在 `platform/config/application.yml`，修改后重启对应服务。模板为同目录 `application.yml.example`；Dify 配置仍在 `dify.env`。旧的 `mysql.env`、`ports.env`、`admin.env` 和 `lab.yml` 已迁入新文件并停止读取。数据库不可用时启动报错，不切换到其他数据库。

```yaml
wuxiaozhi:
  acquisition:
    enabled: true
    default-device: microscope
    experiments:
      newton_rings: microscope
    devices:
      microscope:
        base-url: http://192.168.1.100:8765
        capture-path: "/api/v1/devices/{deviceId}/captures"
        device-id: microscope-01
        api-token: "与采集服务相同的密钥"
```

上例实际请求 `http://192.168.1.100:8765/api/v1/devices/microscope-01/captures`。接口路径须与采集服务实际提供的路径一致，请求和响应格式保持双方约定。`contracts` 只说明接口格式，程序不读取它作为部署配置，部署人员无需修改。

增加设备时，在 `devices` 下增加同级配置，每台可连接不同 IP、端口及接口路径；在 `experiments` 下用实验代码指定配置名。未单独指定的实验使用 `default-device`，已指定但不存在或被停用的设备会明确报错，不回退到其他设备。完整模板见 `platform/config/application.yml.example`。

采集服务自己的监听地址和设备参数位于 `acquisition-service/config/service.json`。两端配置相同的访问密钥，真实密钥和数据库配置不进入 Git。设备驱动和成像参数只在采集服务维护。

正式部署：平台按 [部署说明](platform/docs/lab-deployment.md) 打包为单 JAR；采集服务单独部署在连接 USB/串口设备的电脑上。平台配置该采集电脑的地址，教师和学生浏览器仍统一访问平台。

日常按改动服务单独打包：platform 改动使用 `platform/scripts/build-lab-release.ps1`，acquisition-service 改动使用 `acquisition-service/build-release.ps1`。各自在本服务 dist 中新增一个带时间后缀的 ZIP，保留已有 ZIP，不留展开目录或说明、测试报告；程序、运行脚本、配置与依赖清单保持完整。只有首次完整部署或两边都需要发布时，才使用本地 `deployment/build-windows-release.ps1` 生成根目录 dist 中的双服务包。deployment 目录仍按原约定仅在本地维护，不提交 Git。

当前接口支持按实验选择预配置设备并采集 JPEG 图像。后续 Java 采集服务遵循同一份契约即可替换或接入，平台无需安装 Python、Java 设备 SDK 或驱动。同一实验下多个工位的设备自动路由、数值/曲线协议属于后续扩展。

迁移保留原 Git 历史、数据库、上传文件、配置和历史发布包。原项目使用说明现位于 `platform/README.md`。
