import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

try:
    import cv2
except Exception as exc:
    cv2 = None
    CV2_IMPORT_ERROR = str(exc)
else:
    CV2_IMPORT_ERROR = ""

BACKENDS = {
    "any": 0 if cv2 is None else cv2.CAP_ANY,
    "dshow": 0 if cv2 is None else cv2.CAP_DSHOW,
    "msmf": 0 if cv2 is None else cv2.CAP_MSMF,
}

# 笔记本内置摄像头名称特征 —— 获取成像绝不使用这些设备
BUILTIN_NAME_HINTS = (
    "integrated",
    "built-in",
    "built in",
    "内置",
    "facial",
    "ir camera",
    "windows hello",
    "lenovo camera",
    "hp hd",
    "realtek",
    "fhd camera",
    "720p",
    "笔记本",
    "integrated camera",
)


class CaptureConfig:
    def __init__(self, args):
        self.backend = args.backend
        self.width = args.width
        self.height = args.height
        self.fps = args.fps
        self.warmup = args.warmup
        self.name_keyword = (args.name_keyword or "UVC").strip()


def int_param(params, name, default):
    values = params.get(name)
    if not values:
        return default
    try:
        return int(values[0])
    except ValueError:
        return default


def str_param(params, name, default):
    values = params.get(name)
    return values[0].strip() if values and values[0].strip() else default


def json_response(handler, status, payload):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


_camera_names_cache = None


def dshow_camera_names():
    """DirectShow 视频输入设备名，顺序与 OpenCV CAP_DSHOW 的 index 一致。"""
    global _camera_names_cache
    if _camera_names_cache is not None:
        return _camera_names_cache

    com_initialized = False
    try:
        import pythoncom

        pythoncom.CoInitialize()
        com_initialized = True
    except Exception:
        pass

    try:
        from pygrabber.dshow_graph import FilterGraph

        names = [name.strip() for name in FilterGraph().get_input_devices() if str(name).strip()]
        if not names:
            raise RuntimeError("未检测到任何 DirectShow 视频输入设备")
        _camera_names_cache = names
        return names
    except RuntimeError:
        raise
    except Exception as exc:
        raise RuntimeError(
            "无法枚举摄像头设备，请安装 pygrabber：pip install -r backend/scripts/requirements-uvc.txt"
        ) from exc
    finally:
        if com_initialized:
            try:
                import pythoncom

                pythoncom.CoUninitialize()
            except Exception:
                pass


def refresh_camera_names():
    global _camera_names_cache
    _camera_names_cache = None
    return dshow_camera_names()


def is_builtin_camera(name):
    lower = (name or "").lower()
    return any(hint in lower for hint in BUILTIN_NAME_HINTS)


def find_microscope(config, params):
    """
    严格模式：只认电子显微镜（设备名包含关键字，如 UVC Camera）。
    没有显微镜就报错，绝不回退到内置/FHD 摄像头。
    """
    keyword = str_param(params, "name_keyword", config.name_keyword) or "UVC"
    keyword_lower = keyword.lower()
    names = dshow_camera_names()

    for index, name in enumerate(names):
        if is_builtin_camera(name):
            continue
        if keyword_lower in name.lower():
            return index, name

    detected = "、".join(names)
    raise RuntimeError(
        f"未检测到电子显微镜（设备名需包含「{keyword}」）。"
        f"当前仅有：{detected}。"
        f"请连接显微镜 USB，并关闭占用相机的其他软件。"
    )


def describe_cameras(config, params):
    keyword = str_param(params, "name_keyword", config.name_keyword) or "UVC"
    keyword_lower = keyword.lower()
    names = dshow_camera_names()
    cameras = []
    microscope_index = None
    microscope_name = None
    for index, name in enumerate(names):
        builtin = is_builtin_camera(name)
        is_microscope = (not builtin) and keyword_lower in name.lower()
        if is_microscope:
            microscope_index = index
            microscope_name = name
        cameras.append(
            {
                "index": index,
                "name": name,
                "builtin": builtin,
                "microscope": is_microscope,
            }
        )
    return {
        "cameras": cameras,
        "microscopeIndex": microscope_index,
        "microscopeName": microscope_name,
        "nameKeyword": keyword,
        "microscopeAvailable": microscope_index is not None,
    }


