package com.cleanpilot.service;

import com.cleanpilot.model.ActiveSocketInfo;
import com.cleanpilot.model.ProcessThreatInfo;
import com.cleanpilot.model.ProcessThreatInfo.ThreatLevel;
import com.cleanpilot.model.ThreatExplanation;
import com.cleanpilot.util.ProcessExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * High-precision Windows Rogue Process & Threat Hunter.
 * Detects stealth crypto-miners, outbound reverse shells/beacons, process masquerading,
 * high binary entropy (packers/droplets), and unsigned payloads.
 *
 * Implements Stage Five production-readiness requirements:
 * 1. Multi-signal evidence correlation (path, signature, entropy, sockets, ancestry).
 * 2. Multi-core normalized CPU delta sampling across consecutive ticks.
 * 3. Developer tooling and compiler calibration to prevent false positives.
 * 4. Transparent plain-language explainability for every flagged finding.
 * 5. Strict fail-safe protections ensuring essential Windows OS binaries are never disrupted.
 * 6. Two-stage graceful process termination (SIGTERM before forced kill).
 */
@Service
public class RogueProcessThreatService {

    private static final Logger log = LoggerFactory.getLogger(RogueProcessThreatService.class);

    private final OperatingSystem operatingSystem;
    private final int logicalProcessorCount;
    private final NetworkOptimizationService networkService;

    // Cache signature results and entropy to minimize execution overhead
    private final Map<String, SignatureRecord> signatureCache = new ConcurrentHashMap<>();
    private final Map<String, Double> entropyCache = new ConcurrentHashMap<>();
    private final Map<Integer, OSProcess> priorProcessTicks = new ConcurrentHashMap<>();
    private final Set<Integer> suspendedPids = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private record SignatureRecord(boolean isSigned, String signer, String status) {}

    // Immutable list of core Windows binaries that must NEVER be terminated or touched
    private static final Set<String> IMMUTABLE_CRITICAL_BINARIES = Set.of(
            "system", "system idle process", "registry", "smss.exe", "csrss.exe", "wininit.exe",
            "services.exe", "lsass.exe", "winlogon.exe", "explorer.exe", "svchost.exe",
            "fontdrvhost.exe", "dwm.exe", "spoolsv.exe", "taskhostw.exe", "sihost.exe",
            "searchindexer.exe", "ctfmon.exe", "runtimebroker.exe", "audiodg.exe",
            "smartscreen.exe", "securityhealthservice.exe", "cleanpilot", "cleanpilot.exe"
    );

    // Well-known system binary names commonly targeted for masquerading
    private static final Set<String> KNOWN_SYSTEM_NAMES = Set.of(
            "svchost.exe", "csrss.exe", "lsass.exe", "services.exe", "wininit.exe",
            "winlogon.exe", "smss.exe", "taskhostw.exe", "sihost.exe"
    );

    // Recognized developer and build runtimes to calibrate against false-positive mining flags
    private static final Set<String> RECOGNIZED_DEVELOPER_TOOLS = Set.of(
            "node.exe", "python.exe", "pythonw.exe", "java.exe", "javaw.exe",
            "rustc.exe", "cargo.exe", "go.exe", "cl.exe", "gcc.exe", "g++.exe",
            "msbuild.exe", "dotnet.exe", "git.exe", "javac.exe", "gradlew.bat",
            "dart.exe", "flutter.bat", "code.exe", "idea64.exe", "devenv.exe", "cursor.exe"
    );

    // High-risk remote ports associated with C2 beaconing, reverse shells, Metasploit, IRC botnets
    private static final Set<Integer> SUSPICIOUS_C2_PORTS = Set.of(
            4444, 1337, 31337, 5555, 6667, 8088, 9001, 9050, 4445, 8888, 7777, 9999
    );

    // Script engines and LOLBins that should rarely hold direct outbound internet connections
    private static final Set<String> SUSPICIOUS_SCRIPT_ENGINES = Set.of(
            "powershell.exe", "cmd.exe", "cscript.exe", "wscript.exe",
            "mshta.exe", "rundll32.exe", "certutil.exe", "regsvr32.exe", "bitsadmin.exe"
    );

