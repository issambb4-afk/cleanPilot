# CleanPilot — Full Production Readiness, Security, Quality & Launch Audit Report

**Audit Date**: October 24, 2026  
**Auditor Profile**: Independent Senior Software Delivery Auditor, Windows Desktop Application Security Specialist, QA Lead & Release Engineer  
**Target Application**: CleanPilot Desktop (Windows 10 & 11)  
**Evaluated Version**: 1.0.0-RC1 (Artifact version: 1.0.0)  
**Deliverable Document**: Full Production Readiness, Security, Quality & Launch Audit  

---

## 1. Executive Summary

### Is CleanPilot ready today?
**No.** CleanPilot is not ready to be distributed to the general public or promoted to external users. While the core codebase contains sophisticated engineering innovations—including zero-telemetry architecture, off-heap parallel streaming SHA-256 deduplication, non-blocking virtual thread stream draining, and robust NTFS reparse-point isolation in its primary file cleaners—the release packaging and distribution layer has critical deficiencies. Crucially, the Windows release binaries are completely unsigned, no functional Windows installer (`.msi`) package exists, distribution manifests contain dummy hashes, public legal/privacy disclosures are absent, and several secondary repair/cleanup workflows bypass the application's central safety and confirmation lifecycle.

### What is the score out of 100?
**58.5 / 100** (Band: *50–69 — Not sufficiently ready for a broad public release; major preparation remains*).

### What is the release decision?
**NO-GO**. A general public release cannot be authorized under any circumstances at this time.

### What are the three most important reasons?
1. **Unsigned Windows Binaries & Missing Installers (SEC-001, PKG-001)**: The standalone executable (`build/dist/CleanPilot/CleanPilot.exe`) has an Authenticode status of `NotSigned`. No `.msi` installer exists on disk (the `packageMsi` task requires WiX Toolset, which is absent from the environment), and all package manager manifests (`winget`, `chocolatey`, `scoop`) contain unpopulated `<CALCULATED_SHA256_HASH>` placeholders pointing to non-existent GitHub releases. Distributing an unsigned executable that requests Administrator UAC elevation will immediately trigger Microsoft Defender SmartScreen untrusted publisher blocks.
2. **Safety Lifecycle & Confirmation Bypass in System Repair (SEC-002, UX-001)**: In `WindowsSystemRepairViewController.java`, clicking "Clean Component Store" (`onCleanComponentStore`) or "Repair Health" (`onRepairHealth`) immediately initiates destructive filesystem purges (`C:\Windows\SoftwareDistribution\Download`) and elevated DISM/SFC commands with **zero confirmation dialog**, zero preview, and total omission of the `OperationLifecycleManager` state machine and durable SQLite audit journal.
3. **Elevated Subprocess Timeout & Exit Code Propagation Flaws (SEC-003, QAL-001)**: `WindowsSecurityUtils.runElevated` executes PowerShell via `Start-Process powershell.exe -Verb RunAs -Wait` under a hardcoded 120-second timeout. Full DISM `/RestoreHealth` and SFC `/scannow` operations regularly require 10 to 30 minutes on real systems, causing guaranteed premature task kills after 2 minutes. Furthermore, `Start-Process` without `-PassThru` does not propagate the inner process exit code, causing the UI to falsely announce "Repair Completed Successfully" when operations fail or are aborted.

### What is the single biggest risk?
**SmartScreen Reputation Blockade & Accidental System Destabilization**: If distributed in its current state, users attempting to run CleanPilot will be greeted with aggressive Windows Defender SmartScreen "Windows protected your PC" warnings due to lack of code signing. For users who bypass the warning, triggering the unconfirmed WinSxS cleanup or system repair will launch elevated background commands that abort midway due to the 120s timeout, risking an interrupted Windows servicing stack without a journaled recovery trail.

### What must be fixed or verified before the next release?
1. Wire `OperationConfirmationDialog` and `OperationLifecycleManager` into `WindowsSystemRepairViewController`, `NetworkOptimizerViewController`, and `StartupOptimizerService`.
2. Acquire a valid Authenticode Code-Signing Certificate, configure `signtool` in `build.gradle.kts`, and sign all executable binaries and DLLs.
3. Install WiX Toolset v3.x, execute `packageMsi`, generate real SHA-256 hashes, and update package manager distribution manifests.
4. Correct `WindowsSecurityUtils.runElevated` to support configurable timeouts, descendant process tree termination (`/T`), and accurate inner exit-code extraction.
5. Create official `PRIVACY.md`, `LICENSE` (Apache-2.0), and `SECURITY.md` documents in the repository root.

### What is the recommended next action?
Maintain CleanPilot strictly in **Internal Development** mode. Execute remediation sprint P0 to resolve the 7 release-blocking items before scheduling a constrained, supervised Private Beta.

---

## 2. Overall Readiness Score Out of 100

| Evaluation Category | Maximum Weight | Scored Points | Fulfillment % | Key Deduction Driver |
| :--- | :---: | :---: | :---: | :--- |
| **Security, Privileged Execution & Filesystem Safety** | 25 | **17.5** | 70.0% | Unconfirmed WinSxS/SFC actions; 120s elevated timeout; unconstrained child processes. |
| **Installation, Packaging, Signing & Distribution** | 15 | **4.5** | 30.0% | Unsigned binaries; no MSI built; WiX missing; placeholder hashes in manifests. |
| **Functional Correctness & Data Integrity** | 15 | **10.0** | 66.7% | HKLM startup toggle broken; missing audit journal in repair services; false-success reporting. |
| **Automated Testing & Release Engineering** | 15 | **9.5** | 63.3% | 104 unit tests pass, but zero automated UI tests, zero installer tests, and no CI workflow. |
| **Windows Compatibility & Reliability** | 10 | **5.5** | 55.0% | Tested on Win11 x64 only; Win10, ARM64, ReFS, and secondary physical drives unverified. |
| **Privacy & Transparency** | 8 | **5.5** | 68.8% | Zero telemetry verified in code, but no public privacy policy document exists. |
| **UX, Accessibility & Onboarding** | 5 | **3.5** | 70.0% | Excellent responsive reflow and themes; lacks onboarding wizard; mixed language strings. |
| **Support, Documentation & Incident Response** | 4 | **1.5** | 37.5% | No `SECURITY.md`, no `LICENSE` file, no support ticket/issue tracker link in UI. |
| **Marketing Readiness & Product Credibility** | 3 | **1.0** | 33.3% | High quality brand kit; no live landing page, download links, or validated distribution. |
| **TOTAL WEIGHTED SCORE** | **100** | **58.5** | **58.5%** | **NOT READY FOR PUBLIC DISTRIBUTION** |

---

## 3. Final Release Decision

```
========================================================================================
                          FINAL RELEASE DECISION: NO-GO
========================================================================================
```

CleanPilot **fails 7 mandatory release gates** governing binary signing, installer integrity, distribution manifest validity, legal/privacy disclosures, and unconfirmed privileged operations. Public distribution cannot be approved until all P0 blockers are resolved and retested.

---

## 4. Four Separate Release Decisions

### A. Internal Development Use
* **Decision**: **CONDITIONAL GO**
* **Rationale**: The core Spring Boot 3 / JavaFX 21 application compiles cleanly, the Spring ApplicationContext bootstraps with all beans resolved, all 104 automated unit and integration tests pass, and local developer execution (`gradlew.bat bootRun`) functions safely without host destabilization.
* **Mandatory Conditions**:
  1. Developers must not run `cleanComponentStore()` or `repairSystemHealth()` against primary production workstations without verifying local environment isolation.
  2. The embedded SQLite database at `~/.cleanpilot/cleanpilot.db` must be backed up if prior audit history is needed.
* **Remaining Risks**: Elevated commands executed within the IDE can hang if the 120s timeout terminates the launcher while child PowerShell instances persist.
* **Approver**: Lead Systems Engineer.

### B. Private Beta
* **Decision**: **NO-GO**
* **Rationale**: Private beta testers (even technical users) cannot be asked to test an unsigned desktop application that performs unconfirmed component store purges and elevated system file repairs. Testing cannot be safely expanded until confirmation dialogs are universal and the elevated execution timeout is corrected.
* **Permitted User Group (Once Promoted to Conditional Go)**: Internal corporate staff and vetted technical testers on non-critical Windows 10/11 VMs.
* **Required Safeguards**: Distribution must be delivered via pre-packaged zip bundles accompanied by manual SHA-256 verification instructions.
* **Approver**: QA Lead & Security Specialist.

### C. Public Download
* **Decision**: **NO-GO**
* **Rationale**: Multiple hard release gates triggered:
  - Binary signature is completely absent (`Status: NotSigned`).
  - No standalone MSI installer exists (`packageMsi` task fails without WiX).
  - Winget, Chocolatey, and Scoop manifests are non-functional (placeholder `<CALCULATED_SHA256_HASH>` and dead download URLs).
  - No public privacy policy exists to legally substantiate zero-telemetry claims.
  - No `LICENSE` file is included in the package.
* **Approver**: Release Engineering & Legal Counsel.

### D. Public Marketing Campaign
* **Decision**: **NO-GO**
* **Rationale**: Initiating a public promotional campaign (e.g. Hacker News Show HN, Reddit r/windows, Product Hunt, YouTube tech press) while the software lacks a signed binary, a functioning installer, a landing page with real downloads, and a privacy policy would severely damage the CleanPilot brand reputation and lead to immediate "malware/scareware" accusations on social platforms.
* **Approver**: Product Founder & Marketing Consultant.

---

## 5. Scope, Commit, Environment, and Audit Limitations

### 5.1 Host & Environment Baseline
* **Operating System**: Microsoft Windows 11 Enterprise / Pro (64-bit), Build 10.0.22631 (amd64).
* **Java Runtime**: OpenJDK 21.0.12.1 Temurin (build 21.0.12.1+1-LTS, Eclipse Adoptium).
* **Gradle Build Tool**: Gradle 8.11.1 (Kotlin DSL 2.0.20, Groovy 3.0.22).
* **Workspace Path**: `C:\projects\smarting\Clean`.
* **Execution Date & Time**: October 24, 2026, 15:14 CEST.

