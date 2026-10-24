# CleanPilot — Desktop Design System & Component Guidelines

**Version**: 1.0.0  
**Target Platform**: Windows 11 & Windows 10 (JavaFX 21 + AtlantaFX Primer Theme)  
**Aesthetic**: Calm, confident, precision productivity hub. Soft surfaces, clear typography, restrained neutral grays, semantic color accents.

---

## 1. Design Principles

1. **Precision & Trust**: Every number, progress bar, and status indicator reflects genuine hardware and filesystem measurements. No simulated optimizations, no fake percentages, and no artificial "health meters".
2. **Calm Productivity**: Replaces aggressive cybersecurity neon and garish gradients with clean Windows Fluent-inspired surfaces, subtle borders, and readable typography.
3. **Hierarchy of Action**: Primary actions are immediately obvious; secondary actions are restrained; destructive operations are visually distinct and explicitly confirmed.
4. **Adaptive & Responsive**: All components adapt to varying DPI scaling (100% to 200%) and window dimensions (from 1440×900 down to 1024×700 and 800×600).
5. **Zero Visual Noise**: Avoid unnecessary cards-within-cards, excessive borders, or gratuitous animations.

---

## 2. Color Palette & Semantic Tokens

### 2.1 Core Palette

| Token Name | Light Theme Hex | Dark Theme Hex | Purpose & Usage |
| :--- | :--- | :--- | :--- |
| `--cp-color-primary` | `#2563EB` (Blue 600) | `#3B82F6` (Blue 500) | Primary brand accent, primary action buttons, active tab indicators |
| `--cp-color-primary-hover` | `#1D4ED8` (Blue 700) | `#60A5FA` (Blue 400) | Hover state on primary interactive elements |
| `--cp-color-primary-subtle` | `#EFF6FF` (Blue 50) | `rgba(59, 130, 246, 0.15)` | Selected table row background, subtle accent badges, hero cards |
| `--cp-color-bg-base` | `#F8FAFC` (Slate 50) | `#0F172A` (Slate 900) | Application root background |
| `--cp-color-bg-surface` | `#FFFFFF` (Pure White) | `#1E293B` (Slate 800) | Content cards, dialog surfaces, table background |
| `--cp-color-bg-subtle` | `#F1F5F9` (Slate 100) | `#334155` (Slate 700) | Section headers, card headers, disabled inputs |
| `--cp-color-border-default` | `#E2E8F0` (Slate 200) | `#334155` (Slate 700) | Standard component and card borders (1px) |
| `--cp-color-border-subtle` | `#F1F5F9` (Slate 100) | `#1E293B` (Slate 800) | Dividers and table column separators |
| `--cp-color-fg-default` | `#0F172A` (Slate 900) | `#F8FAFC` (Slate 50) | Primary headlines, table cell text, input text |
| `--cp-color-fg-muted` | `#64748B` (Slate 500) | `#94A3B8` (Slate 400) | Secondary supporting text, captions, timestamps |
| `--cp-color-fg-subtle` | `#94A3B8` (Slate 400) | `#64748B` (Slate 500) | Inactive icons, placeholder text |

### 2.2 Semantic Feedback & Status Tokens

| Status | Foreground / Text | Background Subtle | Badge Style Class | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Success** | `#16A34A` (Green 600) | `rgba(22, 163, 74, 0.12)` | `.badge-success` | Healthy components, recovered space, verified safe |
| **Warning** | `#D97706` (Amber 600) | `rgba(217, 119, 6, 0.12)` | `.badge-warning` | High boot impact, outdated drivers, unverified signatures |
| **Danger** | `#DC2626` (Red 600) | `rgba(220, 38, 38, 0.12)` | `.badge-danger` | Rogue process anomalies, permanent deletions, component errors |
| **Info / Neutral** | `#0284C7` (Sky 600) | `rgba(2, 132, 199, 0.12)` | `.badge-info` | Offline privacy status, diagnostic counts, read-only stats |

---

## 3. Typography Scale

CleanPilot uses the standard Windows system font stack: `"Segoe UI Variable Text", "Segoe UI", -apple-system, sans-serif` with standard LCD subpixel antialiasing.

