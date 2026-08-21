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


class CaptureConfig:
    def __init__(self, args):
        self.index = args.index
        self.backend = args.backend
        self.width = args.width
        self.height = args.height
        self.fps = args.fps
        self.warmup = args.warmup


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
    return values[0].strip().lower() if values and values[0].strip() else default


def json_response(handler, status, payload):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def capture_frame(config, params):
    if cv2 is None:
        raise RuntimeError(f"OpenCV 不可用：{CV2_IMPORT_ERROR or '未安装 cv2'}")

    index = int_param(params, "index", config.index)
    width = int_param(params, "width", config.width)
    height = int_param(params, "height", config.height)
    fps = int_param(params, "fps", config.fps)
    backend_name = str_param(params, "backend", config.backend)
    backend = BACKENDS.get(backend_name, BACKENDS[config.backend])

    cap = cv2.VideoCapture(index, backend)
    if not cap.isOpened():
        raise RuntimeError(f"未检测到 UVC 相机或相机被占用（index={index}, backend={backend_name}）")

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
            raise RuntimeError("UVC 相机已打开，但没有获取到有效图像")

        ok, encoded = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 92])
        if not ok:
            raise RuntimeError("图像编码失败")
        return encoded.tobytes(), frame.shape[1], frame.shape[0]
    finally:
        cap.release()


class UvcHandler(BaseHTTPRequestHandler):
    config = None

    def log_message(self, fmt, *args):
        print("[uvc]", fmt % args, flush=True)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            json_response(self, 200, {"ok": True, "opencv": cv2 is not None})
            return
        if parsed.path not in ("/capture", "/capture.jpg"):
            json_response(self, 404, {"message": "not found"})
            return

        try:
            params = parse_qs(parsed.query)
            image, width, height = capture_frame(self.config, params)
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(image)))
            self.send_header("X-Frame-Width", str(width))
            self.send_header("X-Frame-Height", str(height))
            self.end_headers()
            self.wfile.write(image)
        except Exception as exc:
            json_response(self, 503, {"message": str(exc)})


def main():
    parser = argparse.ArgumentParser(description="Local UVC capture helper for Wuxiaozhi.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--index", type=int, default=1)
    parser.add_argument("--backend", choices=["any", "dshow", "msmf"], default="dshow")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument("--warmup", type=int, default=15)
    args = parser.parse_args()

    UvcHandler.config = CaptureConfig(args)
    server = ThreadingHTTPServer((args.host, args.port), UvcHandler)
    print(f"UVC capture helper listening on {args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