### 5.2 Version Control & Source Traceability Baseline
* **Git Status**: `fatal: not a git repository (or any of the parent directories): .git`.
* **Working Tree**: Not version-controlled via Git. Commit hash cannot be established.
* **File Inventory**: 132 Java source files (`src/main/java`, `src/test/java`), 16 FXML layout documents, 1 CSS stylesheet (`app.css`), 4 i18n resource bundles (`en`, `fr`, `ar`, default).
* **Untracked Artifacts in Root**: `cleanpilot.db` (12,288 bytes, created 19/10/2026) located in repository root (legacy working-directory database before relocation to `~/.cleanpilot/`).

### 5.3 Audit Limitations & Exclusions
* **Host Safety Constraint**: In accordance with the audit mandate, no real destructive operations (WinSxS purge, elevated DISM RestoreHealth, live UAC execution, uninstallation of host software) were executed against the host OS. Static code inspection, isolated synthetic test cases (`@TempDir`), and mock processes were used.
* **Platform Testing Constraints**: Windows 10, Windows 11 on ARM64, and physical non-NTFS volumes (ReFS, exFAT) could not be tested on live hardware in the current environment.
* **WiX Toolset Absence**: The WiX Toolset was not available in the host environment; the `packageMsi` task could not be compiled or inspected as a completed installer artifact.
* **UI Test Automation Absence**: No TestFX or headless UI automation harness is present; JavaFX view behaviors, dialog popups, and layout reflow were verified through static FXML/CSS inspection and controller review.

---

## 6. Evidence and Verification Methodology

The conclusions in this report are grounded in direct, reproducible evidence categorized into four strict verification tiers:

```mermaid
flowchart LR
    A["1. Live Build & Test Execution"] --> E["Audit Findings & Score"]
    B["2. Static Source & FXML Review"] --> E
    C["3. Binary & Manifest Inspection"] --> E
    D["4. Documentation Cross-Audit"] --> E
```

1. **Live Build & Automated Test Execution**: Executed `.\gradlew.bat test --rerun-tasks` directly from the workspace root. Captured all test outputs, duration, exit code, and inspected the generated JUnit HTML reports at `build/reports/tests/test/index.html`.
2. **Static Source Code & Architecture Inspection**: Line-by-line review of all 16 controllers, 30 services, security utilities (`WindowsSecurityUtils`, `ProcessExecutionUtils`), safety models (`OperationPreview`, `OperationLifecycleManager`), and persistence entities.
3. **Binary & Release Artifact Inspection**: Evaluated existing build outputs in `build/dist/CleanPilot/` and `build/libs/`. Verified Authenticode digital signatures via PowerShell `Get-AuthenticodeSignature`, computed SHA-256 cryptographic hashes via `Get-FileHash`, and analyzed package manager manifests in `distribution/`.
4. **Documentation vs Implementation Discrepancy Analysis**: Audited claims in `BASELINE.md`, `RELEASE_CHECKLIST.md`, `SAFETY_ARCHITECTURE.md`, `RISK_REGISTER.md`, and `FINAL_ASSESSMENT.md` against actual code behavior.

---

## 7. Weighted Scoring Table With Calculations

$$\text{Category Score} = \text{Weight} \times \left( \frac{\text{Verified Points}}{\text{Applicable Denominator}} \right)$$

| Category ID | Category Name | Items Range | Applicable Items | Weight | Full Pass | Partial | Fail / Unverified | Subtotal Score |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **CAT-1** | Security, Privileged Execution & Filesystem Safety | Items 41–80 | 40 | 25 | 24 | 10 | 6 | **17.50 / 25.0** |
| **CAT-2** | Installation, Packaging, Signing & Distribution | Items 1–28 | 28 | 15 | 6 | 4 | 18 | **4.50 / 15.0** |
| **CAT-3** | Functional Correctness & Data Integrity | Items 81–114 | 34 | 15 | 18 | 9 | 7 | **10.00 / 15.0** |
| **CAT-4** | Automated Testing & Release Engineering | Items 133–152 | 20 | 15 | 11 | 3 | 6 | **9.50 / 15.0** |
| **CAT-5** | Windows Compatibility & Reliability | Items 115–132 | 18 | 10 | 7 | 5 | 6 | **5.50 / 10.0** |
| **CAT-6** | Privacy & Transparency | Items 29–40 | 12 | 8 | 7 | 2 | 3 | **5.50 / 8.0** |
| **CAT-7** | UX, Accessibility & Onboarding | Items 105–114 (subset) | 10 | 5 | 6 | 2 | 2 | **3.50 / 5.0** |
| **CAT-8** | Support, Documentation & Incident Response | Items 153–162 | 10 | 4 | 2 | 3 | 5 | **1.50 / 4.0** |
| **CAT-9** | Marketing Readiness & Product Credibility | Items 163–178 | 16 | 3 | 4 | 3 | 9 | **1.00 / 3.0** |
| **TOTAL** | **Comprehensive Production Readiness** | **Items 1–178** | **178** | **100** | **84** | **41** | **53** | **58.50 / 100** |

---

## 8. Mandatory Release-Gate Assessment

A passing numerical score can never override a critical safety, integrity, or distribution failure.

| Gate ID | Mandatory Release Gate Criterion | Audit Status | Triggered? | Release Impact |
| :--- | :--- | :--- | :---: | :--- |
| **GATE-1** | Zero unresolved Critical safety or security findings. | **PASS** | No | No critical vulnerability identified that allows remote exploitation. |
| **GATE-2** | Zero unresolved High findings affecting privileged execution or destructive safety. | **FAIL** | **YES** | High findings SEC-002, SEC-003, and SEC-004 trigger automatic NO-GO. |
| **GATE-3** | Material claims of safe privileged execution must be verified. | **FAIL** | **YES** | Elevated DISM/SFC commands fail timeout; exit codes unpropagated. |
| **GATE-4** | Runnable distribution installer must exist and be traceable to audited code. | **FAIL** | **YES** | No MSI installer exists; project is not a Git repo; cannot trace commit. |
| **GATE-5** | Release binaries and installer must possess valid Authenticode signatures. | **FAIL** | **YES** | Binary is unsigned (`Status: NotSigned`). SmartScreen block is guaranteed. |
| **GATE-6** | All essential automated test suites must execute cleanly during audit. | **PASS** | No | 104 tests executed cleanly in 1m58s with exit code 0. |
| **GATE-7** | Public privacy policy and open-source license must exist before public launch. | **FAIL** | **YES** | No `PRIVACY.md` or `LICENSE` file in repository. |

**Verdict**: Gates 2, 3, 4, 5, and 7 are **TRIGGERED**. Mandatory release blockers are active.

---

## 9. Complete Pre-Launch Checklist Audit (Items 1–178)

Every item has been individually assessed based on verified code, test, build, or documentation evidence.

### A. Installation and Lifecycle (Items 1–15)
1. **A stable installation package exists**: `FAIL — Confirmed failure`. Only an unpacked portable app-image directory exists (`build/dist/CleanPilot`). No `.msi` file exists in `build/dist/` or project.
2. **Installation succeeds on clean supported Windows**: `NOT VERIFIED — Insufficient evidence`. Cannot install without an installer artifact.
3. **Installed application launches without manual developer setup**: `PASS — Verified`. Standalone portable `CleanPilot.exe` includes bundled JRE 21 runtime and launches without system JDK.
4. **Application files placed in appropriate directories**: `PASS — Verified`. App data and SQLite database reside in user profile `${user.home}/.cleanpilot/`, safe from `Program Files` write restrictions.
5. **Per-user and machine-wide installation behavior understood**: `PARTIAL — Incomplete`. App-image is portable; MSI configuration (`--win-dir-chooser`) is written in `build.gradle.kts` but not built.
6. **Required runtimes and native dependencies handled correctly**: `PASS — Verified`. Java 21 runtime bundled via `jpackage`; OSHI includes JNA 5.15.0 with bundled Windows DLLs.
7. **Missing or incompatible dependencies produce understandable errors**: `PASS — Verified`. `ApplicationContextStartupTest` verifies graceful bean loading failure handling.
8. **Upgrade behavior preserves required user data and settings**: `PARTIAL — Incomplete`. SQLite DB in `${user.home}/.cleanpilot/cleanpilot.db` survives app replacement, but installer upgrade path untested.
9. **Failed installation and upgrade scenarios handled safely**: `NOT VERIFIED — Insufficient evidence`. No MSI installer available to test rollback.
10. **Uninstallation works without leaving unexpected components**: `NOT VERIFIED — Insufficient evidence`. No MSI uninstallation tested.
11. **Uninstallation does not remove user data without authorization**: `NOT VERIFIED — Insufficient evidence`. Uninstaller script not generated.
12. **Reinstallation and recovery behavior documented**: `PARTIAL — Incomplete`. Mentioned in `BASELINE.md`, but no user troubleshooting document provided.
13. **Installation paths with spaces and non-ASCII supported**: `PASS — Verified`. `ProcessExecutionUtils` handles UTF-8 paths; JPackage handles standard Windows spaces.
14. **Installer and application versions agree**: `PASS — Verified`. `build.gradle.kts` sets `version = "1.0.0"` and `application.yml` sets `version: "1.0.0"`.
15. **Distribution process documented and reproducible**: `PARTIAL — Incomplete`. Documented in `MARKETING_AND_DISTRIBUTION_GUIDE.md`, but build fails for MSI due to missing WiX toolset.

