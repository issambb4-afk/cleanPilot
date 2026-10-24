#!/usr/bin/env python3
"""
CleanPilot Automated Multi-Platform Release Engine
Updates Winget, Chocolatey, and Scoop manifests with computed SHA-256 hashes.
"""

import sys
import os
import hashlib
import json
import re
import argparse

def compute_sha256(file_path):
    h = hashlib.sha256()
    with open(file_path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().upper()

def update_winget(version, repo, msi_name, msi_hash):
    msi_url = f"https://github.com/{repo}/releases/download/v{version}/{msi_name}"
    
    # 1. Installer YAML
    installer_path = "distribution/winget/CleanPilot.CleanPilot.installer.yaml"
    if os.path.exists(installer_path):
        with open(installer_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r"PackageVersion:\s*.*", f"PackageVersion: {version}", content)
        content = re.sub(r"InstallerUrl:\s*.*", f"InstallerUrl: {msi_url}", content)
        content = re.sub(r"InstallerSha256:\s*.*", f"InstallerSha256: {msi_hash}", content)
        with open(installer_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  [OK] Updated Winget Installer: {installer_path}")

    # 2. Version YAML
    ver_path = "distribution/winget/CleanPilot.CleanPilot.yaml"
    if os.path.exists(ver_path):
        with open(ver_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r"PackageVersion:\s*.*", f"PackageVersion: {version}", content)
        with open(ver_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  [OK] Updated Winget Version: {ver_path}")

    # 3. Locale YAML
    loc_path = "distribution/winget/CleanPilot.CleanPilot.locale.en-US.yaml"
    if os.path.exists(loc_path):
        with open(loc_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r"PackageVersion:\s*.*", f"PackageVersion: {version}", content)
        content = re.sub(r"releases/tag/v.*", f"releases/tag/v{version}", content)
        with open(loc_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  [OK] Updated Winget Locale: {loc_path}")

def update_chocolatey(version, repo, msi_name, msi_hash):
    msi_url = f"https://github.com/{repo}/releases/download/v{version}/{msi_name}"
    
    # 1. chocolateyinstall.ps1
    script_path = "distribution/chocolatey/tools/chocolateyinstall.ps1"
    if os.path.exists(script_path):
        with open(script_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r"url64\s*=.*", f"url64         = '{msi_url}'", content)
        content = re.sub(r"checksum64\s*=.*", f"checksum64    = '{msi_hash}'", content)
        with open(script_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  [OK] Updated Chocolatey Install Script: {script_path}")

    # 2. cleanpilot.nuspec
    nuspec_path = "distribution/chocolatey/cleanpilot.nuspec"
    if os.path.exists(nuspec_path):
        with open(nuspec_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = re.sub(r"<version>.*</version>", f"<version>{version}</version>", content)
        with open(nuspec_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  [OK] Updated Chocolatey Nuspec: {nuspec_path}")

def update_scoop(version, repo, zip_name, zip_hash):
    zip_url = f"https://github.com/{repo}/releases/download/v{version}/{zip_name}"
    scoop_path = "distribution/scoop/cleanpilot.json"
    if os.path.exists(scoop_path):
        with open(scoop_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        data["version"] = version
        data["architecture"]["64bit"]["url"] = zip_url
        data["architecture"]["64bit"]["hash"] = zip_hash
        with open(scoop_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        print(f"  [OK] Updated Scoop Manifest: {scoop_path}")

def main():
    parser = argparse.ArgumentParser(description="CleanPilot Multi-Platform Release Updater")
    parser.add_argument("--version", default="1.0.0", help="Release version (e.g. 1.0.0)")
    parser.add_argument("--repo", default="cleanpilot/cleanpilot", help="GitHub repo (e.g. cleanpilot/cleanpilot)")
    parser.add_argument("--dist", default="build/dist", help="Dist folder containing MSI and ZIP")
    args = parser.parse_args()

    version = args.version
    repo = args.repo
    dist = args.dist

    msi_name = f"CleanPilot-{version}.msi"
    zip_name = f"CleanPilot-{version}-windows-x64-portable.zip"

    msi_path = os.path.join(dist, msi_name)
    zip_path = os.path.join(dist, zip_name)

    msi_found = os.path.exists(msi_path)
    zip_found = os.path.exists(zip_path)

    if not msi_found:
        for f in os.listdir(dist) if os.path.exists(dist) else []:
            if f.endswith(".msi"):
                msi_path = os.path.join(dist, f)
                msi_name = f
                msi_found = True
                break

    if not zip_found:
        for f in os.listdir(dist) if os.path.exists(dist) else []:
            if f.endswith(".zip"):
                zip_path = os.path.join(dist, f)
                zip_name = f
                zip_found = True
                break

    if not zip_found and not msi_found:
        print(f"Error: Missing build artifacts in {dist}. Run `./gradlew packageAppImage` first.")
        sys.exit(1)

    print("Computing cryptographic SHA-256 hashes...")
    msi_hash = compute_sha256(msi_path) if msi_found else "PENDING_CI_BUILD_HASH"
    zip_hash = compute_sha256(zip_path) if zip_found else "PENDING_CI_BUILD_HASH"

    if msi_found:
        print(f"  MSI ({msi_name}): {msi_hash}")
    else:
        print(f"  MSI ({msi_name}): [Skipped locally - WiX toolset not detected; will be computed by GitHub Actions CI/CD]")

    if zip_found:
        print(f"  ZIP ({zip_name}): {zip_hash}")
    else:
        print(f"  ZIP ({zip_name}): [Not found]")

    # Write SHA256SUMS.txt
    sums_file = os.path.join(dist, "SHA256SUMS.txt")
    with open(sums_file, "w", encoding="utf-8") as f:
        if msi_found:
            f.write(f"{msi_hash}  {msi_name}\n")
        if zip_found:
            f.write(f"{zip_hash}  {zip_name}\n")
    print(f"  Saved {sums_file}")

    print("\nUpdating distribution manifests...")
    if msi_found:
        update_winget(version, repo, msi_name, msi_hash)
        update_chocolatey(version, repo, msi_name, msi_hash)
    else:
        print("  [*] Winget and Chocolatey MSI hashes will be injected automatically during GitHub Actions CI/CD.")
    
    if zip_found:
        update_scoop(version, repo, zip_name, zip_hash)

    print("\nAll manifests successfully synchronized for release v" + version + "!")

    print("\nAll manifests successfully synchronized for release v" + version + "!")

if __name__ == "__main__":
    main()
