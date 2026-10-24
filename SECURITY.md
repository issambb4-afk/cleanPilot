# CleanPilot — Security Policy & Vulnerability Disclosure

CleanPilot takes security, privilege boundaries, and host filesystem safety extremely seriously. We welcome responsible security research and disclosures from independent security professionals, QA engineers, and users.

---

## 1. Supported Versions

Security updates are actively maintained for the following versions:

| Version | Supported | Security Policy |
| :--- | :---: | :--- |
| **1.0.x** | **Yes** | Active security patches and hotfixes |
| **< 1.0.0** | No | Pre-release; please upgrade to 1.0.0+ |

---

## 2. Core Security Invariants

When evaluating potential vulnerabilities, note CleanPilot's core security invariants:
1. **Unprivileged UI Context**: The JavaFX application UI runs as a standard unprivileged user process.
2. **Explicit Privilege Escalation**: Operations requiring Administrator rights (DISM, SFC, Winsock reset) must explicitly prompt via UAC and use injection-safe Base64 UTF-16LE encoded PowerShell scripts.
3. **Filesystem Confinement**: CleanPilot operations must strictly reject drive roots, `%SystemRoot%`, `%ProgramFiles%`, and user profile roots from destructive deletion.
4. **Reparse Point (Junction) Isolation**: Directory walks must never descend into NTFS directory junctions or follow symbolic links outside the scanned directory tree.
5. **Zero Outbound Telemetry**: The application must never transmit workstation data over the network.

---

## 3. Reporting a Security Vulnerability

If you discover a security vulnerability, privilege escalation flaw, command injection vulnerability, or path traversal bypass in CleanPilot:

> [!IMPORTANT]
> **Please do NOT report security vulnerabilities via public GitHub issues.**

Instead, please report the vulnerability privately:
1. **GitHub Private Vulnerability Report**: Submit a report via GitHub Security Advisories at:  
   [https://github.com/cleanpilot/cleanpilot/security/advisories/new](https://github.com/cleanpilot/cleanpilot/security/advisories/new)
2. **Email Disclosure**: Send an email to:  
   `security@cleanpilot.org`

### Information to Include in Your Report
To help us triage and resolve the issue quickly, please provide:
- A clear description of the vulnerability and its potential impact.
- Step-by-step reproduction instructions or a harmless proof-of-concept (PoC).
- Affected CleanPilot version and Windows OS version/architecture.
- Any suggested remediation or code fix.

---

## 4. Response Timeline & Disclosure SLA

We are committed to responding promptly to all security disclosures:
- **Initial Acknowledgment**: Within **48 hours** of report receipt.
- **Triage & Reproduction**: Within **5 business days**.
- **Fix & Patch Release**: Critical vulnerabilities patched within **14 days**.
- **Public Disclosure**: Coordinated after the fix is released and users have had an opportunity to update.

---

## 5. Security Hall of Fame

We gratefully credit security researchers who report verified vulnerabilities in accordance with this responsible disclosure policy.
