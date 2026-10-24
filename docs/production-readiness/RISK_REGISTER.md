# CleanPilot — Production Risk Register

**Status Date**: October 23, 2026  
**Auditor**: Senior Systems, Security & Release Engineering  
**Version**: 1.0.0-RC1

---

## 1. Risk Register Overview

| Risk ID | Category | Severity | Target Component | Description | Remediation Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RSK-01** | Security / Elevation | `CRITICAL` | `WindowsSecurityUtils.java` | Shell argument quoting flaw in elevated PowerShell invocation could allow injection or hang on prompt. | **RESOLVED & VERIFIED** (Base64 UTF-16LE encoded PowerShell execution with 120s timeout) |
| **RSK-02** | Data Safety | `CRITICAL` | `AppUninstallerService.java` | Unchecked recursive deletion of residual application directories risking system root deletion. | **RESOLVED & VERIFIED** (Path whitelist enforcement, min-depth $\ge 3$, junction interception) |
| **RSK-03** | Packaging / Storage | `CRITICAL` | `application.yml` | SQLite database stored in application working directory, throwing `SQLITE_CANTOPEN` in `Program Files`. | **RESOLVED & VERIFIED** (`~/.cleanpilot/cleanpilot.db` user-profile path) |
| **RSK-04** | Concurrency / I/O | `HIGH` | Subprocess Services | Subprocess stream pipe buffer exhaustion deadlocking `waitFor()` in `wevtutil`, `pnputil`, `diskpart`. | **RESOLVED & VERIFIED** (`ProcessExecutionUtils` non-blocking virtual thread stream draining) |
| **RSK-05** | UI / Concurrency | `HIGH` | View Controllers | Uncaught `RejectedExecutionException` crashing JavaFX thread pool during view transition/shutdown. | **RESOLVED & VERIFIED** (Safe `submitAsync` check & catch across all 8 UI controllers) |
| **RSK-06** | Filesystem Interop | `HIGH` | `QuarantineService.java` | `Files.move(..., ATOMIC_MOVE)` failing on cross-volume moves across secondary drives (`D:\` to `C:\`). | **RESOLVED & VERIFIED** (Cross-volume stream copy + delete fallback & vault confinement check) |
| **RSK-07** | Test Hygiene | `MEDIUM` | `WindowsComponentStoreServiceTest.java` | Unit tests mutating live `C:\Windows\SoftwareDistribution\Download` directory on host. | **RESOLVED & VERIFIED** (Dry-run mode parameter added and verified) |
| **RSK-08** | I18N / Encoding | `MEDIUM` | `ProcessExecutionUtils.java` | Localized Windows console outputs (`dism`, `sfc`, `reg`) scrambled by hardcoded UTF-8 decoding. | **RESOLVED & VERIFIED** (`getNativeCharset()` auto-resolving Windows OEM codepage) |
| **RSK-09** | Security / Traversal | `CRITICAL` | `DeveloperCleanupService.java` | `FileVisitOption.FOLLOW_LINKS` and unvalidated recursive delete of developer artifacts risking target escapes. | **RESOLVED & VERIFIED** (Removed link-following, added `isSafeArtifactPath`, junction interception) |
| **RSK-10** | Security / Injection | `CRITICAL` | `WindowsAppxDebloaterService.java` | Unsanitized AppX package full name string passed directly into PowerShell command, and unprotected store apps. | **RESOLVED & VERIFIED** (Regex pattern `^[A-Za-z0-9._-]+$`, `PROTECTED_PACKAGES` fail-safe) |
| **RSK-11** | Data Safety | `CRITICAL` | `FileDeletionService.java` | No protection against deleting critical OS system files or drive roots from duplicate/large file cleaners. | **RESOLVED & VERIFIED** (`isSafeDeletablePath` blocking system roots, Windows directories, boot files) |
| **RSK-12** | Security / Traversal | `CRITICAL` | `DeepRecoveryScannerService.java` | Path traversal sequence in recovered filename (`../../`) escaping extraction directory. | **RESOLVED & VERIFIED** (Filename path sanitization and boundary assertion) |