def capture_frame(config, params):
    if cv2 is None:
        raise RuntimeError(f"OpenCV 不可用：{CV2_IMPORT_ERROR or '未安装 cv2'}")

    index, camera_name = find_microscope(config, params)

    width = int_param(params, "width", config.width)
    height = int_param(params, "height", config.height)
    fps = int_param(params, "fps", config.fps)
    backend_name = str_param(params, "backend", config.backend)
    backend = BACKENDS.get(backend_name, BACKENDS["dshow"])

    cap = cv2.VideoCapture(index, backend)
    if not cap.isOpened():
        raise RuntimeError(
            f"电子显微镜「{camera_name}」无法打开，可能被其他软件占用（index={index}）"
        )

    try:
        if width:
            cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        if height:
            cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
        if fps:
            cap.set(cv2.CAP_PROP_FPS, fps)

        ok = False
        frame = None
        for _ in range(max(1, config.warmup)):
            ok, frame = cap.read()
            if ok and frame is not None and frame.size:
                break
            time.sleep(0.08)

        if not ok or frame is None or not frame.size:
            raise RuntimeError(f"电子显微镜「{camera_name}」已连接，但未获取到有效图像")

        ok, encoded = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 92])
        if not ok:
            raise RuntimeError("图像编码失败")
        print(
            f"[uvc] microscope index={index} name={camera_name} size={frame.shape[1]}x{frame.shape[0]}",
            flush=True,
        )
        return encoded.tobytes(), frame.shape[1], frame.shape[0], index, camera_name
    finally:
        cap.release()


class UvcHandler(BaseHTTPRequestHandler):
    config = None

    def log_message(self, fmt, *args):
        print("[uvc]", fmt % args, flush=True)

    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        if parsed.path == "/health":
            info = describe_cameras(self.config, params)
            json_response(
                self,
                200,
                {
                    "ok": True,
                    "opencv": cv2 is not None,
                    "microscopeAvailable": info["microscopeAvailable"],
                    "microscopeName": info["microscopeName"],
                },
            )
            return

        if parsed.path == "/cameras":
            try:
                json_response(self, 200, describe_cameras(self.config, params))
            except Exception as exc:
                json_response(self, 503, {"message": str(exc)})
            return

        if parsed.path not in ("/capture", "/capture.jpg"):
            json_response(self, 404, {"message": "not found"})
            return

        try:
            image, width, height, index, camera_name = capture_frame(self.config, params)
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(image)))
            self.send_header("X-Frame-Width", str(width))
            self.send_header("X-Frame-Height", str(height))
            self.send_header("X-Camera-Index", str(index))
            self.send_header("X-Camera-Name", camera_name)
            self.end_headers()
            self.wfile.write(image)
        except Exception as exc:
            json_response(self, 503, {"message": str(exc)})


def main():
    parser = argparse.ArgumentParser(description="电子显微镜 UVC 采集服务（仅显微镜，无内置摄像头回退）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--backend", choices=["any", "dshow", "msmf"], default="dshow")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument("--warmup", type=int, default=5)
    parser.add_argument(
        "--name-keyword",
        default="UVC",
        help="电子显微镜在系统中的设备名关键字（默认 UVC，对应 UVC Camera）",
    )
    args = parser.parse_args()

    UvcHandler.config = CaptureConfig(args)
    try:
        names = dshow_camera_names()
        print(f"[uvc] video inputs: {names}", flush=True)
    except Exception as exc:
        raise SystemExit(f"UVC 服务启动失败：{exc}") from exc

    server = ThreadingHTTPServer((args.host, args.port), UvcHandler)
    print(
        f"UVC microscope capture listening on {args.host}:{args.port} "
        f"(keyword={args.name_keyword}, microscope-only)",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
