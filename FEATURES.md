# CleanPilot — Complete Feature Catalog & Architecture Guide

CleanPilot is an honest, privacy-first Windows system maintenance, disk diagnostics, and security hub built with **Java 21**, **Spring Boot 3**, **JavaFX 21**, **AtlantaFX**, and **OSHI**. It operates on a **strict zero-telemetry policy**: all analytics, diagnostics, and repairs run 100% locally on your machine with zero cloud dependencies.

---

## 📑 Table of Contents

1. [🛡 Rogue Process & Threat Hunter](#1--rogue-process--threat-hunter)
2. [📊 System Health & Resource Monitoring](#2--system-health--resource-monitoring)
3. [💻 Laptop Health & Diagnostics Hub](#3--laptop-health--diagnostics-hub)
4. [🔌 Hardware & Driver Inspector](#4--hardware--driver-inspector)
5. [💾 Storage & Deep Cleanup Suite](#5--storage--deep-cleanup-suite)
6. [🛠 Developer & Cache Cleaner](#6--developer--cache-cleaner)
7. [⏱ Startup & Boot Trace Optimizer](#7--startup--boot-trace-optimizer)
8. [📦 App Uninstaller & Leftover Cleaner](#8--app-uninstaller--leftover-cleaner)
9. [🔧 Windows System Repair & WinSxS Component Store](#9--windows-system-repair--winsxs-component-store)
10. [🌐 DNS Cache & Network Socket Optimizer](#10--dns-cache--network-socket-optimizer)
11. [🔒 Safety & Zero-Defect Architecture](#11--safety--zero-defect-architecture)
12. [⚙ Platform & Core UI](#12--platform--core-ui)

---

## 1. 🛡 Rogue Process & Threat Hunter

A multi-layered threat detection engine designed to identify malware, background cryptocurrency miners, reverse shells, and evasion techniques without risking OS stability.

* **Stealth Crypto-Miner & CPU Hijacker Detector**:
  * Tracks sustained background CPU velocity ($\ge 15\%$) for windowless processes executing from user-writable directories (`%TEMP%`, `%APPDATA%`, `AppData\Local`, `C:\Users\Public`, `Downloads`).
  * Scans invocation command lines for mining algorithm flags and pool parameters (`xmrig`, `minerd`, `stratum+tcp`, `nicehash`, `cryptonight`, `monero`).
* **Outbound Beaconing & Reverse Shell Hunter**:
  * Correlates active TCP/UDP sockets with running process IDs in real time.
  * Detects connections on known C2 (Command & Control) and reverse-shell ports (`4444`, `1337`, `31337`, `5555`, `6667`, `8088`, `9001`, `9050`).
  * Flags Living-off-the-Land Binaries (LOLBins) and script engines (`powershell.exe`, `cmd.exe`, `mshta.exe`, `rundll32.exe`, `certutil.exe`, `wscript.exe`) holding active outbound connections to external public IPs.
  * Alerts on rapid socket accumulation ($>5$ public internet sockets from an untrusted binary).
* **Process Masquerading & Fake System Binary Identifier**:
  * Verifies processes masquerading under critical Windows names (`svchost.exe`, `csrss.exe`, `lsass.exe`, `services.exe`, `wininit.exe`, `winlogon.exe`, `smss.exe`, `explorer.exe`).
  * Flags any process using these names if it runs outside legitimate directories (`C:\Windows\System32\` or `C:\Windows\`).
  * Detects deceptive double extensions (`invoice.pdf.exe`, `contract.docx.exe`, `photo.jpg.exe`, `notes.txt.exe`).
  * Identifies typosquatted system names (`scvhost.exe`, `svhost.exe`, `lssas.exe`, `csrs.exe`, `taskmgrr.exe`).
* **Authenticode Digital Signature Verification**:
  * Queries Windows Authenticode signatures via cached PowerShell inspection.
  * Distinguishes valid Microsoft / trusted vendor signatures from unsigned binaries and flags corrupted or tampered binaries (`HashMismatch`).
* **Shannon High-Entropy Scanner ($H = -\sum p_i \log_2 p_i$)**:
  * Computes byte entropy across executable binaries (0.00 to 8.00).
  * Flags high entropy ($\ge 7.20 / 8.00$) in user directories, identifying packed payloads (UPX, VMProtect, Themida) or encrypted ransomware droplets.
* **Fail-Safe Process Freezing (Suspension)**:
  * Implements `NtSuspendProcess` thread freezing via native API calls.
  * Allows users to instantly pause rogue CPU usage and sever beaconing without terminating the process, enabling forensic review.
* **Protected System Process Shield**:
  * Hardcoded immutable whitelist preventing termination or suspension of critical OS components (`System`, `csrss.exe`, `lsass.exe`, `svchost.exe`, `explorer.exe`, etc.).

---

## 2. 📊 System Health & Resource Monitoring

Real-time telemetry and resource usage tracking powered by native OSHI sensors.

* **Live Dashboard**:
  * Dynamic CPU load percentage gauge with per-core breakdown.
  * Physical RAM and Virtual Swap utilization meters.
  * Partition storage bar charts showing free vs. used capacity across all local drives (`C:`, `D:`, etc.).
  * System uptime, battery percentage, and thermal summary at a glance.
* **Deep Process Manager**:
  * Dynamic sorting by CPU usage, Memory (RSS), Disk I/O (Read/Write), Name, or PID.
  * Multi-tick CPU delta calculation normalized across all logical CPU cores.
  * Process hierarchy discovery (Parent PID, Child PIDs).
  * System Hog identifier: automatically flags runaway processes consuming $>15\%$ CPU or $>1$ GB RAM.
  * Graceful vs. forceful termination options with protection against killing essential processes.

---

## 3. 💻 Laptop Health & Diagnostics Hub

Deep hardware diagnostics designed for mobile PCs and battery-powered laptops.

* **Battery Health & Degradation Scorecard**:
  * Compares manufacturer design capacity (mWh) against current full charge capacity.
  * Calculates exact battery wear rate percentage and completed charge cycles.
  * Displays real-time discharge/charge wattage, estimated run time, and chemistry.
* **Thermal Metrics & Throttling Detection**:
  * Monitors CPU core temperatures, GPU temperatures, and cooling fan speeds (RPM).
  * Detects active thermal throttling events where CPU clocks are down-throttled due to heat.
* **Storage SMART Health**:
  * Queries volume SMART attributes, temperature, raw read error rates, and drive longevity indicators.
* **Memory Thrashing & Driver Pool Analysis**:
  * Analyzes paging pressure, hard page faults per second, and commit limit headroom.
  * Driver Leak Detection: flags Kernel Non-Paged Pool leaks ($>1.2$ GB), identifying faulty kernel drivers.
* **Operating System Stability & Crash Profiler**:
  * Parses Windows System Event Log for bugchecks, kernel panics (Event ID 1001), and unexpected shutdowns (Event ID 6008).
  * Warns about excessive uptime ($>7$ days) caused by Windows Fast-Startup kernel bloat.
* **Scorecard Export**:
  * Exports comprehensive diagnostic reports in Markdown format.

---

## 4. 🔌 Hardware & Driver Inspector

Comprehensive hardware inventory and driver health verification.

* **Hardware Device Tree**:
  * Organizes devices by category: Processors, Display Adapters, Network Adapters, Storage Controllers, Audio Devices, USB Hubs, and Human Interface Devices (HID).
  * Resolves Hardware IDs, Device Instance Paths, and Vendor IDs (VID/PID).
* **Driver Version & Signature Auditor**:
  * Inspects driver version, release date, INF file name, and device status codes (e.g., Code 43, Code 10, Code 28).
  * Flags outdated or ancient drivers ($>5$ years old) that may cause stability or performance issues.
  * Evaluates driver digital signature status to prevent rootkits and unverified driver loading.

---

## 5. 💾 Storage & Deep Cleanup Suite

High-throughput, safe file management to reclaim gigabytes of disk space.

* **Large Files Analyzer**:
  * Recursive streaming crawler capable of indexing hundreds of thousands of files in seconds.
  * Filter presets: $>100$ MB, $>500$ MB, $>1$ GB, $>5$ GB.
  * File type categorization: High-definition videos, disk images (`.iso`, `.vhd`), virtual machine drives, compressed archives.
* **Duplicate Files Finder (3-Tier Collision Pipeline)**:
  1. *Stage 1*: File size grouping (eliminates 90%+ of non-matches instantly).
  2. *Stage 2*: Partial 4 KB header hashing (verifies file format match).
  3. *Stage 3*: Full SHA-256 streaming hashing for bit-for-bit guarantee.
  * Smart Auto-Selection: "Keep Newest", "Keep Oldest", or "Keep Shortest Path".
* **Trash & Deep Recovery**:
  * Native Windows Recycle Bin reader displaying original file paths, deletion timestamps, and sizes.
  * Native file restore to original locations.
  * Unallocated space signature scanner for recovering accidentally deleted files.
* **Quarantine Vault**:
  * Stashes files into a protected, encrypted directory before deletion, allowing 100% reversible rollbacks.

---

## 6. 🛠 Developer & Cache Cleaner

Tailored cleanup for software developers, content creators, and power users.

* **Developer Artifact Reclaimer**:
  * Scans and safely removes heavy build artifacts:
    * Node.js / JavaScript: `node_modules`, `.next`, `.nuxt`, `.cache`
    * Java / JVM: `.gradle`, `build/`, `target/`, `.m2/repository`
    * Python: `__pycache__`, `.pytest_cache`, `.venv`, `.tox`
    * Rust / C++: `target/debug`, `target/release`, `.vs/`, `CMakeBuild/`
* **System & Browser Cache Purge**:
  * Windows Temp folders (`C:\Windows\Temp`, `%TEMP%`).
  * Windows Prefetch cache (cleaning stale prefetch files).
  * System Memory Crash Dumps (`MEMORY.DMP`, Minidumps).
  * Windows Thumbnail and Icon caches.
  * Google Chrome and Microsoft Edge disk caches (preserving cookies and session logins).

---

## 7. ⏱ Startup & Boot Trace Optimizer

Analyzes and optimizes Windows boot duration and startup applications.

* **Startup Application Manager**:
  * Enumerates entries in `HKLM\Software\Microsoft\Windows\CurrentVersion\Run`, `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, and shell startup folders.
  * Calculates impact scores for each startup item based on disk I/O and process footprint.
  * Safe toggle to enable/disable items without deleting original registry configurations.
* **Boot Trace Profiler**:
  * Queries Windows Diagnostics-Performance Event Logs (Event IDs 100 & 101).
  * Displays:
    * `MainPathBootTime`: Firmware and Windows core kernel load duration.
    * `BootPostBootTime`: Time spent initializing background startup apps before the desktop is responsive.
    * `TotalBootTime`: Total boot time in seconds.

---

## 8. 📦 App Uninstaller & Leftover Cleaner

Complete application lifecycle management beyond standard Windows Settings.

* **Installed Applications Inventory**:
  * Combines 32-bit (`Wow6432Node`) and 64-bit Windows Uninstall registries.
  * Displays publisher, install date, version, estimated disk footprint, and uninstall command strings.
* **Silent & Elevated Uninstallation**:
  * Executes silent or interactive native uninstallers with proper UAC privilege escalation.
* **Deep Leftover & Residual Scanner**:
  * Scans `%LOCALAPPDATA%`, `%APPDATA%`, and `C:\ProgramData` for orphaned folders left behind by uninstalled software.
  * Itemizes leftover size and enables safe, permanent cleanup of dead software residue.

---

## 9. 🔧 Windows System Repair & WinSxS Component Store

Repairs damaged operating system files and reclaims storage from the Windows Component Store.

* **WinSxS Component Store Analyzer & Purge**:
  * Executes non-destructive DISM Component Store analysis (`/AnalyzeComponentStore`).
  * Calculates Component Store Size, actual size, reclaimable package sizes, and superseding updates.
  * Component Store Cleanup (`/StartComponentCleanup`, `/ResetBase`) to purge superseded Windows Update backups.
* **System File Integrity (SFC & DISM Check)**:
  * Performs quick system corruption scans via `DISM /Online /Cleanup-Image /CheckHealth`.
  * Orchestrates System File Checker (`sfc /scannow`) to repair corrupt `.dll` and system files using verified Windows component store copies.
* **Windows AppX Modern Debloater**:
  * Scans pre-installed Windows UWP apps (Cortana, Xbox Live, Bing Weather, Zune, etc.).
  * Safely removes selected packages for the current user and unprovisions them from future user profiles.

---

## 10. 🌐 DNS Cache & Network Socket Optimizer

Diagnoses network anomalies and repairs network stack congestion.

* **Active Socket Leak Hunter**:
  * Inspects TCP connections for socket leaks (`CLOSE_WAIT` and `TIME_WAIT` congestion).
  * Flags applications leaking unclosed socket handles that cause gaming ping spikes or browser timeouts.
* **DNS Resolver Cache Flush**:
  * Uses PowerShell `Clear-DnsClientCache` with automated fallback to `ipconfig /flushdns`.
  * Purges poisoned or stale DNS entries to resolve domain resolution failures.
* **Winsock Stack Reset**:
  * Dispatches elevated `netsh winsock reset` to fix corrupted network LSP layers and restoration of network adapter communication.

---

## 11. 🔒 Safety & Zero-Defect Architecture

CleanPilot is designed to prevent data loss or system disruption:

1. **Non-Destructive Defaults**:
   * File deletions use the Windows Shell native Recycle Bin (`SHFileOperation` / native trash).
   * Irreversible hard deletions are strictly quarantined first or explicitly confirmed.
2. **System Protection Whitelist**:
   * PID 0, PID 4, and core system executables are protected from all termination or freeze actions.
   * Path validation ensures spoofed binaries are differentiated from genuine Windows components.
3. **Admin Privilege Isolation**:
   * Read-only telemetry runs in standard user mode without requiring elevated admin privileges.
   * Elevation (`WindowsSecurityUtils.runElevated`) is only requested when performing deep repair actions (SFC, Winsock reset, WinSxS cleanup).
4. **Strict Zero-Telemetry**:
   * No outbound HTTP/HTTPS telemetry requests, no analytics SDKs, and no cloud logins. All data stays on the user's device.

---

## 12. ⚙ Platform & Core UI

* **Modern Production Interface**: Built with JavaFX 21 and the AtlantaFX Nord Dark / Primer Light themes.
* **High Performance**: Native OSHI hardware access, streaming SHA-256 hashing, and non-blocking background workers (`ExecutorService`).
* **System Tray Companion**: Minimizes to the Windows notification area with background notifications.
* **Database Persistence**: Local embedded SQLite database storing historical scan metrics and storage freed trends over time.
