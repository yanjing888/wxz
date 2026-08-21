import argparse
import os
import time

import cv2


BACKENDS = {
    "any": cv2.CAP_ANY,
    "dshow": cv2.CAP_DSHOW,
    "msmf": cv2.CAP_MSMF,
}


def imwrite_unicode(path, image):
    ext = os.path.splitext(path)[1] or ".jpg"
    ok, buf = cv2.imencode(ext, image)
    if not ok:
        raise RuntimeError(f"Failed to encode image as {ext}")
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    buf.tofile(path)


def open_camera(index, backend, width, height, fps):
    cap = cv2.VideoCapture(index, backend)
    if not cap.isOpened():
        return None
    if width:
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
    if height:
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)
    if fps:
        cap.set(cv2.CAP_PROP_FPS, fps)
    return cap


def list_cameras(args):
    for backend_name, backend in BACKENDS.items():
        if args.backend != "all" and args.backend != backend_name:
            continue
        print(f"[{backend_name}]")
        for index in range(args.max_index + 1):
            cap = open_camera(index, backend, args.width, args.height, args.fps)
            if cap is None:
                print(f"  {index}: not opened")
                continue

            ok = False
            frame = None
            for _ in range(10):
                ok, frame = cap.read()
                if ok and frame is not None and frame.size:
                    break
                time.sleep(0.1)

            w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            real_fps = cap.get(cv2.CAP_PROP_FPS)
            print(f"  {index}: opened frame={ok} reported={w}x{h}@{real_fps:g}")
            if args.save_samples and ok:
                path = os.path.join(args.output_dir, f"sample_{backend_name}_{index}.jpg")
                imwrite_unicode(path, frame)
                print(f"      saved {path}")
            cap.release()


def preview_camera(args):
    backend = BACKENDS[args.backend]
    cap = open_camera(args.index, backend, args.width, args.height, args.fps)
    if cap is None:
        raise RuntimeError(f"Cannot open camera index {args.index} with backend {args.backend}")

    print("Press s to save, q or Esc to quit.")
    count = 0
    try:
        while True:
            ok, frame = cap.read()
            if not ok or frame is None:
                print("No frame received.")
                time.sleep(0.1)
                continue

            cv2.imshow(args.window_name, frame)
            key = cv2.waitKey(1) & 0xFF
            if key in (27, ord("q")):
                break
            if key == ord("s"):
                count += 1
                path = os.path.join(args.output_dir, f"uvc_frame_{count:04d}.jpg")
                imwrite_unicode(path, frame)
                print(f"saved {path}")
    finally:
        cap.release()
        cv2.destroyAllWindows()


def main():
    parser = argparse.ArgumentParser(description="Generic UVC camera preview/capture tool.")
    parser.add_argument("--index", type=int, default=1, help="Camera index. The Newton-rings UVC camera tested as index 1 on this PC.")
    parser.add_argument("--backend", choices=["any", "dshow", "msmf", "all"], default="dshow")
    parser.add_argument("--width", type=int, default=1920)
    parser.add_argument("--height", type=int, default=1080)
    parser.add_argument("--fps", type=int, default=30)
    parser.add_argument("--output-dir", default=r"C:\Temp\codex_uvc_frames")
    parser.add_argument("--window-name", default="UVC Camera")
    parser.add_argument("--list", action="store_true", help="Try camera indexes instead of opening preview.")
    parser.add_argument("--max-index", type=int, default=5)
    parser.add_argument("--save-samples", action="store_true", help="Save one image per opened camera while listing.")
    args = parser.parse_args()

    if args.list:
        list_cameras(args)
    else:
        if args.backend == "all":
            args.backend = "dshow"
        preview_camera(args)


if __name__ == "__main__":
    main()
