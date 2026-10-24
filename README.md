<p align="center">
  <img src="docs/assets/logo/cleanpilot-logo-dark.svg" alt="CleanPilot Logo" width="480">
  <br/>
  <strong>Honest, Privacy-First Desktop Disk Analyzer &amp; Storage Management Utility</strong>
  <br/>
  <em>Built with Spring Boot 3, Java 21 (Virtual Threads), JavaFX 21+, AtlantaFX, and OSHI</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Release-v1.0.0-2563EB.svg?style=flat-square" alt="Release">
  <img src="https://img.shields.io/badge/Java-21%2B%20LTS-F97316.svg?style=flat-square" alt="Java 21">
  <img src="https://img.shields.io/badge/Telemetry-Zero%20%28100%25%20Offline%29-16A34A.svg?style=flat-square" alt="Zero Telemetry">
  <img src="https://img.shields.io/badge/OS-Windows%2010%20%2F%2011-0284C7.svg?style=flat-square" alt="Windows 10/11">
  <img src="https://img.shields.io/badge/License-Apache%202.0-6366F1.svg?style=flat-square" alt="License">
</p>

---

## 🛡️ Core Philosophy & Safety Directives

CleanPilot strictly rejects fake cleaner patterns, simulated progress bars, deceptive performance claims, and arbitrary deletions:

1. **Authentic Telemetry**: All metrics originate from real operating system and hardware APIs via [OSHI](https://github.com/oshi/oshi) (`com.github.oshi:oshi-core`) and Java NIO.2.
2. **Local-First & Zero Telemetry**: Absolutely no network telemetry, no external analytic tracking, and no cloud indexing. CleanPilot runs 100% offline.
3. **Memory-Bounded Parallel Streaming Hashing**: Enhanced multi-pass duplicate detection:
   - **Pass 1**: Group files by exact byte size with physical **Inode / fileKey** deduplication (avoids double-counting hardlinks).
   - **Pass 2**: **Head + Tail (Two-Sided 4 KB)** fast boundary hash (`FileChannel`). Reads only file boundaries to eliminate >99.5% of false size collisions.
   - **Pass 3**: Parallel off-heap **128 KB Direct ByteBuffer `FileChannel`** SHA-256 streaming verification across CPU cores. Large files are never allocated into the Java heap.
4. **Real-Time Throughput Telemetry**: Dynamic I/O bandwidth (`MB/s`) and traversal rate (`files/s`) tracked in real time.
5. **Developer Artifacts & Stale File Filters**: Preset recognition for developer dependencies (`node_modules`, `target`, `.gradle/caches`, `.venv`) and stale cold-storage files (> 1 year).
6. **Safe Deletion & Native Trash**: Deletions attempt delegation to `java.awt.Desktop.getDesktop().moveToTrash(file)` to allow file restoration. Hard deletions require explicit confirmation.
7. **Zero UI-Thread Freezes**: All disk walking, hashing, and heavy I/O operations execute asynchronously on background threads via Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`). Progress is reported safely via `Platform.runLater()`.
8. **Local Persistence**: Embedded SQLite database (`cleanpilot.db`) with Spring Data JPA for historical scan audits and recovered space tracking.

---

## 🏗️ Architecture Overview

```
src/main/java/com/cleanpilot/
├── CleanPilotApplication.java        # Spring Boot main application entrypoint
├── CleanPilotFxApp.java              # JavaFX Application bootstrap & lifecycle orchestrator
├── config/
│   └── AppConfig.java                # Virtual Threads executor & desktop capabilities configuration
├── event/
│   └── StageReadyEvent.java          # Event published when primary JavaFX Stage is initialized
├── model/
│   ├── DrivePartitionInfo.java       # Physical disk partitions & capacity telemetry
│   ├── DuplicateFileGroup.java       # Groups of identical files sharing SHA-256 hashes
│   ├── ProcessInfo.java              # CPU %, RAM (RSS), disk I/O, and blocker impact badges
│   ├── ScanHistoryEntity.java        # JPA entity for scan and cleanup audits
│   ├── ScannedFileItem.java          # File model with category and size metadata
│   └── SystemResourceMetrics.java    # CPU load % and RAM stats
├── repository/
│   └── ScanHistoryRepository.java    # Spring Data JPA repository with SQLite
├── service/
│   ├── DuplicateDetectionService.java# Multi-pass streaming duplicate hunter
│   ├── FileDeletionService.java      # Safe native trash & permanent deletion service
│   ├── LargeFileScannerService.java  # NIO.2 recursive large file scanner
│   ├── ProcessMonitoringService.java # Real-time CPU, RAM, and blocker process inspector
│   ├── ScanHistoryService.java       # Historical audit log management
│   ├── SystemMetricsService.java     # Hardware telemetry provider via OSHI
│   └── UserPreferencesService.java   # Local preferences (thresholds, exclusions, themes)
├── controller/
│   ├── DashboardViewController.java  # Real-time drive gauges, CPU/RAM stats, blocker alerts
│   ├── DuplicatesViewController.java # Hierarchical TreeTableView duplicate manager
│   ├── HistoryViewController.java    # Audit history table & statistics
│   ├── LargeFilesViewController.java # Filterable, virtualized TableView large file analyzer
│   ├── MainViewController.java       # Shell navigation rail and AtlantaFX theme toggle
│   ├── ProcessMonitorViewController.java # Active process table with sorting, filtering & kill action
│   └── SettingsViewController.java   # Theme switcher, threshold spinner, exclusion manager
└── ui/
    ├── FormatUtils.java              # Standard cross-locale byte, duration, and date formatting
    ├── SpringFXMLLoader.java         # Spring-aware FXML loader
    ├── StageInitializer.java         # Primary stage configuration
    └── ThemeManager.java             # AtlantaFX Primer Dark / Light theme manager
```

---

## 🚀 Getting Started

### Prerequisites
- **Java 21+** (Eclipse Adoptium / Temurin 21 recommended)
- **Gradle 8.11+** (bundled via `./gradlew`)

### Running the Application

```powershell
# Development run
./gradlew run
# or
./gradlew bootRun
```

### Running the Test Suite

```powershell
./gradlew test
```

### Building the Executable JAR

```powershell
./gradlew bootJar
```
The fat executable JAR is generated in `build/libs/cleanpilot-1.0.0.jar`.

### Packaging Native Installer (Windows MSI)

```powershell
./gradlew packageMsi
```
Leverages `jpackage` to bundle a self-contained native desktop installer.
