import argparse
import base64
from collections import OrderedDict
from datetime import datetime, timezone
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import threading
import time
from urllib.parse import unquote, urlparse
from uuid import UUID


SERVICE_ROOT = Path(__file__).resolve().parents[2]
DEVICE_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
CAPTURE_PATH = re.compile(r"^/api/v1/devices/([A-Za-z0-9_-]{1,64})/captures$")
MAX_IMAGE_BYTES = 12 * 1024 * 1024


class ApiError(Exception):
    def __init__(self, status, code, message):
        super().__init__(message)
        self.status, self.code = status, code


def load_config(path):
    config = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if not isinstance(config, dict):
        raise ValueError("配置必须是 JSON 对象")
    config["apiToken"] = os.environ.get("ACQUISITION_API_TOKEN", config.get("apiToken", ""))
    if not isinstance(config["apiToken"], str):
        raise ValueError("apiToken 必须是字符串")
    host = config.get("host", "127.0.0.1")
    if host not in ("127.0.0.1", "localhost") and not config["apiToken"]:
        raise ValueError("对局域网开放前必须设置 apiToken 或 ACQUISITION_API_TOKEN")
    if not isinstance(config.get("port", 8765), int) or not 1 <= config.get("port", 8765) <= 65535:
        raise ValueError("port 必须为 1–65535")
    ids = set()
    for device in config.get("devices", []):
        device_id = device.get("id", "")
        if not DEVICE_ID.fullmatch(device_id) or device_id in ids:
            raise ValueError("设备 id 必须唯一，且只包含字母、数字、下划线或连字符")
        if device.get("adapter") != "uvc":
            raise ValueError("当前 Python 服务仅安装了 uvc 适配器")
        if not isinstance(device.get("name"), str) or not device["name"].strip():
            raise ValueError("设备 name 不能为空")
        if device.get("backend", "dshow") not in ("dshow", "msmf", "any"):
            raise ValueError("设备 backend 必须为 dshow、msmf 或 any")
        if not isinstance(device.get("nameKeyword", "UVC"), str) or not device.get("nameKeyword", "UVC").strip():
            raise ValueError("设备 nameKeyword 不能为空")
        for key, default, maximum in (("width", 1920, 8192), ("height", 1080, 8192),
                                      ("fps", 30, 120), ("warmup", 5, 60)):
            value = device.get(key, default)
            if type(value) is not int or not 1 <= value <= maximum:
                raise ValueError(f"设备 {key} 必须为 1–{maximum} 的整数")
        ids.add(device_id)
    return config


class AcquisitionService:
    def __init__(self, config, adapters=None):
        self.config = config
        self.devices = {device["id"]: device for device in config.get("devices", [])}
        if adapters is None:
            from acquisition.adapters.uvc import UvcAdapter
            adapters = {key: UvcAdapter(device) for key, device in self.devices.items()}
        self.adapters = adapters
        # UVC drivers can share a USB bus; avoid opening devices concurrently.
        self.capture_lock = threading.Lock()
        self.cache_lock = threading.Lock()
        self.results = OrderedDict()

    def list_devices(self):
        return [dict(id=key, name=device["name"], capabilities=["image"],
                     **self.adapters[key].status()) for key, device in self.devices.items()]

    def capture(self, device_id, request_id):
        if device_id not in self.devices:
            raise ApiError(404, "DEVICE_NOT_FOUND", "未配置该设备")
        try:
            UUID(request_id)
        except (ValueError, TypeError, AttributeError):
            raise ApiError(400, "INVALID_REQUEST", "requestId 必须是 UUID")
        with self.cache_lock:
            now = time.monotonic()
            for key in list(self.results):
                if self.results[key][0] <= now:
                    del self.results[key]
            cached = self.results.get(request_id)
            if cached:
                if cached[1]["deviceId"] != device_id:
                    raise ApiError(409, "REQUEST_CONFLICT", "此 requestId 已用于其他设备")
                return cached[1]
        if not self.capture_lock.acquire(blocking=False):
            raise ApiError(409, "DEVICE_BUSY", "设备正在采集，请稍后重试")
        try:
            # Another request may have completed after the first cache lookup.
            with self.cache_lock:
                cached = self.results.get(request_id)
                if cached:
                    if cached[1]["deviceId"] != device_id:
                        raise ApiError(409, "REQUEST_CONFLICT", "此 requestId 已用于其他设备")
                    return cached[1]
            try:
                frame = self.adapters[device_id].capture()
            except Exception as exc:
                raise ApiError(503, "DEVICE_UNAVAILABLE", str(exc)) from exc
            image = frame["image"]
            if not image or len(image) > MAX_IMAGE_BYTES:
                raise ApiError(503, "INVALID_IMAGE", "设备返回空图像或图像超过 12MB")
            result = dict(apiVersion="1", requestId=request_id, deviceId=device_id,
                          capturedAt=datetime.now(timezone.utc).isoformat(), kind="image",
                          contentType="image/jpeg", dataBase64=base64.b64encode(image).decode("ascii"),
                          width=frame["width"], height=frame["height"],
                          deviceName=frame["name"])
            with self.cache_lock:
                self.results[request_id] = (time.monotonic() + 60, result)
                while len(self.results) > 8:
                    self.results.popitem(last=False)
            return result
        finally:
            self.capture_lock.release()


