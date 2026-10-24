# CleanPilot — Privacy Policy & Zero-Telemetry Charter

**Effective Date**: October 24, 2026  
**Version**: 1.0.0  
**Application**: CleanPilot Desktop for Windows  

---

## 1. Core Commitment: 100% Offline, Strict Zero-Telemetry

CleanPilot was engineered with a non-negotiable architectural invariant: **your workstation data belongs solely to you**. 

Commercial disk cleaners and system utilities frequently bundle background advertising telemetry, analytics beacons, usage trackers, and cloud-indexing services. CleanPilot fundamentally rejects this paradigm.

1. **Zero Outbound Network Calls**: CleanPilot does not establish any network sockets, HTTP/HTTPS connections, or remote API queries.
2. **Zero Telemetry SDKs**: The application contains no analytics tracking libraries (Google Analytics, Firebase, Mixpanel, Sentry, TelemetryDeck, Segment, etc.).
3. **Zero Crash Pings**: In the event of an unexpected crash or runtime exception, stack traces are logged locally to standard Windows logging directories. No remote error reports or diagnostic dumps are ever transmitted.
4. **Zero Account Requirement**: CleanPilot requires no user account, registration, login credentials, email address, or license validation server.

---

## 2. Information Handled Locally on Your Device

CleanPilot inspects and processes data exclusively on your local workstation to perform storage analysis and system diagnostics:

### 2.1 File & Directory Metadata
- **Paths, Filenames & Sizes**: Scanned locally using Java NIO.2 to calculate disk usage, discover duplicate files, locate large files, and detect developer caches (`node_modules`, `.gradle`, `target`, `.venv`).
- **Cryptographic Hashes (SHA-256)**: Generated on-demand using streaming off-heap buffers to confirm file duplicate identity. Hashes remain in local volatile memory and SQLite tables.

### 2.2 Operating System & Hardware Metrics
- **System Telemetry**: CPU load percentage, RAM usage, disk partition capacity, battery health degradation, and thermal sensors are queried directly from Windows kernel counters via [OSHI](https://github.com/oshi/oshi) and JNA. These metrics are displayed in real-time on your screen and are never persisted beyond volatile memory.
- **Process Activity**: Active process names, process IDs (PIDs), and resource consumption are queried locally to monitor performance and identify high-entropy or rogue processes.

### 2.3 Application Quarantine Vault
- When files are moved to the CleanPilot Quarantine Vault (soft-delete), they are moved to a local user folder:
  `%USERPROFILE%\.cleanpilot\quarantine\`
- Metadata (original path, file size, SHA-256 hash, and deletion timestamp) is stored in the local embedded database:
  `%USERPROFILE%\.cleanpilot\cleanpilot.db`
- Quarantined files are retained for a configurable 30-day retention window before auto-purge, and can be restored at any time with 1 click.

---

## 3. Data Storage & Local Persistence

All CleanPilot persistent state is isolated inside your user profile directory:

```
C:\Users\<YourUsername>\.cleanpilot\
├── cleanpilot.db        # SQLite database storing scan history and quarantine index
└── quarantine\          # Local vault containing soft-deleted files
```

CleanPilot does not use the Windows Registry for user telemetry or application tracking.

---

## 4. User Rights & Complete Data Erasure

You retain complete control over your data at all times:

1. **Viewing Your History**: You can review every maintenance operation and scan audit directly in the application under **Scan History**.
2. **Restoring Quarantined Files**: You can inspect, restore, or permanently purge any quarantined file under **Trash & Recovery**.
3. **Complete Erasure / Factory Reset**: To completely remove all CleanPilot data, history, and preferences from your computer, delete the `.cleanpilot` folder:
   ```powershell
   Remove-Item -Recurse -Force "$env:USERPROFILE\.cleanpilot"
   ```
   Doing so completely erases all local history and quarantine items.

---

## 5. Contact & Questions

If you have questions regarding CleanPilot's privacy architecture or local-first invariants, please open an issue on the official GitHub repository:
- **Repository**: [https://github.com/cleanpilot/cleanpilot](https://github.com/cleanpilot/cleanpilot)
- **Security & Privacy Contact**: `security@cleanpilot.org`
