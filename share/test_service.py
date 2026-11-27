import base64
import io
import json
import os
import tempfile
import unittest
from unittest.mock import patch

from service import application


class ShareLifecycle(unittest.TestCase):
    def test_publish_choose_change_and_revoke(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(
            os.environ, EDCAM_DB=tmp + "/db", EDCAM_UPLOAD_KEY="device-key"
        ):

            def call(method, path, token="", payload=None, **headers):
                body = json.dumps(payload).encode() if payload else b""
                status = []
                result = application(
                    dict(
                        REQUEST_METHOD=method,
                        PATH_INFO=path,
                        HTTP_AUTHORIZATION="Bearer " + token,
                        CONTENT_LENGTH=str(len(body)),
                        **{"wsgi.input": io.BytesIO(body)},
                        **headers
                    ),
                    lambda s, h: status.append(int(s.split()[0])),
                )
                return status[0], result[0]

            images = [
                {
                    "label": str(i),
                    "jpeg": base64.b64encode(b"\xff\xd8\xff\xe0\xff\xd9").decode(),
                }
                for i in range(3)
            ]
            self.assertEqual(
                call("POST", "/shares", payload={"images": images})[0], 403
            )
            code, raw = call("POST", "/shares", "device-key", {"images": images})
            self.assertEqual(code, 201)
            created = json.loads(raw)
            url = "/s/" + created["id"]
            self.assertIn(b"Ed-Cam", call("GET", url)[1])
            self.assertEqual(
                len(json.loads(call("GET", url + "/info")[1])["labels"]), 3
            )
            self.assertEqual(call("GET", url + "/0")[0], 200)
            for index in ("2", "1"):
                self.assertEqual(
                    call(
                        "POST",
                        url + "/vote",
                        HTTP_X_EDCAM_VOTER="a" * 32,
                        HTTP_X_EDCAM_CHOICE=index,
                    )[0],
                    200,
                )
            self.assertEqual(
                json.loads(call("GET", url + "/votes", created["owner"])[1])["results"],
                [["0", 0], ["1", 1], ["2", 0]],
            )
            self.assertEqual(call("GET", url + "/votes")[0], 403)
            self.assertEqual(
                call(
                    "POST",
                    url + "/vote",
                    HTTP_X_EDCAM_VOTER="a" * 32,
                    HTTP_X_EDCAM_CHOICE="9",
                )[0],
                400,
            )
            self.assertEqual(
                call("POST", "/shares", "device-key", {"images": []})[0], 400
            )
            self.assertEqual(call("DELETE", url)[0], 403)
            self.assertEqual(call("DELETE", url, created["owner"])[0], 200)
            self.assertEqual(call("GET", url + "/0")[0], 404)
            with patch("service.time.time", return_value=1000):
                second = json.loads(
                    call("POST", "/shares", "device-key", {"images": images[:1]})[1]
                )
            with patch("service.time.time", return_value=1000 + 7 * 86400):
                expired = "/s/" + second["id"]
                self.assertEqual(call("GET", expired)[0], 404)
                self.assertEqual(call("GET", expired + "/0")[0], 404)


if __name__ == "__main__":
    unittest.main()
