# CleanPilot — Windows Compatibility Matrix

**Target OS**: Windows 10 & Windows 11  
**Architectures**: x86_64 (AMD64 / Intel 64), AArch64 (ARM64 Windows via emulation / native JVM)

---

## 1. Supported Operating System Editions

| Operating System | Version / Build | Architecture | Privileges | Support Status | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Windows 11 Pro / Enterprise** | 22H2 / 23H2 / 24H2 | x64 | Standard / Admin | **Fully Supported** | Full hardware sensor, OSHI, AtlantaFX dark/light themes |
| **Windows 11 Home** | 22H2 / 23H2 / 24H2 | x64 | Standard / Admin | **Fully Supported** | BitLocker metrics omitted on editions without BitLocker |
| **Windows 10 Pro / Enterprise** | 21H2 / 22H2 | x64 | Standard / Admin | **Fully Supported** | Tested with PowerShell 5.1 & modern Windows Terminal |
| **Windows 10 Home** | 21H2 / 22H2 | x64 | Standard / Admin | **Fully Supported** | Verified standard user capabilities |
| **Windows 11 ARM64** | 23H2 / 24H2 | ARM64 | Standard / Admin | **Supported** | Runs on Java 21 ARM64; OSHI JNA native fallback active |

---

## 2. Filesystem & Drive Configurations

* **NTFS**: Fully supported; verified reparse point / junction handling and file attributes.
* **ReFS**: Fully supported for file scanning and duplicate detection.
* **FAT32 / exFAT**: Supported for external drives, USB sticks, and SD cards (quarantine utilizes cross-volume copy fallback).
* **Multi-Drive Setups**: Supported (`C:\`, `D:\`, `E:\`); cross-volume quarantine fallbacks handle moves between non-system drives and the user vault.