class AcquisitionHandler(BaseHTTPRequestHandler):
    # Keep HTTP/1.0 connection handling: no unread request body can leak into the next request.
    def reply(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def authorize(self):
        token = self.server.service.config.get("apiToken", "")
        if token and not hmac.compare_digest(self.headers.get("Authorization", "").encode(),
                                             ("Bearer " + token).encode()):
            raise ApiError(401, "UNAUTHORIZED", "采集服务访问密钥无效")

    def dispatch(self):
        path = unquote(urlparse(self.path).path)
        if self.command == "GET" and path == "/api/v1/health":
            return dict(ok=True, service="wuxiaozhi-acquisition", apiVersion="1")
        self.authorize()
        if self.command == "GET" and path == "/api/v1/devices":
            return dict(apiVersion="1", devices=self.server.service.list_devices())
        capture = CAPTURE_PATH.fullmatch(path)
        if self.command == "POST" and capture:
            if self.headers.get("Transfer-Encoding"):
                raise ApiError(400, "INVALID_REQUEST", "仅支持 Content-Length 请求体")
            if self.headers.get_content_type() != "application/json":
                raise ApiError(415, "INVALID_REQUEST", "请求必须为 application/json")
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if size <= 0 or size > 4096:
                    raise ValueError()
                data = json.loads(self.rfile.read(size))
                if not isinstance(data, dict):
                    raise ValueError()
            except (ValueError, UnicodeDecodeError):
                raise ApiError(400, "INVALID_REQUEST", "请求体无效或超过 4096 字节")
            return self.server.service.capture(capture[1], data.get("requestId"))
        raise ApiError(404, "NOT_FOUND", "接口不存在")

    def handle_api(self):
        self.connection.settimeout(30)
        try:
            self.reply(200, self.dispatch())
        except ApiError as exc:
            self.reply(exc.status, dict(code=exc.code, message=str(exc)))
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        except Exception:
            self.reply(500, dict(code="INTERNAL_ERROR", message="采集服务内部错误，请查看服务日志"))

    do_GET = handle_api
    do_POST = handle_api


def create_server(config, adapters=None):
    server = ThreadingHTTPServer((config.get("host", "127.0.0.1"), config.get("port", 8765)), AcquisitionHandler)
    server.service = AcquisitionService(config, adapters)
    return server


def main():
    parser = argparse.ArgumentParser(description="物小智独立设备采集服务")
    parser.add_argument("--config", default=str(SERVICE_ROOT / "config/service.json"))
    args = parser.parse_args()
    path = Path(args.config)
    if not path.is_absolute():
        path = SERVICE_ROOT / path
    if not path.exists():
        raise SystemExit("请将 config/service.json.example 复制为 config/service.json 后配置设备")
    server = create_server(load_config(path))
    print(f"Acquisition API v1 listening on {server.server_address[0]}:{server.server_address[1]}", flush=True)
    print("设备离线不会关闭服务；设备状态见 GET /api/v1/devices", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
