"""Prepare a static update site. This command does not publish or contact a server."""
import argparse
import hashlib
import html
import json
import re
import shutil
import subprocess
from pathlib import Path
from urllib.parse import urlsplit, quote

PACKAGE = "dev.zen.launcher"
CERT = "b5c8d9d5636fa2a5dc18ce91708e3dc1d4d8e25087e1aac69b39ca005c7c220e"


def base_url(value):
    parts = urlsplit(value)
    if (parts.scheme != "https" or not parts.hostname or parts.username or parts.password
            or parts.query or parts.fragment or any(c.isspace() for c in value)):
        raise ValueError("Use an HTTPS directory URL without credentials, query or fragment")
    if parts.port is not None and not 1 <= parts.port <= 65535:
        raise ValueError("Invalid port")
    return value.rstrip("/") + "/"


def metadata(badging, signature, expected_cert):
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not match or match[1] != PACKAGE or int(match[2]) <= 0:
        raise ValueError("APK package/version is invalid")
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})", signature)
    if {v.lower() for v in fingerprints} != {expected_cert.lower()}:
        raise ValueError("APK signing identity does not match the existing personal channel")
    return {"packageName": match[1], "versionCode": int(match[2]), "versionName": match[3]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--base-url", help="Final HTTPS directory; omit for local preview only")
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--expected-cert", default=CERT)
    args = parser.parse_args()
    base = base_url(args.base_url) if args.base_url else None
    notes = args.notes.read_text(encoding="utf-8").strip()
    if len(notes) > 8000:
        raise ValueError("Release notes exceed 8000 characters")
    apk = args.apk.resolve(strict=True)
    out = args.out.resolve()
    if not base and (out / "latest.json").exists():
        raise ValueError("Preview destination contains a production manifest; use another output directory")
    if not 0 < apk.stat().st_size <= 120 * 1024 * 1024:
        raise ValueError("APK must be at most 120 MiB")
    def run(*command):
        return subprocess.run(command, check=True, capture_output=True, text=True, encoding="utf-8").stdout
    release = metadata(run(args.aapt, "dump", "badging", str(apk)),
                       run(args.apksigner, "verify", "--print-certs", str(apk)), args.expected_cert)
    name = f"zen-{release['versionCode']}.apk"
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    release.update(sha256=digest,
                   sizeBytes=apk.stat().st_size, notes=notes)
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, out / name)
    (out / "release-info.json").write_text(json.dumps(release, ensure_ascii=False, indent=2), encoding="utf-8")
    if base:
        release["apkUrl"] = base + quote(name)
        (out / "latest.json").write_text(json.dumps(release, ensure_ascii=False, indent=2), encoding="utf-8")
    esc = html.escape
    update = (f'<p>应用内更新地址</p><code>{esc(base)}latest.json</code>' if base else
              '<p class="notice">本地预览 · 尚未上线。正式部署后才可在应用内检查新版本。</p>')
    page = f'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>禅 · 版本更新</title><style>
*{{box-sizing:border-box}}body{{margin:0;background:#edf0e9;color:#34433b;font:16px/1.8 system-ui,sans-serif}}main{{max-width:640px;margin:8vh auto;padding:36px}}h1{{font:42px Georgia,serif;letter-spacing:.2em}}h2{{font-size:18px;font-weight:500}}small,p{{color:#617168}}a{{display:inline-block;border-radius:28px;background:#526c5d;color:white;padding:12px 26px;text-decoration:none;margin:12px 0}}pre{{font:inherit;white-space:pre-wrap}}code{{display:block;overflow-wrap:anywhere;font-size:12px}}.notice{{border-left:2px solid #819688;padding-left:16px}}section{{border-top:1px solid #cfd8ce;margin-top:28px;padding-top:20px}}</style>
<main><small>保持此刻</small><h1>禅</h1><h2>{esc(release['versionName'])}</h2><pre>{esc(notes)}</pre>
<a href="{esc(name)}" download>下载安装包 · {release['sizeBytes']/1024/1024:.1f} MB</a>
<p>已安装禅时，请直接覆盖安装，保留本地设置与专注记录。安装由 Android 系统确认。</p>
<section>{update}</section><section><small>安装包 SHA-256</small><code>{release['sha256']}</code></section></main></html>'''
    (out / "index.html").write_text(page, encoding="utf-8")
    (out / ".nojekyll").write_text("", encoding="utf-8")
    (out / "_headers").write_text("/latest.json\n  Cache-Control: no-cache\n", encoding="utf-8")
    print(json.dumps({"directory": str(out), "version": release['versionName'],
                      "manifest": base + "latest.json" if base else None, "published": False}, ensure_ascii=False))


if __name__ == "__main__":
    main()
