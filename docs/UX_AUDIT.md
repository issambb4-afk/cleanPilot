# CleanPilot — Comprehensive UI/UX Audit

**Date**: October 24, 2026  
**Auditor**: Senior Desktop Product Designer & JavaFX Systems Engineer  
**Scope**: Complete CleanPilot JavaFX Desktop Shell & 16 Views  
**Target Window Profiles**: 1440×900 (Large Desktop), 1280×800 (Standard Laptop), 1024×700 (Compact), 800×600 (Minimum Narrow)

---

## 1. Executive Summary

CleanPilot is a local-first Windows system maintenance and diagnostics application built on Java 21, JavaFX 21, AtlantaFX (Primer theme), SQLite, and Spring Boot 3. 

An exhaustive audit of all 16 FXML views, controller implementations, CSS stylesheets, and navigation interactions identified several key design, responsiveness, and usability gaps that hinder the user experience:
1. **Vertical Overflow in Application Sidebar**: The navigation rail in `MainView.fxml` places 15 buttons and multiple headers into a non-scrolling `VBox`. At vertical heights below 780px (such as standard laptop screens with 125% DPI scaling or compact 700px windows), the bottom navigation items and footer get clipped off-screen with no way to scroll.
2. **Missing Navigation State Synchronization**: `navThreatHunterBtn` was omitted from the `setActiveNav` array in `MainViewController.java`, resulting in stale navigation button states when navigating to Threat Hunter.
3. **Hardcoded Fixed Spacing and Unresponsive Grid Columns**: Several views (`DashboardView`, `DriversView`, `LaptopDiagnosticsView`, `DeveloperCleanerView`) use fixed `HBox` or 50/50 `GridPane` layouts without wrap capabilities or breakpoint listeners. At 1024px and 800px widths, cards compress excessively, truncating text and clipping numbers.
4. **Visual Hierarchy and Inconsistent Design Language**: Buttons and badges alternate between custom linear gradients (`linear-gradient(to right, #2563eb, #3b82f6)`), inline hex colors, and AtlantaFX semantic tokens (`-color-accent-subtle`). Standardized tokens are needed to achieve a unified, calm Windows 11 Fluent-inspired aesthetic.
5. **Keyboard Focus Rings & Accessibility Gaps**: Focus indicators are weak or invisible on certain custom toggle buttons and table row cells. Tab order on several screens jumps unexpectedly across toolbar buttons before reaching the primary search field.
6. **Information Overload in Action Toolbars**: Several bottom action toolbars crowd 6+ buttons together with equal visual weight, obscuring the primary safe action and risking accidental triggers of destructive operations.

---

## 2. Screen-by-Screen Audit Matrix

