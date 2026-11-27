"""本地图片无需上传到 OSS，也不放开模型服务的出站主机白名单。"""

import base64
import unittest

from pydantic import TypeAdapter, ValidationError
from app.schemas.image_input import ImageInput
from app.schemas.request import ImageOcrRequest
from app.schemas.video import GenerateImageRequest, SubmitVideoRequest


class LocalImageInputTests(unittest.TestCase):
    def test_local_data_and_remote_urls_are_supported(self):
        local = "data:image/png;base64," + base64.b64encode(b"image-fixture").decode()
        adapter = TypeAdapter(ImageInput)
        self.assertEqual(adapter.validate_python(local), local)
        self.assertEqual(adapter.validate_python("https://example.test/a.png"), "https://example.test/a.png")
        self.assertEqual(ImageOcrRequest(name="test.png", mime_type="image/png", image_url=local).image_url, local)
        request = GenerateImageRequest(api_key="test-key", model="qwen-image-edit-plus", base_url="https://dashscope.aliyuncs.com", prompt="test", reference_image_urls=[local])
        self.assertEqual(request.reference_image_urls, [local])
        video = SubmitVideoRequest(api_key="test-key", model="happyhorse-1.0-r2v", base_url="https://dashscope.aliyuncs.com",
                                   prompt="test", resolution="720P", duration_ms=5000, image_url=local,
                                   character_reference_image_urls=[local], scene_reference_image_url=local)
        self.assertEqual(video.character_reference_image_urls, [local])

    def test_local_files_html_and_invalid_base64_are_rejected(self):
        adapter = TypeAdapter(ImageInput)
        for value in ["file:///C:/private/file.png", "data:text/html;base64,PHNjcmlwdD4=", "data:image/png;base64,!!!!", "data:image/png;base64,AA=A"]:
            with self.subTest(value=value), self.assertRaises(ValidationError):
                adapter.validate_python(value)
