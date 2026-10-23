# 实验室摄像头最小部署与使用说明

## 当前业务范围

管理员配置实验室、工位和摄像头地址；教师打开“课堂巡视”，直接查看当前实验室全部工位的视频，并可放大查看。学生不参与摄像头配置和观看流程。

学生绑定为可选的当前座位标记，由教师直接选择名单；不需要终端配对、扫码、课次或学生登录。历史数据保留，旧功能不作为这版巡视的前置条件。自动 AI 巡检默认关闭。

## 管理员操作

1. 登录管理员账号，进入“实验室与工位”。
2. 选择实验室，必要时点击“新增实验室”。
3. 点击“新增工位”，填写工位编号、名称（可留空自动生成）、视频流地址或 IP，勾选启用后保存。
4. 点击“测试画面”。能播放后，教师端就可以查看同一工位。后续通过“编辑配置”修改地址或停用。

摄像头还未到位时可以暂时留空地址，工位会显示待配置。已配置仅代表保存了地址，播放窗口成功显示实际画面才说明视频可用。

## 教师操作

登录教师账号，进入“课堂巡视”，选择实验室即可同时预览该室所有已配置的工位。点击“放大查看”可查看单个工位细节。切换离开巡视页后会停止当前页面的视频连接。

巡视独立于当前选中的实验项目和教学班，不需要点击“开始本次课”，也不要求学生登录。该最小版本允许启用的教师和管理员账号查看全部已配置实验室，学生账号无权获取巡视列表和视频。

课前点击工位上的“绑定学生”，选择教学班、勾选学生后保存。工位卡片显示姓名和班级；一工位可多人同组（最多 8 人），同一学生在同一实验室只允许绑定一个工位。已在其他工位的学生不可重复选择，需先解除原绑定。点击“调整学生”，取消勾选后保存即可解除；课后或换班前点击“清空学生绑定”，核对后重新绑定。绑定保存在数据库，刷新或重启不会丢失，也不会自动判断下课；教师应课前核对、课后清空。

教师只能选择和管理自己可管理教学班的学生，不能覆盖其他教学班的绑定。全部工位画面仍可观看，其他教学班学生姓名不向无管理权限的教师显示。若整室包含其他教学班绑定，整室清空会拒绝，可逐个调整本班工位或请对应教师清空。

## 视频地址与 IP

优先填写设备或视频网关提供的完整 HTTP/HTTPS FLV、WS/WSS FLV 地址。RTSP 不能直接在浏览器里播放，需要部署视频网关转为可播放的 FLV；本系统负责转发，不转码。

只有 IP 不能确定任意型号摄像头的视频路径。如果本实验室使用同型号设备，可在 `config/application.yml` 中配置一次：

```yaml
wuxiaozhi:
  lab:
    ip-stream-template: "ws://{host}/该型号的实际播放路径.flv"
```

例如当前联调设备原项目已配置的路径为：

```yaml
wuxiaozhi:
  lab:
    ip-stream-template: "ws://{host}/ws/hdl/hlsram/live0.flv"
```

管理员填写 `188.18.31.195` 时会展开成 `ws://188.18.31.195/ws/hdl/hlsram/live0.flv`，填写 `IP:端口` 也可以。不同型号请填写完整地址，或由部署人员更换规则。模板未配置时，系统会提示填写完整视频流地址。

IP 展开后的完整地址保存在该工位的数据库配置中。修改模板只影响之后的保存操作；更换已有摄像头地址请编辑该工位并保存，避免重启覆盖管理端配置。

原来的 `/ws/` 和 `/ws-direct/` 地址继续由后端按 `wuxiaozhi.lab.camera-host` 和 `camera-direct-host` 解析。教师浏览器统一访问应用服务器的鉴权接口，不需要直连摄像头。

## 配置放在哪里