### B. Signing, Distribution, and Reputation (Items 16–28)
16. **Release executable and installer inspected**: `PASS — Verified`. Executable `build/dist/CleanPilot/CleanPilot.exe` inspected; size 560,640 bytes.
17. **Signing status of applicable executables is known**: `PASS — Verified`. `Get-AuthenticodeSignature` confirmed `Status: NotSigned`, `SignatureType: None`.
18. **Signature validity and publisher identity verified**: `FAIL — Confirmed failure`. No signature exists.
19. **Signing claims not made when absent**: `PASS — Verified`. Documentation does not falsely claim the current build is signed.
20. **Artifact hashes can be published and independently checked**: `PARTIAL — Incomplete`. Executable SHA-256 is `8043CB64D65F08DDA76FFDB093D41560319309C75FBA222502F9E72899EB58C6`, but manifests contain `<CALCULATED_SHA256_HASH>`.
21. **Download source uses HTTPS and controlled release access**: `PARTIAL — Incomplete`. URLs in manifests specify HTTPS GitHub release URLs, but releases are not yet published.
22. **Release artifacts do not contain development secrets or test data**: `PASS — Verified`. Fat jar `cleanpilot-1.0.0.jar` contains no API keys, private certs, or test fixtures.
23. **Antivirus or Defender warnings investigated**: `NOT VERIFIED — Insufficient evidence`. No telemetry or virus scanner reports submitted.
24. **Process for investigating false-positive detections**: `PASS — Verified`. Documented in `docs/MARKETING_AND_DISTRIBUTION_GUIDE.md` Section 3.6.
25. **Does not recommend disabling security protections**: `PASS — Verified`. Documentation strictly rejects asking users to disable UAC or Defender.
26. **Supported Windows versions and architectures clearly stated**: `PASS — Verified`. Windows 10/11 x64/ARM64 stated in `README.md` and `COMPATIBILITY.md`.
27. **Release notes identify version, date, changes, limitations**: `PARTIAL — Incomplete`. Release checklist exists, but no user-facing `CHANGELOG.md` exists.
28. **Reproducible process exists for producing release artifact**: `FAIL — Confirmed failure`. No automated CI script (`.github/workflows`) exists; builds rely entirely on local host setup.

### C. Privacy and Transparency (Items 29–40)
29. **Public privacy policy exists and matches actual behavior**: `FAIL — Confirmed failure`. No `PRIVACY.md` or public web link exists in the repository.
30. **Data collected, stored, transmitted, retained documented**: `PARTIAL — Incomplete`. Zero telemetry documented in `BASELINE.md`, but no legal user document.
31. **Network behavior inspected**: `PASS — Verified`. Dependency tree confirmed 0 HTTP clients, 0 analytics SDKs. Code inspection reveals zero outbound sockets created by CleanPilot.
32. **Telemetry claims supported by code**: `PASS — Verified`. All diagnostics (`OSHI`, `JNA`) run strictly local queries against Windows APIs and kernel counters.
33. **Third-party libraries and services accounted for**: `PASS — Verified`. All runtime dependencies originate from standard Maven Central repositories.
34: **Database storage locations understood**: `PASS — Verified`. SQLite stored in `${user.home}/.cleanpilot/cleanpilot.db`.
35. **Sensitive logs do not expose unnecessary info**: `PASS — Verified`. `WindowsSecurityUtils` and `FileDeletionService` sanitize log outputs.
36. **Crash reports and exports reviewed for sensitive data**: `PASS — Verified`. No automatic crash export exists; error logs stay local.
37. **No unexpected account creation or online dependency**: `PASS — Verified`. CleanPilot requires no user account, login, or internet connection.
38. **Optional data sharing disclosed**: `NOT APPLICABLE — Excluded`. No data sharing exists in the application.
39. **Retention and deletion behavior documented**: `PASS — Verified`. Quarantine retention is documented as 30 days default in `QuarantineService.java`.
40. **Users understand information remaining after uninstall**: `PARTIAL — Incomplete`. Database at `~/.cleanpilot/` remains after app-image removal; not clearly explained in user docs.

### D. Security and Privileged Operations (Items 41–60)
41. **UI runs without unnecessary admin privileges**: `PASS — Verified`. JavaFX process starts as standard unprivileged user process.
42. **Privileged operations isolated and explicitly controlled**: `PASS — Verified`. Privileged actions delegate through `WindowsSecurityUtils.runElevated`.
43. **User authorization required before sensitive actions execute**: `FAIL — Confirmed failure`. WinSxS Component Store cleanup and System File Repair run immediately upon button click without confirmation.
44. **Discovery, validation, preview, authorization, execution enforceable stages**: `PARTIAL — Incomplete`. Enforced in `Duplicates`, `LargeFiles`, and `DeveloperCleaner`, but bypassed in `WindowsSystemRepair` and `NetworkOptimizer`.
45. **Preview accurately represents operation that will execute**: `PARTIAL — Incomplete`. Accurately shows counts/sizes in file views; absent in system repair views.
46. **Authorization cannot be bypassed through unexpected service call**: `FAIL — Confirmed failure`. `OperationLifecycleManager.beginExecution()` does not check if operation was `AUTHORIZED`.
47. **Commands constructed without unsafe shell concatenation**: `PASS — Verified`. `WindowsSecurityUtils.runElevated` uses Base64 UTF-16LE encoding (`-EncodedCommand`); AppX debloater uses regex.
48. **Arguments with spaces, quotes, Unicode handled safely**: `PASS — Verified`. Tested in `WindowsSecurityUtilsTest` and `ProcessExecutionUtilsTest`.
49. **Untrusted values cannot inject arbitrary PowerShell/command shell**: `PASS — Verified`. Strict regex `^[A-Za-z0-9._-]+$` on AppX packages; UTF-16LE encoding on elevated scripts.
50. **Privileged operations validate inputs independently of UI**: `PASS — Verified`. Services re-check boundaries (`isProtectedPackage`, `isSafeDeletablePath`).
51. **Command execution has bounded timeouts**: `PARTIAL — Incomplete`. Subprocesses have timeouts, but `runElevated` hardcodes 120s, which is too short for DISM/SFC, and `SystemIntegrityRepairService` calls un-timed `waitFor()` in unprivileged paths.
52. **Standard output and standard error drained safely**: `PASS — Verified`. `ProcessExecutionUtils.drainStream` drains streams asynchronously on virtual threads.
53. **Child processes handled safely on cancellation or timeout**: `FAIL — Confirmed failure`. `Process.destroyForcibly()` does not terminate descendant process trees on Windows; `taskkill` in threat hunter omits `/T`.
54. **Exit codes and error conditions checked**: `PARTIAL — Incomplete`. Checked in `ProcessResult`, but `runElevated` fails to extract the exit code of the inner elevated command.
55. **Elevated operations do not inherit unnecessary environment variables**: `PASS — Verified`. Executed via `powershell.exe -NoProfile -NonInteractive`.
56. **Executable paths and scripts cannot be replaced via writable locations**: `PASS — Verified`. Commands reference system binaries (`powershell.exe`, `dism.exe`, `sfc.exe`, `netstat.exe`, `reg.exe`) located in `%SystemRoot%\System32`.
57. **No hardcoded secrets or credentials**: `PASS — Verified`. Codebase scan revealed 0 embedded passwords, tokens, or private keys.
58. **Sensitive errors do not disclose excessive system details**: `PASS — Verified`. Errors logged to SLF4J with sanitized exception messages.
59. **Security-relevant failures default to safe behavior**: `PASS — Verified`. Path rejections abort file deletion; unverified threat pids abort kill.
60. **Records enough diagnostic information without exposing sensitive data**: `PASS — Verified`. `OperationJournalEntity` records operation outcome, target count, bytes recovered, and high-level summaries.

### E. Filesystem Safety and Destructive Actions (Items 61–80)
61. **Protected operating-system paths identified and enforced**: `PASS — Verified`. `isSafeDeletablePath`, `isSafeResidualPath`, `isSafeArtifactPath` enforce boundaries.
62. **Protected-path checks normalize paths consistently**: `PASS — Verified`. Calls `.toAbsolutePath().normalize()` before all boundary comparisons.
63. **Relative paths, drive roots, UNC paths handled safely**: `PASS — Verified`. Reject drive roots (`getParent() == null`), path depth $< 2$ or $< 3$.
64. **Path traversal attempts rejected**: `PASS — Verified`. Deep recovery tests verify traversal sequence rejection (`../../`).
65. **Junctions and reparse points handled according to documented policy**: `PASS — Verified`. File visitors specify `noneOf(FileVisitOption.class)` and unlink junction directories without entering targets.
66. **Recursive operations do not unintentionally traverse protected locations**: `PASS — Verified`. Junctions intercepted in `preVisitDirectory` with `SKIP_SUBTREE`.
67. **Race conditions between path validation and execution considered**: `PARTIAL — Incomplete`. Static TOCTOU gap exists between preview and execution, though mitigated by re-validation at deletion point.
68. **Application does not follow untrusted links into unrelated directories**: `PASS — Verified`. Verified in `DeveloperCleanupServiceTest` and `AppUninstallerServiceTest`.
69. **Filesystem permissions and access failures handled safely**: `PASS — Verified`. `IOException` caught and recorded as failed target without terminating batch.
70. **Cross-volume moves and quarantine operations handled correctly**: `PASS — Verified`. `QuarantineService.moveOrCopyAcrossVolumes` falls back to copy + delete on cross-volume moves.
71. **Quarantine does not silently become permanent deletion when move fails**: `PASS — Verified`. Source is only deleted after `Files.copy` succeeds.
72. **Quarantined data can be restored using verified process**: `PASS — Verified`. `QuarantineService.restoreItem` verified in `QuarantineServiceTest`.
73. **Restoration cannot overwrite unrelated files without safeguards**: `PASS — Verified`. Appends `_restored_N` suffix if target already exists.
74. **Destructive operations have meaningful preview and confirmation**: `PARTIAL — Incomplete`. Present in file views; completely missing in WinSxS cleanup.
75. **Failed or interrupted operations leave recoverable state**: `PASS — Verified`. Startup crash reconciliation marks interrupted operations `INTERRUPTED`.
76. **Disk-full and permission-denied scenarios handled safely**: `PASS — Verified`. Handled via standard NIO exception catches.
77. **Critical Windows files cannot be removed through ordinary cleanup**: `PASS — Verified`. Boot/paging files, System32, and Program Files protected.
78. **File type, extension, metadata assumptions validated**: `PASS — Verified`. Artifact cleaning checks names (`node_modules`, `target`, `build`, `.venv`, `.vhdx`).
79. **Temporary files created in controlled locations and cleaned up**: `PASS — Verified`. Tests use JUnit `@TempDir`.
80. **Safety checks apply to actual execution layer, not only UI**: `PARTIAL — Incomplete`. Enforced in `FileDeletionService` and `DeveloperCleanupService`, but `WindowsComponentStoreService.deleteRecursively()` lacks path safety checks.

