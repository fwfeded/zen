"""Prepare exactly three reviewed GitHub Release assets; never upload or publish."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

from prepare_update_site import CERT, metadata


def release_urls(repository, version):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Expected owner/repository without a URL or credentials")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Stable release version must be major.minor.patch")
    base = f"https://github.com/{repository}/releases"
    return base + "/latest/download/latest.json", base + f"/download/v{version}/"


def prepare(apk, out, notes, info, repository):
    endpoint, base = release_urls(repository, info["versionName"])
    if len(notes) > 8000 or not notes.strip():
        raise ValueError("Release notes must contain 1..8000 characters")
    if not 0 < apk.stat().st_size <= 120 * 1024 * 1024:
        raise ValueError("APK size must be 1..120 MiB")
    if out.exists() and any(out.iterdir()):
        raise ValueError("Use an empty output folder to avoid mixing releases or private files")
    # Verify the real compiled APK, not just a build command or a sidecar setting.
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        dex = [n for n in names if re.fullmatch(r"classes\d*\.dex", n)]
        if not any(endpoint.encode() in archive.read(n) for n in dex):
            raise ValueError("APK does not contain this repository's fixed update endpoint; rebuild it")
        if any(n.endswith(".zentheme") or n.startswith("assets/paintings/") for n in names):
            raise ValueError("External theme cards/paintings must not be bundled in this release")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    filename = f"zen-{info['versionCode']}.apk"
    manifest = dict(info, apkUrl=base + filename, sha256=digest,
                    sizeBytes=apk.stat().st_size, notes=notes.strip())
    payload = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
    if len(payload) > 64 * 1024:
        raise ValueError("Manifest exceeds the app's 64 KiB limit")
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, out / filename)
    (out / "latest.json").write_bytes(payload)
    (out / "SHA256SUMS.txt").write_text(
        f"{digest}  {filename}\n{hashlib.sha256(payload).hexdigest()}  latest.json\n", encoding="utf-8")
    return {"repository": repository, "tag": "v" + info["versionName"],
            "updateEndpoint": endpoint, "assets": [filename, "latest.json", "SHA256SUMS.txt"],
            "sha256": digest, "published": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--repository", default="fwfeded/zen")
    parser.add_argument("--aapt", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--expected-cert", default=CERT)
    args = parser.parse_args()
    def run(*command):
        return subprocess.run(command, check=True, capture_output=True, text=True, encoding="utf-8").stdout
    info = metadata(run(args.aapt, "dump", "badging", str(args.apk)),
                    run(args.apksigner, "verify", "--print-certs", str(args.apk)), args.expected_cert)
    result = prepare(args.apk.resolve(strict=True), args.out.resolve(),
                     args.notes.read_text(encoding="utf-8").strip(), info, args.repository)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