| Style Class | Font Size | Weight | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- |
| `.title-1` | 26px (1.65em) | 800 (Extra Bold) | 32px | Primary dashboard header, major health score |
| `.title-2` | 20px (1.30em) | 700 (Bold) | 26px | Screen / module titles |
| `.title-3` | 16px (1.10em) | 700 (Bold) | 22px | Major section headers, card titles |
| `.title-4` | 14px (0.95em) | 600 (Semi-Bold) | 20px | Sub-card headers, table column group headers |
| `.body` | 13px (0.88em) | 400 (Regular) | 18px | Standard body text, table cell contents |
| `.text-bold` | 13px (0.88em) | 600 (Semi-Bold) | 18px | Emphasized data, key metrics |
| `.text-small` | 12px (0.80em) | 400 (Regular) | 16px | Supporting notes, secondary descriptions |
| `.text-caption` | 11px (0.72em) | 700 (Bold) | 14px | All-caps card category labels, column hints |

---

## 4. Component Standards

### 4.1 Buttons & Interactive Controls

```css
/* Primary Action */
.button.accent {
    -fx-background-color: #2563eb;
    -fx-text-fill: white;
    -fx-background-radius: 6px;
    -fx-font-weight: 600;
    -fx-padding: 7 16;
    -fx-cursor: hand;
}
.button.accent:hover {
    -fx-background-color: #1d4ed8;
}

/* Secondary / Outlined */
.button.button-outlined {
    -fx-background-color: transparent;
    -fx-border-color: -color-border-default;
    -fx-border-radius: 6px;
    -fx-background-radius: 6px;
    -fx-text-fill: -color-fg-default;
    -fx-padding: 6 14;
}
.button.button-outlined:hover {
    -fx-background-color: -color-neutral-subtle;
}

/* Destructive / High Risk */
.button.danger {
    -fx-background-color: #dc2626;
    -fx-text-fill: white;
    -fx-background-radius: 6px;
    -fx-font-weight: 600;
    -fx-padding: 7 16;
}
.button.danger:hover {
    -fx-background-color: #b91c1c;
}
```

### 4.2 Cards & Surfaces
* **Standard Card** (`.card`): 1px border (`-color-border-subtle`), 8px border-radius, subtle background (`-color-bg-subtle`).
* **KPI Metric Card** (`.card.kpi-card`): Displays an all-caps small caption on top, prominent value in `.title-2`, and a 1-line plain-language context note below.
* **Hero Action Card** (`.card.card-hero`): Used for primary 1-Click Smart Clean and system repair recommendations with soft accent tints.

### 4.3 Data Tables (`TableView` & `TreeTableView`)
* Constrained column resize policies (`CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN` or `CONSTRAINED_RESIZE_POLICY`).
* Clean header rows with subtle bottom borders.
* Alternating rows or subtle hover highlight (`-color-neutral-subtle`).
* Smooth selection highlight using `-color-accent-subtle`.
* Centered empty-state placeholder displaying informative graphic or icon and helpful instruction.

### 4.4 Navigation Rail (Sidebar)
* Minimum width 220px, default width 240px.
* Encapsulated inside a vertical `ScrollPane` with transparent background so it scrolls effortlessly if window height is $<750$px.
* Logically grouped categories:
  * **Overview**
  * **Health & Monitoring**
  * **Storage & Cleanup**
  * **System Tools**
  * **Audit & Settings**
* Active item highlighted with subtle blue pill or accent fill and left indicator bar.

---

## 5. Accessibility Requirements

1. **Focus Rings**: All interactive controls must render a visible 2px focus border (`#3B82F6`) when focused via keyboard navigation (`Tab` / `Shift+Tab`).
2. **Contrast Ratio**: All body and headline text must maintain a minimum contrast ratio of 4.5:1 against the card or surface background in both light and dark themes.
3. **Non-Color Indicators**: Every status state (e.g., Critical, Suspicious, Safe) must provide both an explicit text label and a visual icon or badge—never color alone.
4. **Accessible Shortcuts**: Standard menu accelerators (`Ctrl+D` for Dashboard, `Ctrl+F` for Duplicates, `Ctrl+R` for Processes, `F1` for About) mapped in `MenuBar`.