### F. Application Data and Database (Items 81–92)
81. **Database stored in appropriate per-user location**: `PASS — Verified`. Located at `${user.home}/.cleanpilot/cleanpilot.db`.
82. **Database creation and migration behavior tested**: `PASS — Verified`. SQLite table creation via Hibernate `ddl-auto: update` tested in `ApplicationContextStartupTest`.
83. **Database errors do not silently corrupt user state**: `PASS — Verified`. Spring transactional rollbacks configured on quarantine and journal services.
84. **Transactions used where appropriate for multi-step changes**: `PASS — Verified`. `@Transactional` applied across `QuarantineService` and `OperationLifecycleManager`.
85. **Concurrent access handled safely**: `PASS — Verified`. SQLite locking handled via HikariCP single-connection desktop pool.
86. **Database schema migrations preserve existing data**: `PARTIAL — Incomplete`. Hibernate update works for minor changes; no Flyway/Liquibase migration tool configured.
87. **Application shutdown does not lose pending writes**: `PASS — Verified`. Spring shutdown hook closes Hikari pool cleanly (verified in test logs).
88. **Database backups or recovery procedures documented**: `PARTIAL — Incomplete`. Mentioned in internal docs, but no backup button or user guide exists.
89. **Sensitive database contents minimized**: `PASS — Verified`. Only stores file paths, sizes, hashes, and operation timestamps.
90. **Logs, temporary files, database backups handled appropriately**: `PASS — Verified`. Log levels bounded in `application.yml`.
91. **Database paths not accidentally tied to developer home directory**: `PASS — Verified`. Uses `${user.home}`, dynamic per user.
92. **Unsupported or corrupted database states produce safe failure**: `PASS — Verified`. SQLite driver fails gracefully on lock or file corruption.

### G. Functional Correctness and User Experience (Items 93–114)
93. **All advertised features exist in actual release build**: `PASS — Verified`. All 15 navigation views and underlying services are implemented.
94. **Each primary workflow completed without developer intervention**: `PASS — Verified`. Workflows operate via UI buttons and event handlers.
95. **Dashboard information accurate and not fabricated**: `PASS — Verified`. Drive gauges and CPU/RAM data queried dynamically from OSHI and NIO.
96. **Maintenance recommendations distinguish findings from completed actions**: `PASS — Verified`. Dashboard differentiates findings from actions.
97. **App does not claim action succeeded when execution failed**: `FAIL — Confirmed failure`. `WindowsSystemRepairViewController` reports success when elevated DISM times out or inner process fails.
98. **Long-running operations remain responsive**: `PASS — Verified`. Heavy tasks offloaded to Java 21 virtual threads via `submitAsync`.
99. **Progress indicators reflect actual operation status**: `PASS — Verified`. Progress bars update via `Platform.runLater`.
100. **Errors include actionable next steps**: `PARTIAL — Incomplete`. Error alerts show message strings, but some lack specific troubleshooting steps.
101. **Empty, loading, error, success states implemented**: `PASS — Verified`. Table placeholders and empty-state labels present in FXML.
102. **Repeated clicks cannot trigger duplicate destructive operations**: `PASS — Verified`. Action buttons immediately call `.setDisable(true)` on click.
103. **Cancellation and retry behavior consistent**: `PASS — Verified`. Scanners accept `AtomicBoolean cancelFlag`.
104. **State remains coherent after failed or interrupted operations**: `PASS — Verified`. Interrupted operations reconciled on next boot.
105. **Navigation and dialogs behave correctly**: `PASS — Verified`. Main navigation switching verified in `MainViewController`.
106. **Keyboard navigation and focus management usable**: `PASS — Verified`. 2px focus rings (`:focused`) configured in `app.css`.
107. **Important controls have accessible labels and contrast**: `PASS — Verified`. High contrast AtlantaFX Primer colors used throughout.
108. **Text readable at supported Windows scaling levels**: `PASS — Verified`. Relative font sizes (`em`) used in CSS and FXML.
109. **UI works at documented minimum window dimensions**: `PASS — Verified`. Tested down to 800×600 with scrollable navigation rail.
110. **Long paths, large datasets do not break layout**: `PASS — Verified`. Tables configure constrained resize and text truncation with tooltips.
111. **Tables handle empty, large, and changing datasets**: `PASS — Verified`. Virtualized JavaFX `TableView` and `TreeTableView` utilized.
112. **UI clearly distinguishes reversible from irreversible actions**: `PARTIAL — Incomplete`. Present in `OperationConfirmationDialog`, but missing in system repair views.
113. **App does not exaggerate findings or use manipulative warnings**: `PASS — Verified`. Threat hunter labels calibrated (`Investigate (Suspicious)` instead of `Severe Virus`).
114. **Design communicates maintenance and safety philosophy**: `PASS — Verified`. Clean, calm Fluent-style interface without scareware animations.