| Screen / View | FXML Path & Controller | User Impact | Responsiveness Finding | Accessibility & Usability Finding | Priority |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Global Shell & Sidebar** | `MainView.fxml`<br>`MainViewController.java` | Critical | Sidebar `VBox` overflows vertically on displays $<780$px height. No scroll container or compact icon-mode. | `navThreatHunterBtn` missing from active reset array. Menu accelerator shortcuts need clear tooltips. | **P0** |
| **Dashboard** | `DashboardView.fxml`<br>`DashboardViewController.java` | High | 3 KPI cards in fixed `HBox` compress severely on narrow screens. Telemetry grid fixed at 3 rigid columns. | Hero card uses heavy gradients. "1-Click Smart Clean" button needs clear risk indicator and preview dialog. | **P1** |
| **Process Monitor** | `ProcessesView.fxml`<br>`ProcessMonitorViewController.java` | High | Sparklines and table header toolbar wrap awkwardly when window width is reduced below 1100px. | Search field and Sort By combobox lack explicit accessible labels for screen readers. | **P1** |
| **Threat Hunter** | `ThreatHunterView.fxml`<br>`ThreatHunterViewController.java` | High | SplitPane divider ratio (0.65) leaves details panel cramped at 700px height. Table columns crowd each other. | Threat reasons list had raw technical logs; needs structured explainability with benign context. | **P1** |
| **Hardware & Health** | `LaptopDiagnosticsView.fxml`<br>`LaptopDiagnosticsViewController.java` | High | 2x2 subsystem grid with `percentWidth="50.0"` clips battery and thermal metrics on small laptop screens. | Overall score (100/100) lacks breakdown tooltip explaining the deductions. | **P1** |
| **Drivers Compatibility** | `DriversView.fxml`<br>`DriversViewController.java` | Medium | 4 KPI cards in single `HBox` truncate titles at 1024px. Table has 9 columns without horizontal scrolling. | Double-click row instruction is small and easy to miss. Needs explicit inspect button. | **P2** |
| **Large Files Analyzer** | `LargeFilesView.fxml`<br>`LargeFilesViewController.java` | High | Controls panel spans 2 rows with 7 widgets in row 2, causing severe horizontal overflow at 1024px. | Treemap tab container lacks an empty state when no files are scanned. | **P1** |
| **Duplicate File Hunter** | `DuplicatesView.fxml`<br>`DuplicatesViewController.java` | High | Auto-select toolbar has 5 buttons in a single row that overflow at narrow window widths. | TreeTableView hierarchy indentation needs stronger visual guidelines. | **P1** |
| **Developer CleanerView** | `DeveloperCleanerView.fxml`<br>`DeveloperCleanerViewController.java` | Medium | 5 toggle buttons in filter bar force search field to compress below usable width. | Reversible vs irreversible build cache distinction needs clear badges. | **P2** |
| **Startup Optimizer** | `StartupOptimizerView.fxml`<br>`StartupOptimizerViewController.java` | Medium | 4 KPI cards compress on narrow widths; boot verdict text truncates. | 1-Click Optimize button needs explicit confirmation dialog and preview. | **P2** |
| **Deep App Uninstaller** | `AppUninstallerView.fxml`<br>`AppUninstallerViewController.java` | Medium | StackPane table switching leaves residuals table selection summary disconnected. | Uninstall action vs residual file cleanup visually identical. Needs distinct styling. | **P2** |
| **Windows System Repair** | `WindowsSystemRepairView.fxml`<br>`WindowsSystemRepairViewController.java` | High | Log `TextArea` is fixed at 5 rows; overflows at low height. Debloater table has narrow search bar. | UAC elevation requirements need clear visual badge and explanatory tooltip. | **P1** |
| **Network Optimizer** | `NetworkOptimizerView.fxml`<br>`NetworkOptimizerViewController.java` | Medium | Sockets table has 6 columns; foreign endpoint column truncates IPv6 addresses. | "Flush DNS & Optimize Stack" button needs clear non-destructive explanation. | **P2** |
| **Trash & Recovery** | `recuperate-view.fxml`<br>`RecuperateController.java` | Medium | Segmented tabs + Deep Scan button crowd the search field. Bottom toolbar has 5 action buttons. | Restore vs Extract To vs Permanent Delete need distinct visual hierarchies. | **P2** |
| **Scan & Audit History** | `HistoryView.fxml`<br>`HistoryViewController.java` | Low | Clear History button has equal prominence to Refresh button. | Operation details need expandable drawer or dialog for viewing target list. | **P3** |
| **Settings & Preferences** | `SettingsView.fxml`<br>`SettingsViewController.java` | Low | Long vertical scroll view; cards are uniformly sized without category grouping. | Exclusion path list lacks browse button in dialog; keyboard delete key unmapped. | **P3** |

---

## 3. Systematic Findings & Root Causes

### 3.1 Layout Architecture & Breakpoints
* **Root Cause**: Views were designed assuming a desktop monitor running at 1920×1080 resolution at 100% DPI scaling (approximately 1180×780 logical window size).
* **Impact**: When running on common 13-inch or 14-inch Windows laptops at 125% or 150% scaling, available logical workspace drops to $1024 \times 600$ or $1280 \times 720$. At these dimensions, controls overflow horizontally and navigation rails clip vertically.
* **Remediation**:
  1. Wrap navigation rail in a minimalist `ScrollPane` with `fitToWidth="true"`.
  2. Implement collapsible navigation groups (Overview, Monitor, Manage, Repair, Activity).
  3. Replace rigid horizontal toolbars with responsive wrapping or prioritized secondary action menus.
  4. Ensure tables have `minWidth` on key columns and use `CONSTRAINED_RESIZE_POLICY` intelligently.

### 3.2 Visual Consistency & Design Tokens
* **Root Cause**: `app.css` mixed custom CSS gradients (`linear-gradient(to right, #2563eb, #3b82f6)`) with AtlantaFX `-color-*` variables and raw inline FXML styles (`style="-fx-font-size: 1.4em;"`).
* **Impact**: Themes felt disconnected. When switching between Primer Dark and Primer Light, hardcoded gradients looked out of place against light backgrounds, and some borders retained dark-mode opacity.
* **Remediation**:
  1. Establish centralized semantic design tokens in `DESIGN_SYSTEM.md` and `design-tokens.css`.
  2. Replace inline FXML style rules with semantic CSS class selectors.
  3. Support both AtlantaFX Primer Light and Primer Dark natively with cohesive surfaces.

### 3.3 Usability, Safety & Trust
* **Root Cause**: Technical jargon ("CLOSE_WAIT", "CM_PROB_NONE", "Sha256 Hash", "WinSxS") was presented without plain-language explanations.
* **Impact**: Non-technical users could not evaluate whether a detected issue was critical or harmless.
* **Remediation**:
  1. Present clear, plain-language summaries alongside technical data.
  2. Surface `ThreatExplanation` (observed indicators, anomaly rationale, benign context, next steps) directly in the UI.
  3. Clearly separate safe maintenance actions from high-risk or irreversible operations.
