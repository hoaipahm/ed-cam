# Ed-Cam photo links

The Android camera, album and Android Sharesheet work without this service. The web-link controls upload the selected JPEG or three re-rendered previews only after confirmation. Anyone with the viewer URL can open it. Owner credentials grant result access and revocation. A browser remembers its vote per link and can change it. Vote totals count browser identifiers, not verified people.

## Run on your HTTPS host

Use Python 3.10+ and a persistent writable directory for SQLite. Run the WSGI application behind an HTTPS reverse proxy. For example, on a Linux host with Gunicorn installed, run the following from the share directory after setting EDCAM_UPLOAD_KEY to a generated secret in the service environment.

```sh
export EDCAM_DB=/var/lib/edcam/shares.sqlite3
gunicorn --bind 127.0.0.1:8080 --workers 2 service:application
```

Forward the domain root to that listener. Allow a request body of 24 MiB. Use proxy rate limits for uploads and voting and avoid recording viewer URLs in public access logs. Keep the upload key outside the repository. Enter the HTTPS origin and the upload key on the phone under **Cài đặt → Máy chủ chia sẻ**. Android trusts the normal system HTTPS certificate chain. No hosting account or domain is provisioned by the APK.

## Storage and lifecycle

SQLite stores selected JPEG bytes, labels, votes and the hash of the owner token. Links expire after seven days. Requests remove expired database rows. Configure host backup retention separately. Revoking a link blocks subsequent requests. Recipients can keep previously downloaded copies. Uninstalling the app loses local management credentials, so revoke active links before uninstalling.

## Verification

Run `python -m unittest discover -s share` from the repository root. The Android and browser integration test in `qa/review-links.py` uses a temporary local HTTPS service, isolated database, temporary certificate and an emulator. Only the test instrumentation trusts that certificate.