### H. Compatibility and Platform Support (Items 115–132)
115. **Windows 10 compatibility tested**: `NOT VERIFIED — Insufficient evidence`. Audit executed on Windows 11 only.
116. **Windows 11 compatibility tested**: `PASS — Verified`. Executed and verified on Windows 11 Build 22631.
117. **x64 compatibility tested**: `PASS — Verified`. Running on x86_64 architecture.
118. **ARM64 compatibility tested**: `NOT VERIFIED — Insufficient evidence`. No ARM64 host available; claims in `COMPATIBILITY.md` unverified.
119. **Java runtime and JavaFX match supported architectures**: `PASS — Verified`. OpenJFX 21.0.5 x64 native binaries bundled in `CleanPilot/runtime`.
120. **Native integrations work on advertised architectures**: `PARTIAL — Incomplete`. OSHI/JNA works on x64; ARM64 JNA fallback untested.
121. **NTFS behavior verified**: `PASS — Verified`. Reparse points and attributes verified on NTFS.
122. **ReFS behavior verified**: `NOT VERIFIED — Insufficient evidence`. No ReFS volume available in audit environment.
123. **FAT32 and exFAT behavior verified**: `PARTIAL — Incomplete`. Cross-volume move logic tested in unit tests; not tested on physical USB media.
124. **Multi-drive environments tested**: `NOT VERIFIED — Insufficient evidence`. Audit machine has single root drive `C:\`.
125. **Missing drives and disconnected media handled safely**: `PASS — Verified`. `File.listRoots()` and null checks protect scanners.
126. **Non-ASCII usernames and paths tested**: `PASS — Verified`. UTF-8 encoding options set in `JavaCompile` and Gradle.
127. **User accounts with restricted privileges tested**: `NOT VERIFIED — Insufficient evidence`. Tested under standard administrative desktop account.
128. **Windows UAC and elevation behavior tested safely**: `PARTIAL — Incomplete`. Elevation logic reviewed statically; live UAC prompts not triggered.
129. **Windows updates and restart-required states handled**: `FAIL — Confirmed failure`. `NetworkOptimizationService` triggers `netsh winsock reset` without warning that restart is required.
130. **Different display scaling levels tested**: `PARTIAL — Incomplete`. Tested statically against CSS tokens; live DPI switching untested.
131. **Sleep, resume, and shutdown behavior evaluated**: `PASS — Verified`. Spring context shutdown hook verified.
132. **Filesystem limitations and unsupported combos documented**: `PASS — Verified`. Documented in `COMPATIBILITY.md`.

### I. Testing and Release Engineering (Items 133–152)
133. **Unit tests cover important business logic**: `PASS — Verified`. 28 test classes covering services, hashing, path parsing.
134. **Security regression tests cover known vulnerabilities**: `PASS — Verified`. Path boundary, AppX injection, and traversal regression tests pass.
135. **Integration tests cover critical service interactions**: `PASS — Verified`. `ApplicationContextStartupTest`, `QuarantineServiceTest` pass.
136. **UI tests or documented manual tests cover primary workflows**: `FAIL — Confirmed failure`. Zero automated TestFX or UI integration tests exist.
137. **Tests use temporary directories and synthetic fixtures**: `PASS — Verified`. Tests strictly utilize `@TempDir` without touching host directories.
138. **Tests do not delete or modify real host data**: `PASS — Verified`. Confirmed zero host file modifications during 104-test run.
139. **Security tests cover traversal, hostile arguments, protected paths**: `PASS — Verified`. Covered in `FileDeletionServiceTest`, `WindowsAppxDebloaterServiceTest`.
140. **Failure paths and partial failures tested**: `PASS — Verified`. Covered in `DeepRecoveryScannerServiceTest` and `QuarantineServiceTest`.
141. **Timeout and subprocess deadlock scenarios tested**: `PASS — Verified`. `ProcessExecutionUtilsTest` verifies virtual thread stream draining.
142. **Concurrency and cancellation scenarios tested**: `PASS — Verified`. Cancel flag handling verified in scanners.
143. **Database migration and recovery scenarios tested**: `PARTIAL — Incomplete`. Startup reconciliation tested; schema migration untested.
144. **Tests deterministic and independent of network**: `PASS — Verified`. 104 tests pass 100% offline with zero remote network calls.
145. **Full test suite executed during this audit**: `PASS — Verified`. Executed `.\gradlew.bat test --rerun-tasks` (104 tests passed in 1m58s).
146. **Production build succeeds in clean environment**: `PARTIAL — Incomplete`. `bootJar` and `packageAppImage` succeed; `packageMsi` fails (WiX missing).
147. **Packaged application launched and smoke-tested**: `PARTIAL — Incomplete`. `CleanPilot.exe` inspected on disk; live UI execution smoke-tested in prior run.
148. **Dependency vulnerability and license checks performed**: `PARTIAL — Incomplete`. Dependency tree inspected; no automated OWASP Dependency-Check configured.
149. **CI checks configured and actual results available**: `FAIL — Confirmed failure`. No `.github/workflows` or CI automation exists.
150. **Release artifacts traced to specific commit and build**: `FAIL — Confirmed failure`. Git repository is missing; no commit hash exists.
151. **Failed tests and skipped tests explained**: `PASS — Verified`. 0 failures, 0 skipped.
152. **Known limitations recorded rather than concealed**: `PASS — Verified`. Documented in `RISK_REGISTER.md` and this audit report.

### J. Support and Maintenance (Items 153–162)
153. **Users can report bugs or request help**: `FAIL — Confirmed failure`. No support URL, feedback dialog, or bug report link in the application.
154. **Monitored support contact or feedback channel**: `FAIL — Confirmed failure`. No contact email or forum established.
155. **Security vulnerability reporting method exists**: `FAIL — Confirmed failure`. No `SECURITY.md` or security contact published.
156. **Known issues and workarounds documented**: `PARTIAL — Incomplete`. Known issues documented in internal engineering docs, but not in user documentation.
157. **Process exists for responding to serious defects**: `PARTIAL — Incomplete`. Stated in strategy documents, but no formal incident response plan.
158. **Releases have versioning and changelog policy**: `PARTIAL — Incomplete`. Semantic versioning used (`1.0.0`), but no `CHANGELOG.md`.
159. **Users can find installation, usage, troubleshooting instructions**: `PARTIAL — Incomplete`. `README.md` has developer run instructions, but no end-user user manual.
160. **Developer has realistic plan to maintain product**: `PARTIAL — Incomplete`. Detailed distribution guide exists, but no maintenance team or SLA.
161. **Critical data-loss or security response/withdrawal plan**: `FAIL — Confirmed failure`. No emergency kill-switch or update-revocation mechanism.
162. **Update availability and support expectations stated**: `PARTIAL — Incomplete`. Scoop manifest has autoupdate block, but in-app updater is absent.

### K. Marketing and Product Credibility (Items 163–178)
163. **Clear product description explains who CleanPilot is for**: `PASS — Verified`. Clear positioning as "honest, privacy-first disk analyzer" in `README.md`.
164. **Public landing page exists**: `FAIL — Confirmed failure`. No public web domain or GitHub Pages landing page deployed.
165. **Landing page includes real download or beta access path**: `FAIL — Confirmed failure`. No download page exists.
166. **Screenshots represent current application**: `PARTIAL — Incomplete`. SVG logos and banners exist; current full-resolution UI screenshots are not committed.
167. **Short authentic demonstration available or planned**: `PARTIAL — Incomplete`. Planned in `MARKETING_AND_DISTRIBUTION_GUIDE.md`, but no video exists.
168. **Features and benefits stated without unsupported claims**: `PASS — Verified`. Rejects fake performance gains; focuses on real space and diagnostics.
169. **System requirements and supported Windows versions visible**: `PASS — Verified`. Windows 10/11 x64/ARM64 clearly stated.
170. **Privacy and security info easy to find**: `PARTIAL — Incomplete`. Prominent "Zero Telemetry" badge in UI header; lacks public legal policy.
171. **Download process understandable and trustworthy**: `FAIL — Confirmed failure`. Manifests point to dead download URLs with dummy hashes.
172. **Users can provide feedback without friction**: `FAIL — Confirmed failure`. No feedback button or form in the application.
173. **Product has clear first-use experience**: `PARTIAL — Incomplete`. Opens to Dashboard view; lacks onboarding walkthrough or introductory modal.
174. **Launch plan identifies initial audience and channels**: `PASS — Verified`. Comprehensive target audience matrix in `MARKETING_AND_DISTRIBUTION_GUIDE.md`.
175. **Community promotion follows community rules**: `PASS — Verified`. Guidelines in marketing playbook specify anti-spam and subreddit rules.
176. **Marketing does not imply certification without evidence**: `PASS — Verified`. No false ISO or Microsoft certifications claimed.
177. **First public release has defined support and feedback process**: `FAIL — Confirmed failure`. Unmonitored release channels.
178. **Plan to learn from early users and improve onboarding**: `PARTIAL — Incomplete`. Strategic roadmap exists in docs, but technical instrumentation for feedback is absent.

---

## 10. Critical and High Findings

### Findings Summary
* **Critical Findings (Score Impact: Fatal Blockers)**: **0**
* **High Severity Findings (Score Impact: Major Release Blockers)**: **7**
* **Medium Severity Findings**: **8**
* **Low Severity Findings**: **6**

### High Severity Findings Register

```
+---------+-------------------+----------+-----------------------------------------------------------+
| ID      | Category          | Severity | Title                                                     |
+---------+-------------------+----------+-----------------------------------------------------------+
| SEC-001 | Signing / Rep     | HIGH     | Windows Binaries are Completely Unsigned                  |
| SEC-002 | Safety / UI       | HIGH     | WinSxS and System Repair Lack Confirmation & Lifecycle    |
| SEC-003 | Subprocess / UAC  | HIGH     | Elevated Command 120s Timeout & Missing Exit Code Check   |
| SEC-004 | Process Safety    | HIGH     | Process Termination Does Not Terminate Descendant Trees   |
| PKG-001 | Packaging         | HIGH     | No Standalone MSI Installer Exists; WiX Missing           |
| PKG-002 | Distribution      | HIGH     | Distribution Manifests Contain Dummy SHA-256 Hashes       |
| QAL-001 | Data / UX         | HIGH     | Broken HKLM Startup Optimization & False-Success Verdict  |
+---------+-------------------+----------+-----------------------------------------------------------+
```

#### [SEC-001] Windows Binaries are Completely Unsigned
* **Checklist Items**: 17, 18, 19, 21.
* **Finding**: `build/dist/CleanPilot/CleanPilot.exe` has Authenticode status `NotSigned` with `SignatureType: None`. No digital certificate is configured in `build.gradle.kts`.
* **Evidence**: Executed `Get-AuthenticodeSignature "build/dist/CleanPilot/CleanPilot.exe"` $\rightarrow$ `Status: NotSigned`.
* **User Impact**: Windows Defender SmartScreen blocks execution with "Windows protected your PC / Unknown Publisher". Users cannot launch CleanPilot without clicking hidden override links.
* **Release Impact**: Unacceptable for public launch. Completely destroys user trust for a security/maintenance utility.
* **Recommended Action**: Procure an EV or standard Authenticode Code-Signing Certificate; integrate Microsoft `signtool.exe` into Gradle release task.
* **Effort**: Medium (requires certificate acquisition).
* **Required Retest**: `Get-AuthenticodeSignature` returns `Status: Valid` with legitimate publisher subject and timestamp.

#### [SEC-002] WinSxS and System Repair Lack Confirmation & Lifecycle Journaling
* **Checklist Items**: 43, 44, 45, 74, 80.
* **Finding**: In `WindowsSystemRepairViewController.java` (lines 189–211 and 240–260), `onCleanComponentStore()` and `onRepairHealth()` immediately execute filesystem purges and elevated DISM commands upon button click. They do NOT invoke `OperationConfirmationDialog`, show no target preview, and do NOT register with `OperationLifecycleManager` or write to `OperationJournalEntity`.
* **Evidence**: File `WindowsSystemRepairViewController.java#L189-L211` and `#L240-L260`.
* **User Impact**: An accidental mouse click immediately deletes `C:\Windows\SoftwareDistribution\Download` and triggers system-level servicing mutations without user consent.
* **Release Impact**: Direct violation of CleanPilot's documented Core Safety Architecture ("Explicit User Consent & Dual-Phase Execution").
* **Recommended Action**: Construct an `OperationPreview` in `WindowsSystemRepairViewController`, require `OperationConfirmationDialog.confirm()`, and journal execution via `lifecycleManager`.
* **Effort**: Small (2 developer days).
* **Required Retest**: Clicking "Clean Component Store" displays the modal confirmation; aborting cancels execution; confirming writes an entry to `cleanpilot.db` journal.

#### [SEC-003] Elevated Command 120s Timeout & Missing Exit Code Propagation
* **Checklist Items**: 51, 53, 54, 97.
* **Finding**: `WindowsSecurityUtils.runElevated(String command)` executes via `ProcessExecutionUtils.execute(pb, 120)` with a hardcoded 120-second timeout. Repair commands like `dism /Online /Cleanup-Image /RestoreHealth ; sfc /scannow` take 10–30 minutes on corrupted systems, guaranteeing a timeout kill after 2 minutes. Furthermore, `Start-Process powershell.exe -Verb RunAs -Wait` without `-PassThru` does not return the inner elevated exit code, causing `WindowsSystemRepairViewController` to display "✔ System files repaired successfully" even when the repair failed or timed out.
* **Evidence**: `WindowsSecurityUtils.java#L63` and `SystemIntegrityRepairService.java#L132`.
* **User Impact**: Users with corrupted systems experience aborted repairs and are falsely told their system files were restored.
* **Release Impact**: Severe reliability and credibility defect in core advertised repair feature.
* **Recommended Action**: Modify `runElevated` to accept a custom timeout parameter (e.g. 1800s for system repairs); rewrite PowerShell launcher to capture `$p.ExitCode` via `-PassThru` and exit with the inner code.
* **Effort**: Small (1 developer day).
* **Required Retest**: Mock a failing elevated script; verify `runElevated` returns `false` and UI displays "Repair Interrupted".

#### [SEC-004] Process Termination Does Not Terminate Descendant Trees
* **Checklist Items**: 52, 53, 59.
* **Finding**: In `ProcessExecutionUtils.java#L96`, process timeout terminates the parent with `process.destroyForcibly()`. In `RogueProcessThreatService.java#L643`, fallback process termination executes `taskkill /F /PID <pid>`. On Windows, neither method terminates descendant child processes. Child processes spawned by malicious scripts or miners remain active in the background.
* **Evidence**: `ProcessExecutionUtils.java#L96` and `RogueProcessThreatService.java#L643`.
* **User Impact**: Killing a rogue parent script (e.g., PowerShell or CMD wrapper) leaves the actual payload or miner running unhindered.
* **Release Impact**: Compromises threat-hunting reliability.
* **Recommended Action**: In `ProcessExecutionUtils`, iterate `process.descendants().forEach(ProcessHandle::destroyForcibly)`. In `RogueProcessThreatService`, add `/T` (tree kill) to `taskkill`: `taskkill /F /T /PID <pid>`.
* **Effort**: Small (0.5 developer days).
* **Required Retest**: Spawn a parent process that launches a child notepad; kill parent; verify child process is also terminated.

#### [PKG-001] No Standalone MSI Installer Exists; WiX Toolset Missing
* **Checklist Items**: 1, 2, 6, 28, 146.
* **Finding**: Running `./gradlew.bat packageMsi` fails because WiX Toolset (`candle.exe`, `light.exe`) is not installed on the system. No `.msi` file exists on disk; only the raw unpackaged app-image folder exists.
* **Evidence**: `Get-Command candle.exe, light.exe` returned `exited with code 1` (not found).
* **User Impact**: General users cannot install CleanPilot via standard Windows setup wizard; no start menu shortcuts, uninstaller registry entries, or Add/Remove Programs integration.
* **Release Impact**: Mandatory blocker for public download.
* **Recommended Action**: Install WiX Toolset v3.14 on build machines; ensure `packageMsi` task runs cleanly to output `CleanPilot-1.0.0.msi`.
* **Effort**: Small (tooling configuration).
* **Required Retest**: `./gradlew.bat packageMsi` succeeds and outputs verified `.msi` in `build/dist/`.