| 内容 | 保存位置 |
| --- | --- |
| 服务端口 | `config/application.yml` 的 `server.port`；开发前端为 `wuxiaozhi.frontend.port` |
| IP 播放规则、旧摄像头代理主机、数据库连接、初始化管理员与 JWT 密钥 | `config/application.yml` 或服务器环境变量 |
| 实验室、工位、每个工位的视频地址、启用状态 | 管理端维护，存数据库 |
| 当前工位学生绑定 | 教师端维护，数据库 `workstation_bindings` 表；无需额外配置文件 |

实际 `application.yml` 不提交版本库。应用从启动目录的 `config/application.yml` 读取；源码在 `backend` 目录启动时也读取 `../config/application.yml`。环境配置修改后重启后端，管理端的工位配置保存后生效。

当前摄像头已登记在“牛顿环法测定平凸透镜曲率半径实验室”的 01 号工位，保留原 IP `188.18.31.195`。是否能显示真实画面还取决于设备供电、网络路由及其实际协议；本系统不生成模拟视频填充离线画面。

## 正式部署

构建电脑安装 Node.js、Maven 和 JDK 17+，在 `platform` 目录执行：

项目编译目标固定为 Java 17。构建脚本会检查 JAR 中程序及依赖的字节码版本，防止本机 Maven 全局配置将产物改成仅支持更高 Java 版本。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-lab-release.ps1
```

日常更新按服务分别打包：只有平台改动时，运行上述平台脚本，只新增 `platform/dist/wuxiaozhi-yyyyMMddHHmm.zip`（从 platform 目录看为 `dist/wuxiaozhi-yyyyMMddHHmm.zip`），不重打采集服务；只有采集服务改动时，运行 `acquisition-service/build-release.ps1`，只新增该服务 dist 下的时间戳 ZIP。平台包名使用北京时间，依次为年月日时分，例如 `wuxiaozhi-202610230905.zip`；同一分钟已有同名包时停止并提示下一分钟重试，不覆盖已有包。均保留已有 ZIP，不留解压目录或说明文件。

仅首次完整部署或两边都需要发布时，才使用工程根目录的本地 `deployment/build-windows-release.ps1`，在根目录 dist 新增一个 `wuxiaozhi-yyyyMMddHHmm.zip`，同样使用北京时间精确到分钟，中间目录自动清理。包内保留完整程序、运行脚本、必要配置及离线依赖，不附带 README、测试报告、构建信息或校验说明文件。

解压平台包到学校服务器固定目录。服务器只需安装 JDK 17+，编辑随包的 `config/application.yml`，填写管理员初始化账号密码、随机 JWT 密钥和数据库连接，Windows 运行 `start.bat`，Linux 运行 `bash start.sh`。页面、API 和视频统一由 JAR 提供，默认访问 `http://服务器地址:8082/`。

正式包携带外部 `config/application.yml` 模板，不携带开发机数据库。Dify 配置按项目现有内容保留。首次初始化管理员后可以移除配置中的初始化密码。所有环境均使用 MySQL，按模板修改 `spring.datasource`，无需额外启用 profile。源码启动与正式 JAR 使用同一配置格式，数据库不可用时启动报错。

应用服务器需要能访问摄像头，教师终端需要能访问应用服务器。若前面使用 Nginx，视频路径 `/api/workstations/` 关闭响应缓冲、保留 Authorization 头，并设置足够长的读超时。

源码启动仍为前端 5174、后端 8082 两个服务，运行 `scripts/start.bat`，保留前端运行窗口。完整打包的正式服务与源码启动方式不同。

## 独立仪器采集服务

显微镜 UVC 采集已从教学平台拆出，源码位于同级 `acquisition-service`，运行与安装见 [采集服务说明](../../acquisition-service/README.md)。正式平台包只包含教学服务，不携带 Python 程序或设备驱动；采集服务在连接仪器的电脑上单独启动。

平台 `config/application.yml` 中配置采集服务 IP、端口、接口路径、设备编号、密钥和超时；正式包使用解压目录的同名文件，修改后重启平台后端，无需修改源码或重新打包：