    // Crypto-mining keywords in command-line or binary names
    private static final List<String> MINING_KEYWORDS = List.of(
            "xmrig", "minerd", "stratum", "stratum+tcp", "nicehash", "cryptonight",
            "ethminer", "kawpow", "cpuminer", "hashrate", "monero", "nanopool", "ethermine"
    );

    public RogueProcessThreatService(NetworkOptimizationService networkService) {
        this.networkService = networkService;
        SystemInfo systemInfo = new SystemInfo();
        this.operatingSystem = systemInfo.getOperatingSystem();
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        this.logicalProcessorCount = Math.max(1, processor.getLogicalProcessorCount());
    }

    /**
     * Scans all running processes and performs multi-signal evidence correlation across
     * CPU velocity, network sockets, path legitimacy, Authenticode signatures, and binary entropy.
     */
    public List<ProcessThreatInfo> scanProcessesForThreats() {
        List<OSProcess> currentProcs = operatingSystem.getProcesses();
        Map<Integer, OSProcess> currentMap = new HashMap<>();

        // 1. Gather active socket connections mapped by PID
        Map<Integer, List<ActiveSocketInfo>> socketsByPid = new HashMap<>();
        try {
            List<ActiveSocketInfo> sockets = networkService.scanActiveSockets();
            for (ActiveSocketInfo sock : sockets) {
                socketsByPid.computeIfAbsent(sock.pid(), k -> new ArrayList<>()).add(sock);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve network sockets: {}", e.getMessage());
        }

        List<ProcessThreatInfo> results = new ArrayList<>();

        for (OSProcess proc : currentProcs) {
            int pid = proc.getProcessID();
            currentMap.put(pid, proc);

            // Compute multi-core normalized delta CPU usage across consecutive samples
            OSProcess prior = priorProcessTicks.get(pid);
            double cpuPercent;
            boolean hasPriorSample = (prior != null);
            if (hasPriorSample) {
                double rawCpu = proc.getProcessCpuLoadBetweenTicks(prior);
                cpuPercent = Math.max(0.0, (rawCpu * 100.0) / logicalProcessorCount);
            } else {
                // Baseline tick recorded; do not claim high sustained CPU velocity on a single uncalibrated sample
                cpuPercent = 0.0;
            }

            String procName = proc.getName() != null ? proc.getName().trim() : "Process " + pid;
            String path = proc.getPath() != null ? proc.getPath().trim() : "";
            String cmdLine = proc.getCommandLine() != null ? proc.getCommandLine().trim() : "";

            // Evaluate if this is an essential Windows component protected from termination
            boolean isProtected = isProtectedProcess(pid, procName, path);
            boolean isDevTool = isRecognizedDeveloperTool(procName, path);

            // Socket correlations
            List<ActiveSocketInfo> procSockets = socketsByPid.getOrDefault(pid, Collections.emptyList());
            int foreignSocketCount = 0;
            List<String> foreignConnections = new ArrayList<>();
            for (ActiveSocketInfo s : procSockets) {
                if (isForeignPublicAddress(s.foreignAddress())) {
                    foreignSocketCount++;
                    foreignConnections.add(s.foreignAddress());
                }
            }

            // Multi-signal evidence correlation
            List<String> threatReasons = new ArrayList<>();
            int riskScore = 0;
            double entropy = 0.0;
            boolean isSigned = true;
            String signer = "Microsoft / Valid";
            boolean isUserDir = isUserWritableDirectory(path);

            if (isProtected) {
                riskScore = 0;
            } else if (isDevTool) {
                // Developer tool calibration: do not flag as miner merely for high CPU in user project folders
                if (containsMiningKeywords(procName, cmdLine)) {
                    riskScore += 45;
                    threatReasons.add("Cryptomining parameters or mining pool signatures detected in arguments");
                }
                if (isMasqueradingSystemBinary(procName, path)) {
                    riskScore += 50;
                    threatReasons.add("CRITICAL: Masquerading system binary executing outside System32 (" + path + ")");
                }
                if (hasDeceptiveExtension(path)) {
                    riskScore += 35;
                    threatReasons.add("Deceptive executable extension (" + procName + ")");
                }
            } else {
                // Signal 1: Process Masquerading & Deceptive Names
                if (isMasqueradingSystemBinary(procName, path)) {
                    riskScore += 50;
                    threatReasons.add("CRITICAL: Masquerading system binary executing outside System32 (" + path + ")");
                }

                if (hasDeceptiveExtension(path) || hasTyposquattingName(procName)) {
                    riskScore += 35;
                    threatReasons.add("Deceptive executable extension or typosquatted system name (" + procName + ")");
                }

                // Signal 2: Sustained High CPU Velocity & Mining Indicators (requires confirmed multi-tick delta)
                if (isUserDir && hasPriorSample && cpuPercent >= 15.0) {
                    riskScore += 30;
                    threatReasons.add(String.format("High sustained CPU velocity (%.1f%%) originating from user directory", cpuPercent));
                }

                if (containsMiningKeywords(procName, cmdLine)) {
                    riskScore += 45;
                    threatReasons.add("Cryptomining parameters or mining pool signatures detected in arguments");
                }

                // Signal 3: Outbound Beaconing & Reverse Shell Hunter
                if (!foreignConnections.isEmpty()) {
                    String lowerName = procName.toLowerCase(Locale.ROOT);
                    if (SUSPICIOUS_SCRIPT_ENGINES.contains(lowerName)) {
                        riskScore += 40;
                        threatReasons.add("Script engine (" + procName + ") maintaining active foreign internet connections");
                    }

                    for (String remote : foreignConnections) {
                        int remotePort = extractPort(remote);
                        if (SUSPICIOUS_C2_PORTS.contains(remotePort)) {
                            riskScore += 45;
                            threatReasons.add("Outbound connection on known C2 / reverse-shell port: " + remotePort);
                            break;
                        }
                    }

                    if (isUserDir && foreignSocketCount >= 5) {
                        riskScore += 25;
                        threatReasons.add("Rapid outbound socket fanout (" + foreignSocketCount + " sockets) from user directory");
                    }
                }

                // Signal 4: Authenticode Digital Signature Verification (targeted to suspicious candidates)
                boolean shouldVerifySig = (riskScore >= 20) || (foreignSocketCount > 0 && isUserDir) || (isUserDir && hasPriorSample && cpuPercent >= 10.0);
                if (shouldVerifySig) {
                    SignatureRecord sig = verifyAuthenticodeSignature(path);
                    isSigned = sig.isSigned();
                    signer = sig.signer();
                    if (!isSigned) {
                        riskScore += 25;
                        threatReasons.add("Executable lacks valid digital signature (Unsigned)");
                    } else if ("HashMismatch".equalsIgnoreCase(sig.status())) {
                        riskScore += 50;
                        threatReasons.add("CRITICAL: Tampered binary with corrupted / forged digital signature");
                    }
                }

                // Signal 5: High File Entropy Scanner (Packed / Droplets)
                if (isUserDir && !path.isBlank()) {
                    entropy = calculateFileEntropy(Path.of(path));
                    if (entropy >= 7.20) {
                        riskScore += 25;
                        threatReasons.add(String.format("High file entropy (%.2f / 8.00): potential packed code or encrypted droplet", entropy));
                    }
                }
            }

            // Clamp risk score to [0, 100]
            riskScore = Math.min(100, Math.max(0, riskScore));

            ThreatLevel level;
            if (riskScore >= 75) {
                level = ThreatLevel.CRITICAL;
            } else if (riskScore >= 50) {
                level = ThreatLevel.HIGH;
            } else if (riskScore >= 20) {
                level = ThreatLevel.SUSPICIOUS;
            } else {
                level = ThreatLevel.SAFE;
            }

            boolean isSuspended = suspendedPids.contains(pid);

            // Synthesize transparent explainability analysis
            ThreatExplanation explanation;
            if (isProtected) {
                explanation = ThreatExplanation.verifiedNormal(procName);
            } else if (isDevTool && riskScore < 30) {
                explanation = ThreatExplanation.developerTool(procName, path);
            } else if (threatReasons.isEmpty()) {
                explanation = ThreatExplanation.verifiedNormal(procName);
            } else {
                explanation = buildExplanation(procName, path, cmdLine, riskScore, threatReasons, foreignConnections, isSigned, signer, entropy);
            }

            results.add(new ProcessThreatInfo(
                    pid,
                    procName,
                    path.isBlank() ? "N/A (Kernel / Protected)" : path,
                    cmdLine.isBlank() ? "-" : cmdLine,
                    cpuPercent,
                    proc.getResidentSetSize(),
                    foreignSocketCount,
                    foreignConnections,
                    riskScore,
                    level,
                    threatReasons,
                    isProtected,
                    isSigned,
                    signer,
                    entropy,
                    isSuspended,
                    explanation
            ));
        }

        // Update tick map for accurate subsequent CPU velocity calculations
        priorProcessTicks.clear();
        priorProcessTicks.putAll(currentMap);

        // Sort: highest risk score first, then by CPU usage
        results.sort(Comparator.comparingInt(ProcessThreatInfo::riskScore).reversed()
                .thenComparing(Comparator.comparingDouble(ProcessThreatInfo::cpuPercent).reversed()));

        return results;
    }

    /**
     * Builds a detailed, plain-language explanation for why a process was flagged,
     * detailing observed signals, anomaly rationale, legitimate software contexts, and investigation steps.
     */
    private ThreatExplanation buildExplanation(
            String procName,
            String path,
            String cmdLine,
            int riskScore,
            List<String> threatReasons,
            List<String> foreignConnections,
            boolean isSigned,
            String signer,
            double entropy
    ) {
        List<String> indicators = new ArrayList<>(threatReasons);
        List<String> benignContext = new ArrayList<>();
        List<String> recommendedSteps = new ArrayList<>();
        StringBuilder rationale = new StringBuilder();

        boolean masquerading = threatReasons.stream().anyMatch(r -> r.contains("Masquerading"));
        boolean c2Port = threatReasons.stream().anyMatch(r -> r.contains("C2"));
        boolean minerFlags = threatReasons.stream().anyMatch(r -> r.contains("Cryptomining"));
        boolean scriptEngine = threatReasons.stream().anyMatch(r -> r.contains("Script engine"));
        boolean highCpu = threatReasons.stream().anyMatch(r -> r.contains("CPU velocity"));
        boolean unsigned = !isSigned;
        boolean highEntropy = entropy >= 7.20;

        if (masquerading) {
            rationale.append("Binary claims the name of a core Windows component ('").append(procName)
                    .append("') but executes from '").append(path).append("' instead of %SystemRoot%\\System32. ");
            benignContext.add("Legacy custom administrative script or in-house utility coincidentally given a system name");
            recommendedSteps.add("Inspect file location, digital signature, and parent process ancestry in Process Monitor");
            recommendedSteps.add("Consider immediate process freeze while verifying legitimacy");
        }

        if (c2Port) {
            rationale.append("Outbound communication detected on known reverse-shell/C2 infrastructure port. ");
            benignContext.add("Developer local test server, proxy tunnel, or custom network client");
            recommendedSteps.add("Inspect foreign connection IP addresses and destination hostnames");
            recommendedSteps.add("Verify whether this port is expected for your active development or gaming activities");
        }

        if (minerFlags) {
            rationale.append("Process arguments contain Stratum or mining pool signatures. ");
            benignContext.add("Authorized cryptocurrency mining utility installed intentionally by the user");
            recommendedSteps.add("Verify if this process was launched intentionally; if not, terminate immediately");
        }

        if (scriptEngine && !foreignConnections.isEmpty()) {
            rationale.append("System script interpreter maintains active internet sockets. ");
            benignContext.add("Legitimate IT management script, software updater, or build automation task");
            recommendedSteps.add("Examine process command-line arguments to review the executing script path");
        }

        if (highCpu) {
            rationale.append("Sustained high CPU velocity originating from user-writable directory. ");
            benignContext.add("Video rendering tool, 3D gaming client, scientific compute, or archive compression");
            recommendedSteps.add("Check whether an intensive user application or game is currently active");
        }

        if (highEntropy && unsigned) {
            rationale.append("Binary lacks Authenticode signature and contains high Shannon entropy (")
                    .append(String.format("%.2f", entropy)).append(" / 8.00), characteristic of packed or encrypted code. ");
            benignContext.add("Open-source executable compressed with UPX or packaged using PyInstaller / Electron");
            recommendedSteps.add("Check software vendor and download source");
        }

        if (rationale.isEmpty()) {
            rationale.append("Process exhibits one or more anomalous behavioral indicators. ");
            benignContext.add("Third-party utility or background service");
            recommendedSteps.add("Review process path and digital signature");
        }

        return new ThreatExplanation(
                indicators,
                rationale.toString().trim(),
                benignContext,
                recommendedSteps
        );
    }

    /**
     * Determines whether a process is an essential operating system component
     * whose termination or suspension would destabilize or crash Windows.
     */
    public boolean isProtectedProcess(int pid, String processName, String path) {
        // PID 0 (Idle) and PID 4 (System)
        if (pid <= 4) {
            return true;
        }

        // CleanPilot's own process and parent process
        long currentPid = ProcessHandle.current().pid();
        if (pid == currentPid) {
            return true;
        }
        Optional<ProcessHandle> parentOpt = ProcessHandle.current().parent();
        if (parentOpt.isPresent() && parentOpt.get().pid() == pid) {
            return true;
        }

        if (processName == null || processName.isBlank()) {
            return false;
        }

        String lowerName = processName.trim().toLowerCase(Locale.ROOT);
        String lowerPath = path != null ? path.trim().toLowerCase(Locale.ROOT) : "";

        // Development tools and IDEs exemption to prevent developer interruption
        if (lowerName.equals("antigravity.exe") || lowerName.equals("code.exe")
                || lowerName.equals("idea64.exe") || lowerName.equals("cursor.exe")
                || lowerName.equals("eclipse.exe") || lowerName.equals("devenv.exe")) {
            return true;
        }

        // Critical system binaries: only protect if they are running from legitimate Windows directories
        if (IMMUTABLE_CRITICAL_BINARIES.contains(lowerName)) {
            // If path is empty, it's typically an in-kernel driver or elevated system service
            if (lowerPath.isEmpty()) {
                return true;
            }
            // Legitimate Windows system directories
            String sysRoot = System.getenv("SystemRoot");
            if (sysRoot == null || sysRoot.isBlank()) sysRoot = "C:\\Windows";
            String normSysRoot = sysRoot.trim().toLowerCase(Locale.ROOT);

            if (lowerPath.startsWith(normSysRoot + "\\system32\\")
                    || lowerPath.startsWith(normSysRoot + "\\syswow64\\")
                    || lowerPath.startsWith(normSysRoot + "\\systemapps\\")
                    || lowerPath.startsWith(normSysRoot + "\\explorer.exe")
                    || lowerPath.contains("\\program files\\windowsapps\\")) {
                return true;
            }
            // If it has a critical name but NOT in System32 (e.g. C:\Users\...\svchost.exe), it's masquerading!
            return false;
        }

        return false;
    }

    /**
     * Determines whether a binary is a recognized developer tool or runtime.
     */
    public boolean isRecognizedDeveloperTool(String procName, String path) {
        if (procName == null || procName.isBlank()) return false;
        String lower = procName.trim().toLowerCase(Locale.ROOT);
        return RECOGNIZED_DEVELOPER_TOOLS.contains(lower);
    }

    /**
     * Detects process masquerading: claiming a critical system binary name while
     * executing from an illegitimate directory (e.g. AppData, Temp, ProgramData).
     */
    public boolean isMasqueradingSystemBinary(String processName, String path) {
        if (processName == null || path == null || path.isBlank()) return false;
        String lowerName = processName.trim().toLowerCase(Locale.ROOT);
        String lowerPath = path.trim().toLowerCase(Locale.ROOT);

        if (KNOWN_SYSTEM_NAMES.contains(lowerName)) {
            boolean inSystem32 = lowerPath.startsWith("c:\\windows\\system32\\") || lowerPath.startsWith("c:\\windows\\syswow64\\");
            return !inSystem32;
        }

        if ("explorer.exe".equals(lowerName)) {
            return !lowerPath.equals("c:\\windows\\explorer.exe") && !lowerPath.startsWith("c:\\windows\\systemapps\\");
        }

        return false;
    }

    /**
     * Checks for deceptive double file extensions (e.g., document.pdf.exe, invoice.docx.exe).
     */
    public boolean hasDeceptiveExtension(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf.exe") || lower.endsWith(".docx.exe")
                || lower.endsWith(".xlsx.exe") || lower.endsWith(".jpg.exe")
                || lower.endsWith(".png.exe") || lower.endsWith(".txt.exe")
                || lower.endsWith(".zip.exe") || lower.endsWith(".mp4.exe");
    }

    /**
     * Detects typosquatted system binary names.
     */
    public boolean hasTyposquattingName(String processName) {
        if (processName == null) return false;
        String lower = processName.toLowerCase(Locale.ROOT);
        return Set.of("scvhost.exe", "svhost.exe", "lssas.exe", "csrs.exe", "taskmgrr.exe", "winlogonn.exe").contains(lower);
    }

    /**
     * Verifies if executable is located in a user-writable directory.
     */
    public boolean isUserWritableDirectory(String path) {
        if (path == null || path.isBlank()) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("\\appdata\\") || lower.contains("\\temp\\")
                || lower.contains("\\users\\public\\") || lower.contains("\\downloads\\");
    }

    /**
     * Checks for known cryptomining flag signatures.
     */
    private boolean containsMiningKeywords(String name, String cmdLine) {
        String combined = (name + " " + cmdLine).toLowerCase(Locale.ROOT);
        for (String kw : MINING_KEYWORDS) {
            if (combined.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Calculates Shannon entropy for a binary file (0.00 to 8.00).
     * High entropy (> 7.20) indicates packed code (UPX, VMProtect) or encrypted payload.
     */
    public double calculateFileEntropy(Path file) {
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            return 0.0;
        }

        String key = file.toAbsolutePath().toString();
        Double cached = entropyCache.get(key);
        if (cached != null) {
            return cached;
        }

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024 * 2]; // 2 MB sample
            int bytesRead = in.read(buffer);
            if (bytesRead <= 0) return 0.0;

            long[] frequencies = new long[256];
            for (int i = 0; i < bytesRead; i++) {
                frequencies[buffer[i] & 0xFF]++;
            }

            double entropy = 0.0;
            for (long freq : frequencies) {
                if (freq > 0) {
                    double p = (double) freq / bytesRead;
                    entropy -= p * (Math.log(p) / Math.log(2));
                }
            }
            double rounded = Math.round(entropy * 100.0) / 100.0;
            entropyCache.put(key, rounded);
            return rounded;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Verifies the Authenticode digital signature of an executable.
     */
    public SignatureRecord verifyAuthenticodeSignature(String filePath) {
        if (filePath == null || filePath.isBlank() || !Files.exists(Path.of(filePath))) {
            return new SignatureRecord(false, "File Not Found", "NotFound");
        }

        return signatureCache.computeIfAbsent(filePath, p -> {
            try {
                String cmd = String.format(
                        "Get-AuthenticodeSignature -LiteralPath '%s' | Select-Object -Property Status, @{Name='Signer'; Expression={$_.SignerCertificate.Subject}} | ConvertTo-Json",
                        p.replace("'", "''")
                );

                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", cmd);
                pb.redirectErrorStream(true);
                ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 10);
                String json = result.getOutput();

                boolean isSigned = json.contains("\"Status\":  0") || json.contains("\"Status\": 0") || json.contains("Valid");
                String signer = "Unsigned / Unknown";
                if (json.contains("\"Signer\":")) {
                    int idx = json.indexOf("\"Signer\":");
                    int startQuote = json.indexOf("\"", idx + 9);
                    int endQuote = json.indexOf("\"", startQuote + 1);
                    if (startQuote > 0 && endQuote > startQuote) {
                        signer = json.substring(startQuote + 1, endQuote).replace("\\\"", "\"");
                    }
                }

                String status = isSigned ? "Valid" : (json.contains("HashMismatch") ? "HashMismatch" : "NotSigned");
                return new SignatureRecord(isSigned, signer, status);
            } catch (Exception e) {
                log.warn("Failed to check Authenticode for {}: {}", p, e.getMessage());
                return new SignatureRecord(false, "Verification Error", "Error");
            }
        });
    }

    /**
     * Safely terminates a confirmed rogue process with absolute fail-safe protection.
     * Implements two-stage termination: graceful SIGTERM first, followed by forced kill
     * only if the process does not terminate within the grace period.
     */
    public boolean terminateRogueProcess(int pid) {
        OSProcess proc = operatingSystem.getProcess(pid);
        String name = proc != null ? proc.getName() : "PID " + pid;
        String path = proc != null ? proc.getPath() : "";

        if (isProtectedProcess(pid, name, path)) {
            log.error("FAIL-SAFE PROTECTION: Refused termination of essential OS process PID {}: {}", pid, name);
            return false;
        }

        try {
            Optional<ProcessHandle> ph = ProcessHandle.of(pid);
            if (ph.isPresent()) {
                ProcessHandle handle = ph.get();
                // Step 1: Graceful termination request (SIGTERM)
                handle.destroy();
                try {
                    handle.onExit().get(600, TimeUnit.MILLISECONDS);
                    log.info("Process gracefully terminated: PID {} ({})", pid, name);
                    suspendedPids.remove(pid);
                    return true;
                } catch (TimeoutException te) {
                    log.debug("Process PID {} did not terminate gracefully within 600ms; escalating to forced kill.", pid);
                } catch (Exception ignored) {}

                // Step 2: Force termination if still active (including child process tree)
                if (handle.isAlive()) {
                    try {
                        handle.descendants().forEach(ProcessHandle::destroyForcibly);
                    } catch (Exception ignored) {}
                    handle.destroyForcibly();
                }
            }

            // Fallback to taskkill /F /T to ensure full process tree, child threads and handles are closed
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid));
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.execute(pb, 5);

            suspendedPids.remove(pid);
            log.info("Rogue process terminated successfully: PID {} ({})", pid, name);
            return true;
        } catch (Exception e) {
            log.error("Failed to terminate process PID {}: {}", pid, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Freezes/suspends process execution immediately (stopping CPU mining and beaconing)
     * without killing it, allowing deep inspection while completely neutralizing the threat.
     */
    public boolean suspendRogueProcess(int pid) {
        OSProcess proc = operatingSystem.getProcess(pid);
        String name = proc != null ? proc.getName() : "PID " + pid;
        String path = proc != null ? proc.getPath() : "";

        if (isProtectedProcess(pid, name, path)) {
            log.error("FAIL-SAFE PROTECTION: Refused suspension of essential OS process PID {}: {}", pid, name);
            return false;
        }

        try {
            // PowerShell thread suspension via NtSuspendProcess reflection
            String script = String.format(
                    "$p = [System.Diagnostics.Process]::GetProcessById(%d); " +
                    "Add-Type -MemberDefinition '[DllImport(\"ntdll.dll\")] public static extern uint NtSuspendProcess(IntPtr h);' -Name P -Namespace N; " +
                    "[N.P]::NtSuspendProcess($p.Handle);",
                    pid
            );

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.execute(pb, 10);

            suspendedPids.add(pid);
            log.info("Rogue process suspended: PID {} ({})", pid, name);
            return true;
        } catch (Exception e) {
            log.error("Failed to suspend process PID {}: {}", pid, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Resumes a previously suspended process.
     */
    public boolean resumeRogueProcess(int pid) {
        try {
            String script = String.format(
                    "$p = [System.Diagnostics.Process]::GetProcessById(%d); " +
                    "Add-Type -MemberDefinition '[DllImport(\"ntdll.dll\")] public static extern uint NtResumeProcess(IntPtr h);' -Name P -Namespace N; " +
                    "[N.P]::NtResumeProcess($p.Handle);",
                    pid
            );

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.execute(pb, 10);

            suspendedPids.remove(pid);
            log.info("Process resumed: PID {}", pid);
            return true;
        } catch (Exception e) {
            log.error("Failed to resume process PID {}: {}", pid, e.getMessage(), e);
            return false;
        }
    }

    private boolean isForeignPublicAddress(String foreign) {
        if (foreign == null || foreign.isBlank()) return false;
        String host = foreign.split(":")[0].trim();
        if (host.equals("0.0.0.0") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("*")) {
            return false;
        }
        // Exclude private IP subnets: 10.0.0.0/8, 192.168.0.0/16, 172.16.0.0/12
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("127.")) {
            return false;
        }
        if (host.startsWith("172.")) {
            try {
                String[] parts = host.split("\\.");
                if (parts.length >= 2) {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }

    private int extractPort(String address) {
        try {
            int colonIdx = address.lastIndexOf(':');
            if (colonIdx > 0 && colonIdx < address.length() - 1) {
                return Integer.parseInt(address.substring(colonIdx + 1));
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
