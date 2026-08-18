# 物小智 Honor 平板 APK 打包说明

## 1. 后端先放到平板能访问的位置

APK 里只打包前端应用壳，Spring Boot 后端需要运行在服务器、实验室电脑或同一局域网主机上。

示例后端地址：

```env
http://192.168.1.100:8082
```

平板浏览器能打开 `http://192.168.1.100:8082/api/system/dify-status`，APK 才能登录和使用服务。

## 2. 配置 APK 使用的后端地址

复制示例文件：

```powershell
cd frontend
Copy-Item .env.production.example .env.production
```

编辑 `frontend/.env.production`：

```env
VITE_API_BASE_URL=http://你的后端IP:8082
```

## 3. 生成 Android 工程

第一次打包时执行：

```powershell
cd frontend
npm run build
npx cap add android
npm run android:sync
```

以后前端改完，只需要：

```powershell
cd frontend
npm run android:sync
```

## 4. 在 Android Studio 里出 APK

如果命令行提示 `SDK location not found`，先安装 Android Studio，打开后按向导安装 Android SDK。默认路径通常是：

```text
C:\Users\你的用户名\AppData\Local\Android\Sdk
```

Android Studio 配好 SDK 后，可以回到命令行直接构建 debug APK：

```powershell
cd frontend
npm run android:debug
```

也可以打开 Android Studio：

```powershell
cd frontend
npm run android:open
```

Android Studio 打开后：

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

调试包通常在：

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

## 5. 放到 Honor 平板

用数据线或 ADB 安装：

```powershell
adb install -r frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

## 6. 霸屏模式

普通 APK 只能做到全屏沉浸，不能彻底禁止 Home、返回、多任务。

推荐三档：

```text
演示档：安装 APK，打开后全屏使用。
课堂档：平板开启“应用固定/屏幕固定”，学生只能停留在物小智。
正式档：把物小智设为设备所有者 Device Owner 或用 MDM，开启 Kiosk/Lock Task 模式。
```

如果要做到“屏幕一亮就是 Logo，点击进入登录”，需要继续做 Android 原生层：

```text
1. 设置应用启动页/Logo。
2. 配置沉浸式全屏和保持屏幕常亮。
3. 设备所有者模式下启用 Lock Task。
4. 可选：把应用设为默认 Launcher。
```