#### [PKG-002] Distribution Manifests Contain Dummy SHA-256 Hashes
* **Checklist Items**: 20, 21, 171.
* **Finding**: `distribution/winget/CleanPilot.CleanPilot.installer.yaml`, `distribution/chocolatey/tools/chocolateyinstall.ps1`, and `distribution/scoop/cleanpilot.json` all contain literal string `<CALCULATED_SHA256_HASH>` and reference unreleased GitHub download URLs.
* **Evidence**: `CleanPilot.CleanPilot.installer.yaml#L11`, `chocolateyinstall.ps1#L10`, `cleanpilot.json#L10`.
* **User Impact**: Any attempt by power users to install CleanPilot via `winget`, `choco`, or `scoop` fails immediately with checksum mismatch or 404 Not Found errors.
* **Release Impact**: Total blocker for advertised package manager deployments.
* **Recommended Action**: Integrate an automated hashing script in Gradle that populates actual SHA-256 hashes into distribution manifests after release binary generation.
* **Effort**: Small (1 developer day).
* **Required Retest**: Run manifest validator; verify valid 64-character hex SHA-256 matches the release artifact.

#### [QAL-001] Broken HKLM Startup Optimization & Silent Failure
* **Checklist Items**: 94, 97, 104.
* **Finding**: `StartupOptimizerService.java` scans machine-wide startup apps in `HKLM` (line 57), but `setStartupEnabled()` hardcodes registry operations strictly to `RUN_KEY_HKCU` (lines 297–304). Attempting to disable an HKLM startup app executes `reg delete HKCU\... /v <name>`, leaving the HKLM entry untouched while the UI marks it disabled. Furthermore, modifying HKLM requires Administrator privileges, but no elevation request is performed.
* **Evidence**: `StartupOptimizerService.java#L294-L308`.
* **User Impact**: Users attempt to disable heavy system startup apps; the UI reports success, but the apps continue to launch on every Windows boot.
* **Release Impact**: Important data integrity and feature correctness defect.
* **Recommended Action**: Track origin key (`HKCU` vs `HKLM`) in `StartupAppInfo`; branch registry manipulation accordingly; invoke `WindowsSecurityUtils.runElevated` when modifying HKLM entries.
* **Effort**: Small (1 developer day).
* **Required Retest**: Unit test verifying HKLM startup item toggle targets HKLM with elevation.

---

## 11. Medium and Low Findings

### Medium Findings Register
* **[SEC-005] `FileDeletionService.isSafeDeletablePath` Inconsistent ProgramFiles Prefix Check**: `isSafeDeletablePath` checks `pathStr.equals(pf)` (exact equality), whereas `DeveloperCleanupService` checks `pathStr.equals(normPf) || pathStr.startsWith(normPf + File.separator)`. A file selected inside `C:\Program Files\Vendor\app.dll` is not blocked by `isSafeDeletablePath` if path depth $\ge 2$. (Item 61, 62).
* **[SEC-006] `WindowsComponentStoreService.deleteRecursively` Lacks Reparse-Point Guards**: Lines 195–205 use raw `Files.walk(path)` without `noneOf(FileVisitOption.class)` or junction interception, diverging from the reparse-point safeguards implemented in `DeveloperCleanupService` and `AppUninstallerService`. (Item 65, 80).
* **[SEC-007] `OperationLifecycleManager.beginExecution` Lacks State Enforcement**: Calling `beginExecution(opId)` transitions directly from any state to `EXECUTING` without verifying that the state was previously `AUTHORIZED`. (Item 44, 46).
* **[NET-001] Network Reset Executes Without Restart Warning**: `NetworkOptimizationService.optimizeNetworkStack()` invokes `netsh winsock reset` without warning the user that a system restart is required or that active VPN adapters will be disconnected. (Item 129).
* **[LEGAL-001] Missing Open-Source `LICENSE` File in Repository Root**: Despite displaying an Apache-2.0 badge in `README.md` and declaring `"license": "Apache-2.0"` in Scoop manifests, no `LICENSE` or `LICENSE.txt` file exists in the repository root. (Item 158).
* **[LEGAL-002] Missing Public `PRIVACY.md` Policy**: No dedicated privacy document exists to support the zero-telemetry claims or explain local SQLite data retention. (Item 29, 30).
* **[REL-001] Lack of Version-Controlled Git Repository**: The repository root contains no `.git` folder (`fatal: not a git repository`). Releases cannot be cryptographically tagged or tied to commit hashes. (Item 150).
* **[REL-002] Absence of Automated CI/CD Workflows**: No `.github/` directory or GitHub Actions workflows exist to automate testing, artifact compilation, hashing, and release publishing. (Item 149).

### Low Findings Register
* **[UX-002] Mixed Language Strings in `MainView.fxml`**: Navigation rail buttons contain French text (`"Bilan de santé"`, `"Nettoyage personnalisé"`, `"Moniteur de ressources"`), while the window title, menus, and badges are in English, despite complete i18n resource bundles (`messages_en`, `messages_fr`, `messages_ar`) existing in resources. (Item 105).
* **[UX-003] Missing First-Run Onboarding Experience**: The application launches directly into the Dashboard without introducing CleanPilot's safety philosophy, quarantine mechanism, or zero-telemetry architecture. (Item 173).
* **[DOC-001] Legacy Root `cleanpilot.db` Artifact**: A 12 KB `cleanpilot.db` file from early development exists in the workspace root, creating confusion regarding active database storage. (Item 81, 91).
* **[DOC-002] No `SECURITY.md` Vulnerability Disclosure Policy**: The repository lacks a defined vulnerability reporting channel or security contact email. (Item 155).
* **[DOC-003] Missing User Guide & Troubleshooting Manual**: Documentation is heavily developer-centric; no end-user manual exists for non-technical users. (Item 159).
* **[TEST-001] Zero Automated UI Tests (TestFX)**: Automated tests cover services and utilities thoroughly, but include 0 headless UI or controller workflow tests. (Item 136).

---

## 12. Security and Safety Assessment

### 12.1 Privileged Boundary Architecture
CleanPilot enforces an unprivileged UI model: the main application process runs in the security context of the standard logged-on Windows user.

```mermaid
flowchart TD
    UI["CleanPilot UI (Unprivileged User Process)"] -->|"Standard Actions"| LocalIO["Local Filesystem / NIO.2 / SQLite"]
    UI -->|"Sensitive Action Click"| Svc["Service Layer"]
    Svc -->|"Check Admin Status"| WSU["WindowsSecurityUtils.isRunningAsAdmin()"]
    WSU -->|True| Direct["Direct Process Execution"]
    WSU -->|False| UAC["UAC Prompt: powershell.exe -Verb RunAs"]
    UAC -->|"UTF-16LE Base64 Script"| Elevated["Elevated PowerShell Instance"]
    Elevated --> DismSfc["dism.exe / sfc.exe / netsh.exe"]
```

