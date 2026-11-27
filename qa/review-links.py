"""Integration test. Install ShareInstrumentation and point ANDROID_HOME to the SDK.
Uses cryptography, Pillow, Playwright and a local Chromium/Edge browser."""

import base64
import datetime
import io
import json
import os
from pathlib import Path
import secrets
import ssl
import subprocess
import sys
import tempfile
import threading
import urllib.request
from wsgiref.simple_server import make_server

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "share"))
from service import application


def review(temp):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "localhost")])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(subject)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=7))
        .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost")]), False)
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), True)
        .sign(key, hashes.SHA256())
    )
    pem = cert.public_bytes(serialization.Encoding.PEM)
    (temp / "cert.pem").write_bytes(pem)
    (temp / "key.pem").write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    password = secrets.token_urlsafe(24)
    os.environ.update(EDCAM_UPLOAD_KEY=password, EDCAM_DB=str(temp / "db"))
    server = make_server("127.0.0.1", 0, application)
    port = server.server_port
    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(temp / "cert.pem", temp / "key.pem")
    server.socket = tls.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    serial = os.environ.get("ANDROID_SERIAL", "emulator-5554")
    assert serial.startswith("emulator-")
    adb = [
        str(
            Path(os.environ["ANDROID_HOME"])
            / "platform-tools"
            / ("adb.exe" if os.name == "nt" else "adb")
        ),
        "-s",
        serial,
    ]
    subprocess.run(adb + ["reverse", f"tcp:{port}", f"tcp:{port}"], check=True)
    try:
        result = subprocess.check_output(
            adb
            + [
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "cert",
                base64.b64encode(
                    cert.public_bytes(serialization.Encoding.DER)
                ).decode(),
                "-e",
                "port",
                str(port),
                "-e",
                "key",
                password,
                "com.edcam.test/com.edcam.ShareInstrumentation",
            ],
            text=True,
        )
        assert "PASS Android HTTPS" in result, result
        print(result, flush=True)
        base = f"https://localhost:{port}"
        context = ssl.create_default_context(cadata=pem.decode())

        def request(path, method, token, body=None):
            req = urllib.request.Request(
                base + path,
                data=json.dumps(body).encode() if body else None,
                method=method,
                headers={"Authorization": "Bearer " + token},
            )
            with urllib.request.urlopen(req, context=context) as response:
                return json.load(response)

        images = []
        for label, color in [
            ("Nguyên bản", "#d18496"),
            ("Nắng 35", "#dbad66"),
            ("Mono", "#999999"),
        ]:
            out = io.BytesIO()
            Image.new("RGB", (160, 120), color).save(out, "JPEG")
            images.append(
                {"label": label, "jpeg": base64.b64encode(out.getvalue()).decode()}
            )
        link = request("/shares", "POST", password, {"images": images})
        path = "/s/" + link["id"]
        with sync_playwright() as pw:
            browser = pw.chromium.launch(
                executable_path=os.environ.get("EDCAM_BROWSER_PATH")
            )
            page = browser.new_page(
                ignore_https_errors=True, viewport={"width": 390, "height": 844}
            )
            page.goto(base + path)
            page.get_by_role("button", name="Nắng 35").click()
            assert (
                page.locator("img").evaluate(
                    "(img) => img.decode().then(() => img.naturalWidth)"
                )
                == 160
            )
            page.get_by_role("button", name="Chọn màu này").click()
            page.get_by_role("status").get_by_text("Đã chọn Nắng 35.").wait_for()
            assert request(path + "/votes", "GET", link["owner"])["results"][1] == [
                "Nắng 35",
                1,
            ]
            out = ROOT / "qa/video-review"
            out.mkdir(exist_ok=True)
            page.screenshot(path=str(out / "public-link-140.png"))
            request(path, "DELETE", link["owner"])
            assert page.goto(base + path).status == 404
            browser.close()
        print(
            "PASS mobile browser JPEG, pick, owner results and revocation", flush=True
        )
    finally:
        subprocess.run(adb + ["reverse", "--remove", f"tcp:{port}"], check=True)
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as temp:
        review(Path(temp))
