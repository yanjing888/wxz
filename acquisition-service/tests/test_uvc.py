from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from acquisition.adapters import uvc


class UvcTest(unittest.TestCase):
    def test_every_enumeration_initializes_and_releases_com(self):
        com = Mock()
        graph = Mock()
        graph.get_input_devices.return_value = ["UVC Camera"]
        module = SimpleNamespace(FilterGraph=Mock(return_value=graph))
        with patch.object(uvc, "comtypes", com), patch.dict(sys.modules, {"pygrabber.dshow_graph": module}):
            self.assertEqual(uvc.dshow_camera_names(), ["UVC Camera"])
            self.assertEqual(uvc.dshow_camera_names(), ["UVC Camera"])
        self.assertEqual(com.CoInitialize.call_count, 2)
        self.assertEqual(com.CoUninitialize.call_count, 2)

    def test_enumeration_failure_still_releases_com(self):
        com = Mock()
        module = SimpleNamespace(FilterGraph=Mock(side_effect=ValueError("driver error")))
        with patch.object(uvc, "comtypes", com), patch.dict(sys.modules, {"pygrabber.dshow_graph": module}):
            with self.assertRaises(RuntimeError):
                uvc.dshow_camera_names()
        com.CoUninitialize.assert_called_once()

    def test_missing_microscope_never_falls_back_to_builtin_camera(self):
        config = SimpleNamespace(name_keyword="UVC")
        with patch.object(uvc, "dshow_camera_names", return_value=["FHD Camera", "Integrated UVC Camera"]):
            with self.assertRaises(RuntimeError):
                uvc.find_microscope(config, {})


if __name__ == "__main__":
    unittest.main()
