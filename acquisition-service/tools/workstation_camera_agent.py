"""Publish a fixed workstation USB camera independently of the browser.

pip install opencv-python
Set WXZ_SERVER, WXZ_STATION_ID and WXZ_STATION_KEY in the environment.
python workstation_camera_agent.py --device-index 0
Explicit device selection avoids silently selecting the CCD camera.
"""
import argparse
import os
import signal
import time
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device-index', type=int, required=True)
    parser.add_argument('--fps', type=float, default=2.0)
    args = parser.parse_args()
    server = os.environ.get('WXZ_SERVER', '').rstrip('/')
    station = os.environ.get('WXZ_STATION_ID', '')
    key = os.environ.get('WXZ_STATION_KEY', '')
    if not server.startswith(('http://', 'https://')) or not station.isdigit() or not key:
        parser.error('Set WXZ_SERVER, WXZ_STATION_ID and WXZ_STATION_KEY first.')
    if args.device_index < 0 or not 0.2 <= args.fps <= 5:
        parser.error('Use device-index >= 0 and fps between 0.2 and 5.')
    import cv2
    running = True

    def stop(*_):
        nonlocal running
        running = False

    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    endpoint = f'{server}/api/workstation-agent/{station}/frame'
    capture = None
    failed = False
    try:
        while running:
            tick = time.monotonic()
            if capture is None or not capture.isOpened():
                if capture is not None:
                    capture.release()
                capture = cv2.VideoCapture(args.device_index)
                capture.set(cv2.CAP_PROP_FRAME_WIDTH, 960)
                capture.set(cv2.CAP_PROP_FRAME_HEIGHT, 540)
            ok, frame = capture.read()
            if not ok:
                print('Camera unavailable; reconnecting in 3 seconds.', flush=True)
                capture.release()
                capture = None
                time.sleep(3)
                continue
            height, width = frame.shape[:2]
            scale = min(1, 960 / width, 720 / height)
            if scale < 1:
                frame = cv2.resize(frame, (int(width * scale), int(height * scale)))
            ok, encoded = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
            if not ok:
                continue
            request = urllib.request.Request(endpoint, data=encoded.tobytes(), headers={
                'Content-Type': 'image/jpeg', 'X-Station-Key': key
            }, method='POST')
            try:
                with urllib.request.urlopen(request, timeout=5) as response:
                    response.read(1024)
                if failed:
                    print('Publisher reconnected.', flush=True)
                failed = False
            except urllib.error.HTTPError as exc:
                if exc.code in (401, 403, 404):
                    print(f'Publisher refused ({exc.code}); check station ID and key.', flush=True)
                    return 2
                if not failed:
                    print(f'Publish failed ({exc.code}); retrying.', flush=True)
                failed = True
            except (urllib.error.URLError, TimeoutError, OSError):
                if not failed:
                    print('Server unavailable; retrying.', flush=True)
                failed = True
            time.sleep(max(0, (3 if failed else 1 / args.fps) - (time.monotonic() - tick)))
    finally:
        if capture is not None:
            capture.release()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