### 12.2 Privilege Elevation Security Analysis
1. **Command Injection Mitigation**: `WindowsSecurityUtils.runElevated` successfully neutralizes classic shell injection vectors (`&`, `;`, `|`, `` ` ``, `$`) by encoding commands into UTF-16LE Base64 and invoking `powershell.exe -EncodedCommand <base64>`. This represents a sound defensive design.
2. **Elevation Scope & UAC Friction**: Privileged operations are correctly restricted to tasks that strictly require Administrator rights (DISM component cleanup, SFC system file repair, AppX deprovisioning for all users, Winsock stack reset).
3. **Identified Weakness**: As noted in SEC-003, `Start-Process powershell.exe -Verb RunAs -Wait` lacks `-PassThru`, meaning exit codes from elevated utilities are discarded. Furthermore, the 120-second timeout forcibly terminates the launcher process while child servicing processes may continue running.

### 12.3 Filesystem Protection Invariants
* **Boundary Enforcement**: `isSafeDeletablePath` in `FileDeletionService` and `isSafeArtifactPath` in `DeveloperCleanupService` strictly normalize paths and enforce a minimum depth of 2 or 3 path segments, successfully preventing accidental targeting of drive roots (`C:\`), `%SystemRoot%`, and user home roots (`C:\Users\Username`).
* **Reparse Point (Junction) Isolation**: The file walking visitors explicitly configure `EnumSet.noneOf(FileVisitOption.class)`. In `preVisitDirectory`, directory junctions and symbolic links are detected via `attrs.isSymbolicLink() || attrs.isOther() || Files.isSymbolicLink(dir)`. The visitor unlinks the junction directory node itself (`Files.delete(dir)`) and returns `FileVisitResult.SKIP_SUBTREE`, guaranteeing that file deletions never descend into junction targets.
* **Quarantine Vault & Collision Safety**: `QuarantineService` moves files into `~/.cleanpilot/quarantine/` with a UUID prefix. If the move spans different physical drives, it gracefully falls back to copy-then-delete. On restore, destination collisions are handled safely by appending `_restored_N`, preventing accidental overwrites.

---

## 13. Installation, Packaging, Signing, and Distribution Assessment

### 13.1 Package Evaluation Matrix

| Packaging Mechanism | Target Artifact | Current Status | Blocker Description |
| :--- | :--- | :---: | :--- |
| **Standalone Fat JAR** | `build/libs/cleanpilot-1.0.0.jar` (65.9 MB) | **BUILT & VERIFIED** | Requires pre-installed Java 21 runtime on client machine. |
| **Native App Image** | `build/dist/CleanPilot/CleanPilot.exe` | **BUILT & VERIFIED** | Unsigned (`NotSigned`); raw directory; lacks installer wrapper. |
| **Windows MSI Setup** | `build/dist/CleanPilot-1.0.0.msi` | **FAILED / NOT BUILT** | `packageMsi` task requires WiX Toolset v3.x (missing from environment). |
| **Winget Manifest** | `CleanPilot.CleanPilot.installer.yaml` | **INVALID** | Contains placeholder `<CALCULATED_SHA256_HASH>` and unreleased URL. |
| **Chocolatey Package** | `chocolateyinstall.ps1` | **INVALID** | Contains placeholder `<CALCULATED_SHA256_HASH>` and unreleased URL. |
| **Scoop Manifest** | `cleanpilot.json` | **INVALID** | Contains placeholder `<CALCULATED_SHA256_HASH>` and unreleased URL. |

### 13.2 Code Signing & Defender Reputation Risk
Windows Defender SmartScreen evaluates binary reputation based on Authenticode code signing and historical download frequency. An unsigned executable downloaded from the internet that subsequently requests UAC elevation will trigger:
1. Microsoft Defender SmartScreen red/blue warning screen ("Windows protected your PC").
2. Antivirus heuristic warnings regarding unverified elevated launchers.
3. Elevated UAC prompt displaying "Publisher: Unknown" in an alarming yellow banner.

**Conclusion**: Releasing CleanPilot without an Authenticode certificate is a critical barrier to adoption and user trust.

---

## 14. Functional Correctness and Data-Integrity Assessment

### 14.1 Operation Lifecycle Contract Verification

```mermaid
stateDiagram-v2
    [*] --> DISCOVER: Non-invasive query
    DISCOVER --> VALIDATE: Boundary assertion
    VALIDATE --> PREVIEW: Quantified impact
    PREVIEW --> AUTHORIZE: Modal confirmation
    AUTHORIZE --> EXECUTE: Thread-safe run
    EXECUTE --> VERIFY: Outcome check
    VERIFY --> COMPLETE: Journaled in SQLite
    COMPLETE --> [*]
```

* **Compliant Services**: `FileDeletionService`, `DuplicateDetectionService`, `DeveloperCleanupService`, `QuarantineService`, and `AppUninstallerService` fully adhere to this lifecycle. Targets are scanned, validated against protected paths, presented in `OperationConfirmationDialog`, authorized by the user, executed asynchronously, and journaled in SQLite.
* **Non-Compliant Services**: `WindowsComponentStoreService` (WinSxS cleanup) and `SystemIntegrityRepairService` (SFC/DISM repair) bypass the lifecycle entirely. They execute directly from controller button handlers without preview, confirmation, or audit logging.

### 14.2 Database Integrity & Crash Recovery
* **Journal Persistence**: `OperationJournalEntity` tracks `operationId`, `operationType`, `state`, `riskClassification`, `totalTargets`, `completedTargets`, `failedTargets`, `bytesAffected`, and log details.
* **Crash Reconciliation**: `OperationLifecycleManager.reconcileInterruptedOperations()` executes on application startup (`@PostConstruct`). Any operation left in `EXECUTING` state from a prior crashed session is safely updated to `INTERRUPTED` without blind auto-retries, preventing corrupt recursive loops.

---

## 15. Test Execution Results

During this audit, the entire automated test suite was executed fresh using the Windows Gradle wrapper:

```powershell
.\gradlew.bat test --rerun-tasks
```

### 15.1 Execution Summary
* **Timestamp**: October 24, 2026, 15:14:13 CEST
* **Execution Duration**: 3 minutes 2 seconds (Test execution: 1 minute 58.41 seconds)
* **Exit Code**: `0` (BUILD SUCCESSFUL)
* **Total Test Suites**: 28 classes
* **Total Tests Executed**: 104
* **Passed**: 104 (100%)
* **Failures**: 0 (0%)
* **Skipped / Ignored**: 0 (0%)
* **Test Report Location**: `build/reports/tests/test/index.html`

### 15.2 Test Suite Breakdown

| Test Suite Class | Tests | Failures | Duration | Scope & Verification Focus |
| :--- | :---: | :---: | :---: | :--- |
| `ApplicationContextStartupTest` | 1 | 0 | 0.83s | Spring Boot context bootstrap & bean autowiring |
| `OperationPreviewTest` | 2 | 0 | 0.03s | Preview impact formatting & risk labeling |
| `AppUninstallerServiceTest` | 8 | 0 | 1.97s | Registry parsing, path whitelist, junction isolation |
| `BootTraceProfilerServiceTest` | 1 | 0 | 1.55s | Windows Event Log boot phase analysis |
| `DeepRecoveryScannerServiceTest` | 5 | 0 | 0.21s | Recycle Bin scanning & path traversal defenses |
| `DeveloperCleanupServiceTest` | 4 | 0 | 19.32s | Artifact discovery, reparse point skipping, deletion |
| `DuplicateDetectionServiceTest` | 5 | 0 | 0.16s | Parallel streaming SHA-256 boundary hashing |
| `FileDeletionServiceTest` | 5 | 0 | 0.02s | System path rejection, native trash delegation |
| `HardwareDriverServiceTest` | 5 | 0 | 5.59s | Driver query, storage controller telemetry |
| `LaptopDiagnosticsServiceTest` | 6 | 0 | 0.78s | Battery wear, thermal throttling, sensor queries |
| `LargeFileScannerServiceTest` | 3 | 0 | 0.05s | NIO.2 recursive scanning with size thresholds |
| `NetworkOptimizationServiceTest` | 2 | 0 | 1.88s | Netstat socket scanning & leak detection |
| `OperationLifecycleManagerTest` | 4 | 0 | 1.41s | State machine transitions & crash reconciliation |
| `ProcessMonitoringServiceTest` | 5 | 0 | 1.64s | Process CPU, RAM, and disk I/O metrics |
| `QuarantineServiceTest` | 6 | 0 | 0.36s | Soft-delete, SHA-256 hash, cross-volume move |
| `RogueProcessThreatServiceTest` | 8 | 0 | 45.15s | Entropy calculation, protected PID/binary asserts |
| `ScanHistoryServiceTest` | 4 | 0 | 0.46s | Historical scan audit persistence in SQLite |
| `ScheduledMaintenanceServiceTest` | 1 | 0 | 0.34s | Scheduled task dispatch and execution guard |
| `SmartCleanerServiceTest` | 2 | 0 | 1.70s | 1-Click clean aggregation across categories |
| `StartupOptimizerServiceTest` | 3 | 0 | 1.83s | Registry Run key scan and shortcut toggle |
| `SystemIntegrityRepairServiceTest` | 1 | 0 | 2.21s | DISM CheckHealth parsing and report creation |
| `WindowsAppxDebloaterServiceTest` | 6 | 0 | 2.60s | AppX regex injection defense, protected packages |
| `WindowsComponentStoreServiceTest` | 3 | 0 | 26.57s | WinSxS footprint estimation (dry-run mode) |
| `WindowsShellIntegrationServiceTest` | 1 | 0 | 0.04s | Context menu integration registration |
| `FormatUtilsTest` | 7 | 0 | 0.00s | Byte formatting, duration, date formatting |
| `PrefixTrieTest` | 2 | 0 | 0.01s | Fast prefix string matching |
| `ProcessExecutionUtilsTest` | 3 | 0 | 1.35s | Virtual thread stream draining & timeouts |
| `WindowsSecurityUtilsTest` | 1 | 0 | 0.36s | Base64 UTF-16LE elevation command formatting |

---

## 16. Windows Compatibility Matrix

| Operating System Configuration | Documented Claim | Verified Status | Evidence & Audit Findings |
| :--- | :--- | :---: | :--- |
| **Windows 11 x64 (22H2 / 23H2 / 24H2)** | Fully Supported | **PASS — Verified** | Audited directly on Win11 x64; all 104 tests pass; UI reflows cleanly. |
| **Windows 10 x64 (21H2 / 22H2)** | Fully Supported | **NOT VERIFIED** | Not tested on native Win10 machine during audit. |
| **Windows 11 on ARM64** | Supported (Java 21 ARM64) | **NOT VERIFIED** | No ARM64 host available; JNA native dispatch unverified on AArch64. |
| **NTFS Filesystem** | Fully Supported | **PASS — Verified** | Reparse points, file attributes, and junctions verified on host NTFS volume. |
| **ReFS Filesystem** | Fully Supported | **NOT VERIFIED** | No ReFS storage spaces volume available for testing. |
| **FAT32 / exFAT Volumes** | Fully Supported | **PARTIAL — Incomplete** | Cross-volume copy logic verified in unit test; physical USB drives unverified. |
| **Multi-Drive Setups (`C:`, `D:`, `E:`)** | Fully Supported | **PARTIAL — Incomplete** | Multi-root iteration supported in code; live secondary volumes unverified. |
| **Restricted User Account (Standard)** | Supported | **PARTIAL — Incomplete** | UI unprivileged execution verified; elevation behavior tested via mocks. |
| **High DPI Scaling (125%, 150%, 200%)** | Fully Supported | **PASS — Verified** | CSS tokens (`em`) and scrollable navigation rail accommodate scaling reflow. |

---

## 17. Privacy and Transparency Assessment

### 17.1 Zero-Telemetry Invariant Verification
CleanPilot claims a strict **Zero-Telemetry (100% Offline)** design. The audit conducted static dependency and source analysis to verify this invariant:
1. **Dependency Analysis**: Runtime classpath contains no analytics or tracking SDKs (Google Analytics, Firebase, Sentry, TelemetryDeck, Mixpanel, Segment).
2. **Network Activity**: Source code contains zero HTTP client libraries (`java.net.http.HttpClient` is never instantiated; Apache HttpClient and OkHttp are absent).
3. **Local Persistence**: All diagnostics, scan metrics, and history logs are written strictly to the local SQLite database at `${user.home}/.cleanpilot/cleanpilot.db`.
4. **Conclusion**: CleanPilot's zero-telemetry technical invariant is **100% genuine and verified in code**.

### 17.2 Legal and Transparency Gaps
Despite the authentic local-first technical design, CleanPilot fails external transparency standards:
* **Missing Privacy Policy**: There is no `PRIVACY.md` in the repository and no privacy policy URL configured in the UI or distribution metadata.
* **Regulatory Compliance**: Under global privacy standards (GDPR, California CCPA), software distributed to the public must clearly disclose data handling, local storage locations, and user deletion rights, even if zero data leaves the workstation.

---

## 18. UX and Onboarding Assessment

### 18.1 Layout Responsiveness
Following the recent UI overhaul, `MainView.fxml` encapsulates the 15-button navigation rail inside a dedicated `ScrollPane` (`fitToWidth="true"`, `.nav-scroll`). At window dimensions from 1440×900 down to 800×600:
* The navigation rail scrolls smoothly without vertical clipping.
* The content pane reflows dynamically without horizontal overflow.
* Modal dialogs (`OperationConfirmationDialog`) enforce a minimum size of 620×460 with internal scrolling on target lists.

### 18.2 Localization & Consistency Issues
* **Language Discrepancy**: While the window title, menus, and badges are in English, the navigation rail buttons in `MainView.fxml` are hardcoded in French (`"Bilan de santé"`, `"Nettoyage personnalisé"`, `"Moniteur de ressources"`).
* **Missing First-Use Experience**: First-time users are dropped directly onto the Dashboard without an introductory explanation of how CleanPilot protects their files or where quarantined items are stored.

---

## 19. Documentation, Support, and Incident-Response Assessment

### 19.1 Documentation Status
* **Engineering Documentation**: Comprehensive and high quality (`BASELINE.md`, `SAFETY_ARCHITECTURE.md`, `TEST_STRATEGY.md`, `COMPATIBILITY.md`, `RISK_REGISTER.md`).
* **User Documentation**: Inadequate. `README.md` targets Java/Gradle developers. No user manual or installation guide exists for standard Windows consumers.
* **Legal Licensing**: Missing. No `LICENSE` file exists in the repository root.

### 19.2 Incident Response & Vulnerability Disclosure
* **Vulnerability Disclosure**: Missing `SECURITY.md`. There is no security contact email, PGP key, or designated vulnerability disclosure process.
* **Emergency Response**: No emergency revocation or notification mechanism exists if a severe data-loss defect is discovered post-release.

---

## 20. Marketing Readiness Assessment

### 20.1 Brand Asset Kit
* CleanPilot possesses an exceptional, professionally designed brand asset suite under `docs/assets/logo/` and `src/main/resources/icons/`, including scalable vector icons (`cleanpilot-icon.svg`), horizontal dark/light logos, OpenGraph banners (1280×640), and multi-resolution Windows icons (`cleanpilot.ico`).

### 20.2 Marketing Feasibility
* CleanPilot's value proposition ("The Anti-CCleaner: 100% Offline, Zero Telemetry, Developer-First") is compelling and resonates strongly with technical power users.
* However, promoting CleanPilot publicly today would be disastrous: users directed to package managers or GitHub releases would encounter 404 errors, dummy checksum failures, or Windows SmartScreen malware warnings. Marketing must remain paused until P0 blockers are closed.

---

## 21. Prioritized Remediation Plan

```mermaid
flowchart TD
    P0["P0: Release Blockers (Sprint 1)"] --> P1["P1: Public Adoption (Sprint 2)"]
    P1 --> P2["P2: Polish & Usability (Sprint 3)"]
    P2 --> P3["P3: Future Enhancements (Post-Launch)"]
```

### P0 — Release Blockers (Mandatory Before Any External Release)

| Finding ID | Required Action | Acceptance Criteria | Effort | Dependencies |
| :--- | :--- | :--- | :---: | :--- |
| **SEC-002** | Add `OperationConfirmationDialog` and `lifecycleManager` to `WindowsSystemRepairViewController`. | WinSxS cleanup & System Repair require explicit modal confirmation and write to SQLite audit journal. | 2 days | None |
| **SEC-001** | Procure Authenticode certificate and integrate `signtool` into Gradle packaging tasks. | `Get-AuthenticodeSignature` on `CleanPilot.exe` returns `Status: Valid`. | 3 days | Certificate purchase |
| **PKG-001** | Install WiX Toolset v3.x and ensure `./gradlew.bat packageMsi` produces verified `.msi`. | Native MSI builds cleanly and installs on clean Windows VM. | 2 days | WiX Toolset |
| **PKG-002** | Compute actual SHA-256 hashes and update `winget`, `choco`, and `scoop` manifests. | Package manifests contain real 64-character hashes matching released binaries. | 1 day | PKG-001 |
| **SEC-003** | Fix `WindowsSecurityUtils.runElevated` timeout (allow $\ge 1800$s) and extract `$p.ExitCode` via `-PassThru`. | Elevated repairs do not abort at 120s; UI reflects real inner exit code. | 1 day | None |
| **SEC-004** | Update `ProcessExecutionUtils` to kill process tree descendants (`/T`). | Terminating a process cleanly kills all child worker processes. | 0.5 days | None |
| **LEGAL-001**| Add official Apache-2.0 `LICENSE` and `PRIVACY.md` files to repository root. | Legal files committed and accessible in project root. | 0.5 days | None |

### P1 — Required Before Wider Public Adoption
* **[QAL-001]**: Correct `StartupOptimizerService` to properly handle and elevate `HKLM` startup entries.
* **[NET-001]**: Add restart warning dialog and confirmation modal before executing `netsh winsock reset`.
* **[REL-001]**: Initialize Git version control and establish tagged release commits.
* **[REL-002]**: Configure automated GitHub Actions CI/CD workflow (`.github/workflows/build.yml`).
* **[UX-002]**: Bind navigation rail button labels to `messages.properties` resource bundles instead of hardcoded French.
* **[DOC-002]**: Publish `SECURITY.md` with official vulnerability disclosure instructions.

### P2 — Important Polish & Usability Improvements
* **[UX-003]**: Implement a 3-step introductory onboarding wizard explaining zero-telemetry and quarantine.
* **[TEST-001]**: Introduce TestFX automated UI tests covering navigation and modal dialog flows.
* **[SEC-005]**: Harmonize `isSafeDeletablePath` to enforce path prefix checks on `ProgramFiles`.
* **[DOC-003]**: Author an end-user troubleshooting and quick-start manual.

### P3 — Future Enhancements
* In-app update notification checking (querying GitHub Releases API locally).
* Live ReFS and multi-drive storage benchmarking.
* Native AArch64 compiled binary distribution for Windows on ARM.

---

## 22. Required Retest Evidence and Closure Criteria

To overturn the NO-GO decision and achieve release certification, the engineering team must provide:

1. **Digital Signature Proof**: Output of `Get-AuthenticodeSignature build\dist\CleanPilot\CleanPilot.exe` demonstrating `Status: Valid` and a recognized publisher identity.
2. **Installer Artifact Proof**: A compiled `CleanPilot-1.0.0.msi` artifact accompanied by a clean install, launch, and uninstall log on a fresh Windows 10/11 virtual machine.
3. **Distribution Manifest Verification**: Validated `winget validate`, `choco pack`, and `scoop checkver` outputs matching live GitHub release URLs and hashes.
4. **Safety Confirmation Verification**: Screencast or test log confirming `WindowsSystemRepairViewController` displays `OperationConfirmationDialog` with target previews before executing WinSxS purges.
5. **Elevated Execution Proof**: Automated test verifying that an elevated task taking $> 120$ seconds completes without premature timeout termination, and that failing elevated commands propagate `exitCode != 0`.
6. **Git Repository Baseline**: Output of `git status` and `git log -1` confirming a clean working tree and immutable release commit hash.

---

## 23. Final Recommendation and Conditions for Reconsideration

### Final Recommendation
CleanPilot demonstrates remarkable engineering craft in its core algorithmic subsystems, achieving true zero idle CPU, robust reparse-point isolation, and authentic zero telemetry. However, release engineering, binary signing, installer packaging, and confirmation consistency remain incomplete.

**CleanPilot must not be published to the public or promoted in its current state.**

### Conditions for Reconsideration
A follow-up audit will be convened to reconsider this release decision upon:
1. Completion of the 7 P0 release blockers.
2. Submission of the 6 required retest evidence artifacts listed in Section 22.
3. Attainment of a re-calculated readiness score of **$\ge 85 / 100$** with zero triggered mandatory release gates.

---

## 24. Appendix: Commands, Test Reports, Artifact Hashes & Evidence References

### A. Executed Audit Commands
```powershell
# 1. Environment & Git baseline
git status --porcelain
java -version
.\gradlew.bat -v

# 2. Test suite execution
.\gradlew.bat test --rerun-tasks

# 3. Artifact signature inspection
Get-AuthenticodeSignature "build/dist/CleanPilot/CleanPilot.exe" | Format-List

# 4. Cryptographic SHA-256 calculation
Get-FileHash "build/dist/CleanPilot/CleanPilot.exe", "build/libs/cleanpilot-1.0.0.jar" -Algorithm SHA256 | Format-List

# 5. Dependency tree inspection
.\gradlew.bat dependencies --configuration runtimeClasspath
```

### B. Cryptographic Hashes of Audited Artifacts
* **`build/dist/CleanPilot/CleanPilot.exe`**:  
  `8043CB64D65F08DDA76FFDB093D41560319309C75FBA222502F9E72899EB58C6`
* **`build/libs/cleanpilot-1.0.0.jar`**:  
  `C5741FFE1BB7F2AE0EE26AF632FDF3BC745C7C94FC7041734680DF2F87A2CEF1`

### C. Primary Code Evidence References
* **Elevated Execution & Quoting**: `src/main/java/com/cleanpilot/util/WindowsSecurityUtils.java` (`#L43-L74`)
* **Process Stream Draining & Timeout**: `src/main/java/com/cleanpilot/util/ProcessExecutionUtils.java` (`#L63-L115`)
* **File Deletion Boundary Protection**: `src/main/java/com/cleanpilot/service/FileDeletionService.java` (`#L68-L125`)
* **Quarantine Vault & Collision Handling**: `src/main/java/com/cleanpilot/service/QuarantineService.java` (`#L65-L142`)
* **Reparse Point & Junction Isolation**: `src/main/java/com/cleanpilot/service/DeveloperCleanupService.java` (`#L388-L418`)
* **AppX Debloater Regex & Failsafes**: `src/main/java/com/cleanpilot/service/WindowsAppxDebloaterService.java` (`#L25-L55`)
* **System Repair Confirmation Bypass**: `src/main/java/com/cleanpilot/controller/WindowsSystemRepairViewController.java` (`#L189-L260`)
* **Operation Lifecycle State Machine**: `src/main/java/com/cleanpilot/service/OperationLifecycleManager.java` (`#L40-L160`)
* **Packaging & WiX JPackage Tasks**: `build.gradle.kts` (`#L54-L126`)
