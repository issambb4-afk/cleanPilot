#!/usr/bin/env python3
"""
Publishes release assets (portable ZIP, checksums) to GitHub Releases
using credentials authenticated via Git Credential Manager.
"""

import sys
import os
import subprocess
import urllib.request
import urllib.parse
import json
import argparse

def get_github_token():
    try:
        p = subprocess.Popen(
            ["git", "credential", "fill"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        out, _ = p.communicate("protocol=https\nhost=github.com\n\n")
        creds = dict(line.split("=", 1) for line in out.splitlines() if "=" in line)
        token = creds.get("password")
        if not token:
            raise RuntimeError("No token found in Git Credential Manager.")
        return token
    except Exception as e:
        print(f"Error obtaining GitHub token: {e}", file=sys.stderr)
        sys.exit(1)

def api_request(url, method="GET", data=None, headers=None, token=None):
    if headers is None:
        headers = {}
    if token:
        headers["Authorization"] = f"token {token}"
    headers["User-Agent"] = "CleanPilot-Release-Uploader"
    headers["Accept"] = "application/vnd.github.v3+json"

    body_bytes = None
    if data is not None:
        if isinstance(data, dict):
            body_bytes = json.dumps(data).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif isinstance(data, (bytes, bytearray)):
            body_bytes = data
        elif isinstance(data, str):
            body_bytes = data.encode("utf-8")

    req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        content = resp.read()
        if content:
            try:
                return json.loads(content.decode("utf-8"))
            except Exception:
                return content
        return None

def main():
    parser = argparse.ArgumentParser(description="Publish CleanPilot GitHub Release")
    parser.add_argument("--repo", default="issambb4-afk/cleanPilot", help="GitHub repo (owner/repo)")
    parser.add_argument("--tag", default="v1.0.0-rc1", help="Release git tag")
    parser.add_argument("--title", default="CleanPilot v1.0.0-RC1 (Windows x64)", help="Release title")
    parser.add_argument("--zip", default="build/dist/CleanPilot-1.0.0-windows-x64-portable.zip", help="Path to zip file")
    parser.add_argument("--sums", default="build/dist/SHA256SUMS.txt", help="Path to SHA256SUMS.txt")
    parser.add_argument("--prerelease", action="store_true", default=True, help="Mark as prerelease")
    args = parser.parse_args()

    token = get_github_token()
    print(f"[*] Authenticated with GitHub for repository: {args.repo}")

    # 1. Check or Create Release
    releases_url = f"https://api.github.com/repos/{args.repo}/releases"
    tag_url = f"{releases_url}/tags/{args.tag}"

    release = None
    try:
        release = api_request(tag_url, token=token)
        print(f"[*] Found existing release for tag '{args.tag}' (ID: {release['id']})")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"[*] Creating new release for tag '{args.tag}'...")
            body_desc = (
                "## CleanPilot v1.0.0-RC1 — Production Release Candidate 🚀\n\n"
                "**Honest, Privacy-First Desktop Disk Analyzer & Storage Management Utility**\n\n"
                "### Included Packages:\n"
                "- **Portable Windows x64**: Zero installation required. Extract and run `CleanPilot.exe`.\n"
                "- **100% Offline**: Zero telemetry, no cloud indexing, authentic hardware metrics.\n\n"
                "### Verification Checksums:\n"
                "Refer to `SHA256SUMS.txt` below."
            )
            create_payload = {
                "tag_name": args.tag,
                "target_commitish": "main",
                "name": args.title,
                "body": body_desc,
                "draft": False,
                "prerelease": args.prerelease
            }
            release = api_request(releases_url, method="POST", data=create_payload, token=token)
            print(f"[OK] Created release '{args.title}' (ID: {release['id']})")
        else:
            raise

    upload_url_template = release["upload_url"] # e.g. https://uploads.github.com/.../assets{?name,label}
    upload_base = upload_url_template.split("{")[0]

    # Delete existing assets with same name if already present
    existing_assets = release.get("assets", [])

    files_to_upload = []
    if os.path.exists(args.zip):
        files_to_upload.append((args.zip, "application/zip"))
    else:
        print(f"Error: Zip file not found at '{args.zip}'", file=sys.stderr)
        sys.exit(1)

    if os.path.exists(args.sums):
        files_to_upload.append((args.sums, "text/plain"))

    for file_path, mime in files_to_upload:
        filename = os.path.basename(file_path)
        file_size_mb = os.path.getsize(file_path) / (1024 * 1024)

        # Remove existing if duplicate
        for asset in existing_assets:
            if asset["name"] == filename:
                print(f"[*] Deleting existing asset '{filename}' (ID: {asset['id']})...")
                api_request(asset["url"], method="DELETE", token=token)
                break

        print(f"[*] Uploading '{filename}' ({file_size_mb:.2f} MB)... This may take 30-60 seconds.")
        with open(file_path, "rb") as f:
            file_bytes = f.read()

        upload_url = f"{upload_base}?name={urllib.parse.quote(filename)}"
        upload_resp = api_request(upload_url, method="POST", data=file_bytes, headers={"Content-Type": mime}, token=token)
        download_url = upload_resp.get("browser_download_url")
        print(f"[OK] Upload complete: {download_url}")

    print("\n" + "=" * 60)
    print("[SUCCESS] Release published successfully!")
    print(f"View Release: https://github.com/{args.repo}/releases/tag/{args.tag}")
    print("=" * 60)

if __name__ == "__main__":
    main()
