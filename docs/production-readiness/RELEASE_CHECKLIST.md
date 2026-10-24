# CleanPilot — Production Release Checklist

**Release Candidate**: 1.0.0-RC1  
**Target Platform**: Windows 10 & 11

---

## 1. Security & Safety Release Gates

- [x] **P0-1: Command Injection Elimination**: All elevated and standard subprocess commands strictly reject arbitrary string interpolation and use parameterized lists or Base64 UTF-16LE encoded PowerShell scripts.
- [x] **P0-2: Operating System Path Protection**: Recursive and permanent deletions (`deleteResidual`, `deleteArtifact`, `deleteFile`) strictly enforce path depth and reject drive roots, `%SystemRoot%`, `%ProgramFiles%`, user profile root, and boot/paging files.
- [x] **P0-3: Reparse Point & Junction Isolation**: File walks use `noneOf(FileVisitOption.class)` and explicitly intercept symbolic links and directory junctions, unlinking them directly without entering target folders.
- [x] **P0-4: AppX Debloating Fail-Safes**: Package full names are validated with `^[A-Za-z0-9._-]+$` and core system packages (`Microsoft.WindowsStore`, etc.) are permanently protected.
- [x] **P0-5: Process Termination Fail-Safes**: Critical Windows binaries, system PIDs $\le 4$, own PID, and parent PID are protected from termination or suspension.
- [x] **P0-6: Zero Telemetry**: Confirmed 0 external network pings, 0 telemetry SDKs, 0 remote data transmission.

---

## 2. Stability & Packaging Release Gates

- [x] **P1-1: Subprocess Pipe Deadlock Prevention**: Subprocesses asynchronously drain `stdout` and `stderr` using virtual threads before `waitFor()`.
- [x] **P1-2: Localized Codepage Decoding**: Native Windows utilities (`dism`, `sfc`, `reg`) are decoded using the system OEM codepage.
- [x] **P1-3: Non-Admin Installation Storage**: Database relocated to `~/.cleanpilot/cleanpilot.db` to prevent `[SQLITE_CANTOPEN]` crash in `C:\Program Files`.
- [x] **P1-4: Cross-Volume Storage Interop**: Quarantine move falls back to streaming copy + delete across distinct drive volumes.
- [x] **P1-5: Thread Concurrency Safety**: JavaFX controllers guard task dispatch against `RejectedExecutionException` during view switching or application shutdown.

---

## 3. Operation Lifecycle & Journaling Release Gates

- [x] **P2-1: Standardized State Machine**: All destructive maintenance operations enforce `DISCOVER` -> `VALIDATE` -> `PREVIEW` -> `AUTHORIZE` -> `EXECUTE` -> `VERIFY` -> `COMPLETE`.
- [x] **P2-2: User Confirmation Modals**: `OperationConfirmationDialog` displays exact target paths, risk classifications, irreversible warnings, and recovery limitations.
- [x] **P2-3: Durable Operation Journal**: Persists operation ID, timestamp, items modified, bytes recovered, and error summaries in SQLite.
- [x] **P2-4: Crash Reconciliation**: Incomplete or power-loss interrupted operations are detected and marked `INTERRUPTED` on application startup without blind auto-retries.

---

## 4. Threat Hunter Evidence Calibration Release Gates

- [x] **P3-1: Multi-Signal Evidence Correlation**: Combines Authenticode signature, entropy, foreign sockets, C2 ports, and process lineage.
- [x] **P3-2: Normalized CPU Delta Sampling**: Uses multi-core normalized CPU delta across consecutive sampling ticks to avoid false-positive spikes.
- [x] **P3-3: Developer Tooling Calibration**: Prevents false-positive crypto-miner classifications for `node.exe`, `python.exe`, `cargo.exe`, and compilers in user project paths.
- [x] **P3-4: Plain-Language Explainability**: Generates transparent rationale, benign context, and recommended investigation steps.
- [x] **P3-5: Two-Stage Process Termination**: Graceful SIGTERM request (`ProcessHandle.destroy()`) with 600ms grace period before force-killing.

---

## 5. UI/UX Modernization & Responsive Layout Release Gates

- [x] **UI-1: Scrollable Navigation Rail**: Encapsulated sidebar in `ScrollPane` (`fitToWidth="true"`, `.nav-scroll`), eliminating vertical clipping on displays $<780$px height.
- [x] **UI-2: Categorical Organization**: Structured 15 views into 5 logical categories (OVERVIEW, MONITOR, MANAGE, REPAIR, ACTIVITY).
- [x] **UI-3: Modern Visual Design Tokens**: Standardized design tokens in `app.css` using AtlantaFX Primer theme foundations for both dark and light modes.
- [x] **UI-4: Responsive Reflow**: Proportional reflow verified across 1440×900, 1280×800, 1024×768, and 800×600 window dimensions.
- [x] **UI-5: Keyboard & Focus Accessibility**: 2px high-visibility accent focus rings (`:focused`) across all buttons, input fields, checkboxes, and tables.
- [x] **UI-6: Contextual Tooltips**: Added descriptive tooltips to all compact buttons, navigation rail items, and toolbar controls.

---

## 6. Distribution & Packaging Release Gates

- [x] **PKG-1: Standalone Runnable Fat Jar**: Built and verified `build/libs/cleanpilot-1.0.0.jar` (65.3 MB).
- [x] **PKG-2: Native Windows App Image**: Created standalone portable bundle via `jpackage` at `build/dist/CleanPilot/CleanPilot.exe` with bundled Java 21 runtime.
- [x] **PKG-3: Spring Boot Context Bootstrap Test**: `ApplicationContextStartupTest` verifies all beans, controllers, services, and JPA repositories instantiate cleanly.
- [x] **PKG-4: Live Runtime Verification**: Clean execution confirmed with zero crashes, valid database migration, and system tray registration.

---

## 7. Resource Efficiency & Zero-Surcharge Release Gates

- [x] **CPU-1: On-Demand Execution Only**: No heavy maintenance or forensic scans execute automatically upon view loading or application startup.
- [x] **CPU-2: Default Disabled File Watchers**: `StorageSentinelService` NIO watcher is disabled by default (`isStorageSentinelEnabled() == false`).
- [x] **CPU-3: Default Disabled Scheduled Maintenance**: `ScheduledMaintenanceService` schedule defaults to `DISABLED`.
- [x] **CPU-4: Infinite Polling Elimination**: UI auto-refresh checkboxes (`ProcessesView`, `LaptopDiagnosticsView`) default to `selected="false"` with timelines paused until explicitly activated by user.
- [x] **CPU-5: Subprocess Spawn Prevention**: No background spawning of `powershell.exe`, `wevtutil.exe`, `pnputil.exe`, or `netstat.exe` during idle or navigation.
- [x] **CPU-6: Verified 0% Idle CPU**: Steady-state CPU consumption measured empirically at **0.00% across all CPU cores**.
