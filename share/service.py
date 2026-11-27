"""Ed-Cam share service. Serve application with a WSGI server behind HTTPS."""

import base64
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import time
from contextlib import closing
from http import HTTPStatus
from pathlib import Path

MAX_BODY = 24 * 1024 * 1024
TTL = 7 * 86400
PAGE = Path(__file__).with_name("viewer.html").read_bytes()


def respond(start, code, data, mime="application/json"):
    if isinstance(data, dict):
        data = json.dumps(data).encode()
    start(
        f"{code} {HTTPStatus(code).phrase}",
        [
            ("Content-Type", mime),
            ("Content-Length", str(len(data))),
            ("Cache-Control", "no-store"),
            ("Referrer-Policy", "no-referrer"),
            ("X-Content-Type-Options", "nosniff"),
            (
                "Content-Security-Policy",
                "default-src 'none'; img-src 'self'; connect-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; frame-ancestors 'none'",
            ),
        ],
    )
    return [data]


def application(env, start):
    def reply(code, data):
        return respond(start, code, data)

    path = env["PATH_INFO"]
    method = env["REQUEST_METHOD"]
    credential = env.get("HTTP_AUTHORIZATION", "").removeprefix("Bearer ")
    db_file = os.environ.get("EDCAM_DB", "edcam-shares.sqlite3")
    with closing(sqlite3.connect(db_file, timeout=10)) as db, db:
        db.execute("PRAGMA foreign_keys=ON")
        db.execute(
            "CREATE TABLE IF NOT EXISTS shares(id TEXT PRIMARY KEY, owner TEXT, expires INTEGER)"
        )
        db.execute(
            "CREATE TABLE IF NOT EXISTS images(id TEXT REFERENCES shares ON DELETE CASCADE, n INTEGER, label TEXT, jpeg BLOB, PRIMARY KEY(id,n))"
        )
        db.execute(
            "CREATE TABLE IF NOT EXISTS votes(id TEXT REFERENCES shares ON DELETE CASCADE, voter TEXT, n INTEGER, PRIMARY KEY(id,voter))"
        )
        now = int(time.time())
        db.execute("DELETE FROM shares WHERE expires<=?", (now,))
        if method == "POST" and path == "/shares":
            key = os.environ.get("EDCAM_UPLOAD_KEY", "")
            if not key or not hmac.compare_digest(key.encode(), credential.encode()):
                return reply(403, {})
            try:
                length = int(env.get("CONTENT_LENGTH") or 0)
                if not 0 < length <= MAX_BODY:
                    return reply(413, {})
                items = json.loads(env["wsgi.input"].read(length))["images"]
                if not isinstance(items, list) or not 1 <= len(items) <= 3:
                    raise ValueError()
                images = []
                for item in items:
                    jpeg = base64.b64decode(item["jpeg"], validate=True)
                    label = item["label"]
                    if (
                        not jpeg.startswith(b"\xff\xd8\xff")
                        or not jpeg.endswith(b"\xff\xd9")
                        or not isinstance(label, str)
                        or len(label) > 100
                    ):
                        raise ValueError()
                    images.append((label, jpeg))
            except (KeyError, TypeError, ValueError):
                return reply(400, {})
            share = secrets.token_urlsafe(18)
            owner = secrets.token_urlsafe(32)
            db.execute(
                "INSERT INTO shares VALUES(?,?,?)",
                (share, hashlib.sha256(owner.encode()).hexdigest(), now + TTL),
            )
            db.executemany(
                "INSERT INTO images VALUES(?,?,?,?)",
                [(share, i, *image) for i, image in enumerate(images)],
            )
            return reply(201, {"id": share, "owner": owner})
        match = re.fullmatch(r"/s/([\w-]{24})(?:/(info|votes|vote|[0-2]))?", path)
        if not match:
            return reply(404, {})
        share, action = match.groups()
        record = db.execute("SELECT owner FROM shares WHERE id=?", (share,)).fetchone()
        if not record:
            if method == "GET" and action is None:
                return respond(
                    start,
                    404,
                    "<!doctype html><html lang=vi><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Ed-Cam</title><main><h1>Liên kết đã đóng</h1><p>Ảnh đã hết hạn hoặc người gửi đã thu hồi liên kết.</p></main></html>".encode(),
                    "text/html; charset=utf-8",
                )
            return reply(404, {"error": "expired"})
        if method == "DELETE" or action == "votes":
            if not hmac.compare_digest(
                record[0], hashlib.sha256(credential.encode()).hexdigest()
            ):
                return reply(403, {})
            if method == "DELETE" and action is None:
                db.execute("DELETE FROM shares WHERE id=?", (share,))
                return reply(200, {})
            if method == "GET" and action == "votes":
                counts = db.execute(
                    "SELECT label,(SELECT COUNT(*) FROM votes v WHERE v.id=i.id AND v.n=i.n) FROM images i WHERE id=? ORDER BY n",
                    (share,),
                ).fetchall()
                return reply(200, {"results": counts})
        if method == "GET":
            if action is None:
                return respond(start, 200, PAGE, "text/html; charset=utf-8")
            if action == "info":
                return reply(
                    200,
                    {
                        "labels": [
                            r[0]
                            for r in db.execute(
                                "SELECT label FROM images WHERE id=? ORDER BY n",
                                (share,),
                            )
                        ]
                    },
                )
            if action in ("0", "1", "2"):
                row = db.execute(
                    "SELECT jpeg FROM images WHERE id=? AND n=?", (share, int(action))
                ).fetchone()
                if row:
                    return respond(start, 200, row[0], "image/jpeg")
        if method == "POST" and action == "vote":
            voter = env.get("HTTP_X_EDCAM_VOTER", "")
            choice = env.get("HTTP_X_EDCAM_CHOICE", "")
            if (
                not re.fullmatch(r"[a-zA-Z0-9-]{20,64}", voter)
                or choice not in ("0", "1", "2")
                or not db.execute(
                    "SELECT 1 FROM images WHERE id=? AND n=?", (share, int(choice))
                ).fetchone()
            ):
                return reply(400, {})
            db.execute(
                "INSERT OR REPLACE INTO votes VALUES(?,?,?)",
                (share, voter, int(choice)),
            )
            return reply(200, {})
        return reply(404, {})
