# CleanPilot — Production Readiness Baseline

**Date**: October 23, 2026  
**Application**: CleanPilot Desktop  
**Target Environment**: Windows 10/11 x64/ARM64  
**Policy**: Strict Zero-Telemetry (100% Local-First)

---

## 1. Runtime & Technology Stack Baseline

| Component | Documented Spec | Actual Implementation | Verification Method | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Java Platform** | Java 21 LTS | OpenJDK 21.0.5 | `build.gradle.kts` toolchain & virtual thread execution | **Verified** |
| **GUI Framework** | JavaFX 21 + AtlantaFX | OpenJFX 21.0.5 + AtlantaFX 2.0.1 | 16 FXML layout documents, themes Primer Light & Nord Dark | **Verified** |
| **DI Container** | Spring Boot 3 | Spring Boot 3.3.5 | Lightweight desktop DI, `@Service`, `@Repository`, zero web ports | **Verified** |
| **Persistence** | SQLite 3 | SQLite 3.47.1 + Hibernate 6.6.1 | Embedded SQL at `~/.cleanpilot/cleanpilot.db` (MSI-safe) | **Verified** |
| **Hardware / OS** | OSHI + JNA | OSHI 6.6.5 + JNA 5.14.0 | Kernel32, Advapi32, Psapi, ntdll & WMI fallbacks | **Verified** |
| **Build & Distribution** | Gradle 8 | Gradle 8.11.1 (Kotlin DSL) | `bootJar` fat JAR, `packageAppImage`, `packageMsi` | **Verified** |

---

## 2. Telemetry & Network Activity Audit

CleanPilot enforces a **strict Zero-Telemetry requirement**:
1. **No Outbound Analytics**: 0 telemetry endpoints, 0 analytics SDKs, 0 crash-report pings.
2. **Local Logging Only**: Logs stored exclusively in local temporary/application folders.
3. **No Credential/Path Leakage**: Passwords, auth tokens, and raw sensitive command lines are never exposed in log output.
4. **Offline First**: All threat hunting, duplicate hashing, AppX auditing, and system diagnostics run completely offline without network dependence.

---

## 3. Subsystem Implementation Inventory

| Subsystem | Primary Service | Primary Controller | State | Permissions |
| :--- | :--- | :--- | :--- | :--- |
| **1. Rogue Threat Hunter** | `RogueProcessThreatService` | `ThreatHunterViewController` | **Verified** | Standard User (Read) / Elevated (Action) |
| **2. Process Monitor** | `ProcessMonitoringService` | `ProcessMonitorViewController` | **Verified** | Standard User |
| **3. Live Dashboard** | `SystemMetricsService` | `DashboardViewController` | **Verified** | Standard User |
| **4. Laptop Health Hub** | `LaptopDiagnosticsService` | `LaptopDiagnosticsViewController` | **Verified** | Standard User |
| **5. Hardware & Drivers** | `HardwareDriverService` | `DriversViewController` | **Verified** | Standard User |
| **6. Large Files Analyzer** | `LargeFileScannerService` | `LargeFilesViewController` | **Verified** | Standard User |
| **7. Duplicate Files Finder** | `DuplicateDetectionService` | `DuplicatesViewController` | **Verified** | Standard User |
| **8. Recuperate & Trash** | `SystemTrashReaderService`, `DeepRecoveryScannerService` | `RecuperateController` | **Verified** | Standard User |
| **9. Developer Cleaner** | `DeveloperCleanupService` | `DeveloperCleanerViewController` | **Verified** | Standard User / Elevated for WSL compact |
| **10. History & Audit** | `ScanHistoryService` | `HistoryViewController` | **Verified** | Standard User |
| **11. Startup Optimizer** | `StartupOptimizerService` | `StartupOptimizerViewController` | **Verified** | Standard User (HKCU) / Admin (HKLM) |
| **12. App Uninstaller** | `AppUninstallerService` | `AppUninstallerViewController` | **Verified** | Standard User / Native Uninstaller |
| **13. System Repair & WinSxS** | `SystemIntegrityRepairService`, `WindowsComponentStoreService` | `WindowsSystemRepairViewController` | **Verified** | Administrator (Elevated via UAC) |
| **14. AppX Debloater** | `WindowsAppxDebloaterService` | `WindowsSystemRepairViewController` | **Verified** | User (Current) / Elevated (All Users) |
| **15. Network Optimizer** | `NetworkOptimizationService` | `NetworkOptimizerViewController` | **Verified** | Standard User (DNS) / Admin (Winsock) |
| **16. System Tray & Sentinels** | `SystemTrayManagerService`, `StorageSentinelService` | Tray Hook | **Verified** | Background Thread |
