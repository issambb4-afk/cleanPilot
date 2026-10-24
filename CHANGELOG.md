# CleanPilot — Changelog

All notable changes to the CleanPilot desktop application are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-10-24

### Initial Release Candidate (v1.0.0-RC1)

#### 🛡 Security & Safety
- **Operation Lifecycle & Confirmation Engine**: Implemented unified 7-stage state machine (`DISCOVER` $\rightarrow$ `VALIDATE` $\rightarrow$ `PREVIEW` $\rightarrow$ `AUTHORIZE` $\rightarrow$ `EXECUTE` $\rightarrow$ `VERIFY` $\rightarrow$ `COMPLETE`) backed by durable SQLite journal logging.
- **Universal User Confirmation Modals**: Added `OperationConfirmationDialog` displaying exact targets, risk badges, irreversible warnings, and recovery limitations across all destructive and privileged actions (Duplicates, Large Files, Developer Caches, App Uninstaller, WinSxS Cleanup, System File Repair, and Network Stack Reset).
- **Reparse Point (Junction) Isolation**: Directory walks explicitly configure `EnumSet.noneOf(FileVisitOption.class)` and unlink directory junctions without entering or deleting target contents outside the tree.
- **Protected Path Boundary Enforcement**: Added `isSafeDeletablePath`, `isSafeResidualPath`, and `isSafeArtifactPath` preventing accidental targeting of drive roots (`C:\`), `%SystemRoot%`, `%ProgramFiles%`, user profile roots, and system boot/paging files.
- **Privilege Elevation Hardening**: Elevated execution via `WindowsSecurityUtils.runElevated` uses UTF-16LE Base64 `-EncodedCommand` execution with configurable timeouts ($\ge 1800$s for repairs), `-PassThru` inner exit code propagation, and process tree termination (`/T`).
- **AppX Debloating Fail-Safes**: Strict regex validation (`^[A-Za-z0-9._-]+$`) and permanent protection for critical Windows Store and shell packages.
- **Deep Recovery Path Traversal Defense**: Strict filename path sanitization and target boundary assertion in `DeepRecoveryScannerService`.
- **Startup Crash Reconciliation**: Automatic detection and reconciliation of interrupted operations on startup (`@PostConstruct`) without recursive auto-retries.

#### ⚡ Performance & Diagnostics
- **Zero Idle CPU**: Empirical 0.00% steady-state idle CPU consumption across all cores by converting all scanners to on-demand execution.
- **Memory-Bounded Parallel Deduplication**: Three-pass duplicate file hunting utilizing physical Inode deduplication, two-sided 4 KB boundary hashing, and off-heap 128 KB Direct ByteBuffer SHA-256 streaming.
- **Subprocess Pipe Deadlock Prevention**: Asynchronous virtual thread stream draining (`ProcessExecutionUtils.drainStream`) preventing buffer exhaustion hangs across native utilities.
- **Native Windows Console Encoding**: Auto-detection of system OEM codepages (`getNativeCharset()`) preventing mojibake on localized Windows builds.
- **High-Precision Threat Hunter**: Multi-signal evidence correlation (entropy, Authenticode signatures, foreign sockets, process ancestry) with developer tooling calibration (`node.exe`, `cargo.exe`, `python.exe`) and plain-language explainability.

#### 🎨 User Interface & Accessibility
- **Responsive Layout Reflow**: Scrollable navigation rail (`ScrollPane` with `fitToWidth="true"`, `.nav-scroll`) supporting compact windows down to 800×600 without vertical clipping.
- **AtlantaFX Modern Design System**: Standardized visual tokens based on AtlantaFX Primer theme foundations for both dark and light modes.
- **Keyboard Navigation & Accessibility**: High-visibility 2px focus rings (`:focused`) across all buttons, inputs, checkboxes, and tables.
- **Zero Telemetry Transparency**: Prominent visual badge and public privacy policy detailing 100% offline, local-first operation.

#### 📦 Distribution & Packaging
- **Standalone App-Image**: Portable bundle with embedded, isolated Java 21 LTS runtime.
- **Distribution Manifest Tooling**: Automated SHA-256 checksum calculation for Winget, Chocolatey, and Scoop package manager deployments.
- **Native MSI Installer Packaging**: Configured `packageMsi` task via `jpackage` with WiX Toolset detection.
