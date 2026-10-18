import base64
import http.client
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from unittest.mock import patch
from uuid import uuid4

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from acquisition.server import create_server, load_config


class FakeAdapter:
    def __init__(self):
        self.calls = 0
        self.failure = None

    def status(self):
        return dict(available=False, message="设备未连接")

    def capture(self):
        self.calls += 1
        if self.failure:
            raise RuntimeError(self.failure)
        return dict(image=b"\xff\xd8test\xff\xd9", width=1, height=1, name="测试显微镜")


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.adapter = FakeAdapter()
        config = dict(host="127.0.0.1", port=0, apiToken="test-secret", devices=[
            dict(id="microscope-01", name="显微镜", adapter="uvc"),
            dict(id="microscope-02", name="显微镜二", adapter="uvc")])
        self.server = create_server(config, {"microscope-01": self.adapter, "microscope-02": self.adapter})
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, method="GET", path="/api/v1/devices", data=None, token="test-secret", headers=None):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=3)
        options = {"Content-Type": "application/json", "Authorization": "Bearer " + token}
        options.update(headers or {})
        connection.request(method, path, None if data is None else json.dumps(data), options)
        response = connection.getresponse()
        result = response.status, json.loads(response.read())
        connection.close()
        return result

    def capture(self, request_id=None, device="microscope-01"):
        return self.request("POST", f"/api/v1/devices/{device}/captures",
                            {"requestId": request_id or str(uuid4())})

    def test_health_stays_ready_without_hardware(self):
        status, body = self.request(path="/api/v1/health", token="")
        self.assertEqual((status, body["ok"], body["apiVersion"]), (200, True, "1"))
        status, body = self.request()
        self.assertEqual(status, 200)
        self.assertFalse(body["devices"][0]["available"])

    def test_authentication_protects_devices_and_capture(self):
        self.assertEqual(self.request(token="wrong")[0], 401)
        self.assertEqual(self.request("POST", "/api/v1/devices/microscope-01/captures",
                                     {"requestId": str(uuid4())}, token="")[0], 401)
        self.assertEqual(self.adapter.calls, 0)

    def test_capture_preserves_unicode_and_identity(self):
        request_id = str(uuid4())
        status, body = self.capture(request_id)
        self.assertEqual(status, 200)
        self.assertEqual(body["requestId"], request_id)
        self.assertEqual(body["deviceName"], "测试显微镜")
        self.assertEqual(base64.b64decode(body["dataBase64"]), b"\xff\xd8test\xff\xd9")
        self.assertIn("+00:00", body["capturedAt"])

    def test_same_request_returns_same_frame_without_recapture(self):
        request_id = str(uuid4())
        first = self.capture(request_id)
        self.assertEqual(first, self.capture(request_id))
        self.assertEqual(self.adapter.calls, 1)
        self.assertEqual(self.capture(request_id, "microscope-02")[0], 409)

    def test_offline_device_returns_failure_without_fake_frame(self):
        self.adapter.failure = "显微镜已拔出"
        status, body = self.capture()
        self.assertEqual(status, 503)
        self.assertEqual(body["code"], "DEVICE_UNAVAILABLE")
        self.assertNotIn("dataBase64", body)

    def test_unknown_device_is_not_auto_selected(self):
        self.assertEqual(self.capture(device="missing")[0], 404)
        self.assertEqual(self.adapter.calls, 0)

    def test_invalid_request_and_media_type(self):
        self.assertEqual(self.capture("bad-request-id")[0], 400)
        path = "/api/v1/devices/microscope-01/captures"
        self.assertEqual(self.request("POST", path, ["not-an-object"])[0], 400)
        self.assertEqual(self.request("POST", path, {"requestId": str(uuid4())},
                                     headers={"Content-Type": "text/plain"})[0], 415)

    def test_device_lock_rejects_concurrent_capture(self):
        self.server.service.capture_lock.acquire()
        try:
            self.assertEqual(self.capture()[0], 409)
            self.assertEqual(self.adapter.calls, 0)
        finally:
            self.server.service.capture_lock.release()

    def test_lan_bind_requires_token(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {"ACQUISITION_API_TOKEN": ""}):
            path = Path(folder) / "service.json"
            path.write_text(json.dumps(dict(host="0.0.0.0", port=8765, devices=[])))
            with self.assertRaisesRegex(ValueError, "apiToken"):
                load_config(path)
            with patch.dict(os.environ, {"ACQUISITION_API_TOKEN": "configured"}):
                self.assertEqual(load_config(path)["apiToken"], "configured")

    def test_invalid_device_configuration_fails_before_listening(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "service.json"
            device = dict(id="microscope-01", name="显微镜", adapter="uvc", fps=0)
            path.write_text(json.dumps(dict(host="127.0.0.1", devices=[device])))
            with self.assertRaisesRegex(ValueError, "fps"):
                load_config(path)


if __name__ == "__main__":
    unittest.main()