```yaml
wuxiaozhi:
  acquisition:
    enabled: true
    default-device: microscope
    experiments:
      newton_rings: microscope
    devices:
      microscope:
        enabled: true
        label: 牛顿环成像相机
        base-url: http://192.168.1.100:8765
        capture-path: "/api/v1/devices/{deviceId}/captures"
        device-id: microscope-01
        api-token: "与采集服务相同的密钥"
        connect-timeout-ms: 2000
        capture-timeout-ms: 25000
```

上例请求地址为 `http://192.168.1.100:8765/api/v1/devices/microscope-01/captures`。路径必须与采集服务实际提供的接口一致；反向代理前缀可写入 `base-url`。`contracts` 只是接口格式说明，运行时不读取，部署时不用修改。

采集服务的 `config/service.json` 配置它自己的监听端口、相同密钥和实际设备成像参数。真实配置均不进入 Git，两个服务可分别升级与重启。

其他设备在 `devices` 中增加同级配置，在 `experiments` 中指定实验代码对应的配置名；未指定的实验使用 `default-device`。每台设备可配置不同的服务地址、密钥和超时。当前真实采集接口支持 JPEG 图像；新增数值和连续曲线采集时还需要对应的接口实现与数据格式，单纯增加配置不会自动接入未知仪器协议。

旧的 `lab.yml`、`mysql.env`、`ports.env`、`admin.env` 已迁入外部 `config/application.yml`，不再读取。现有配置迁移前备份在 `backups/config-before-application-*`，数据库连接、摄像头及访问密钥保持原值。新部署只填写 `application.yml`，Dify 仍使用 `dify.env`。已有文件请合并到同一个根节点，避免重复节点。

原 `UVC_CAMERA_*` 环境变量和 `wuxiaozhi.uvc-camera.*` 已停止使用。设备成像参数放在采集服务，平台的连接参数按上述设备层级配置。需要通过环境变量注入时，在设备 YAML 值中使用 `${变量名:默认值}`；外部 YAML 中填写的具体值覆盖程序内置默认值。工位巡视摄像头仍沿用管理端配置，二者互不替代。

## 升级和历史数据

更新 JAR 前备份数据库；首次从磁盘存储版本升级时也保留原 uploads。当前简化不删除学生、课次、入座或报告历史记录，也不要求转换已有工位。旧版本 MySQL 库若需新增独立实验室，先按 `config/lab-schema-migration.sql` 解除旧实验项目非空限制；新增字段由现有 `ddl-auto=update` 创建。禁止自动变更结构的部署环境应先在备份恢复的测试库审查变更。

图片、采集成像、纠错标注和资料附件统一保存在 MySQL 的 `stored_uploads` 表中（二进制 LONGBLOB），原 `/uploads/文件名` 地址继续有效。新上传不会写入或创建本地 uploads 目录，Dify 直接读取数据库中的图片上传分析。

对话框“获取图像”、选择图片和平板拍照只在浏览器保留待发送预览，不写数据库。学生点击“发送”带图问题时才上传入库并加入对话；清除、重拍、替换或离开页面时，未发送的图片不保存。采集预览接口返回 `previewDataUrl`（临时预览）而非已保存的 `url`。资料库主动上传、读数识别、巡视记录等原有明确提交操作继续按各自用途保存。

首次升级必须先在有旧 uploads 的电脑启动新版。`wuxiaozhi.upload.dir` 仅用于指定旧目录（默认 `./uploads`，相对于平台启动目录）；启动时自动导入其中的历史文件，保留原文件名和问答地址，重复启动不会重复导入或覆盖已入库数据。日志出现 `Upload database migration complete` 后核对历史图片，再备份数据库。导入失败会阻止启动，修复后重启继续导入；原文件不会自动删除。升级回旧版时，旧版仍依赖磁盘且无法读取新版入库的新图片，应使用升级前备份。

此后迁移时备份并恢复完整 MySQL 数据库（包括 stored_uploads 的表结构和数据，命令行导出建议 `mysqldump --single-transaction --hex-blob`），无需携带 uploads 文件夹。核对新电脑 `application.yml` 中的数据库地址和密码；程序与配置文件仍需正常部署。只导出问答表会遗漏图片。尚未完成旧文件入库前，仍需保留旧目录。
