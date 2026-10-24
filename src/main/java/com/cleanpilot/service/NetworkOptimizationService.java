package com.cleanpilot.service;

import com.cleanpilot.model.ActiveSocketInfo;
import com.cleanpilot.model.NetworkOptimizationResult;
import com.cleanpilot.util.ProcessExecutionUtils;
import com.cleanpilot.util.WindowsSecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Inspects active network sockets, detects socket leaks (CLOSE_WAIT / TIME_WAIT congestion),
 * and optimizes DNS cache and Winsock stack.
 */
@Service
public class NetworkOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(NetworkOptimizationService.class);

    /**
     * Inspects active TCP socket connections and identifies potential leaks.
     */
    public List<ActiveSocketInfo> scanActiveSockets() {
        List<ActiveSocketInfo> sockets = new ArrayList<>();
        Map<Integer, String> processMap = buildProcessPidMap();

        try {
            ProcessBuilder pb = new ProcessBuilder("netstat", "-ano");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), ProcessExecutionUtils.getNativeCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("TCP")) continue;

                    String[] tokens = trimmed.split("\\s+");
                    if (tokens.length >= 5) {
                        String proto = tokens[0];
                        String local = tokens[1];
                        String foreign = tokens[2];
                        String state = tokens[3];
                        int pid = 0;
                        try {
                            pid = Integer.parseInt(tokens[4]);
                        } catch (Exception ignored) {}

                        boolean isLeak = "CLOSE_WAIT".equalsIgnoreCase(state) || "TIME_WAIT".equalsIgnoreCase(state);
                        String procName = processMap.getOrDefault(pid, "System / Unknown");

                        sockets.add(new ActiveSocketInfo(proto, local, foreign, state, pid, procName, isLeak));
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            log.error("Failed to execute netstat: {}", e.getMessage());
        }

        return sockets;
    }

    /**
     * Flushes DNS resolver cache and resets Winsock catalog.
     */
    public NetworkOptimizationResult optimizeNetworkStack() {
        boolean dnsFlushed = false;
        boolean winsockReset = false;

        // 1. Try flushing DNS via PowerShell Clear-DnsClientCache
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", "Clear-DnsClientCache");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
            dnsFlushed = (proc.exitValue() == 0);
        } catch (Exception e) {
            log.warn("Clear-DnsClientCache failed: {}", e.getMessage());
        }

        // If not flushed, try elevated flushdns
        if (!dnsFlushed) {
            dnsFlushed = WindowsSecurityUtils.runElevated("ipconfig /flushdns");
        }

        // 2. Winsock Reset (requires elevation)
        if (WindowsSecurityUtils.isRunningAsAdmin()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("netsh", "winsock", "reset");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                proc.waitFor();
                winsockReset = (proc.exitValue() == 0);
            } catch (Exception ignored) {}
        }

        List<ActiveSocketInfo> sockets = scanActiveSockets();
        int closeWaitCount = (int) sockets.stream().filter(ActiveSocketInfo::isSuspectLeak).count();

        String status = dnsFlushed
                ? "DNS resolver cache cleared successfully. " + (winsockReset ? "Winsock catalog reset." : "")
                : "DNS flush requires elevation or was denied.";

        return new NetworkOptimizationResult(dnsFlushed, winsockReset, closeWaitCount, status);
    }

    private Map<Integer, String> buildProcessPidMap() {
        Map<Integer, String> map = new HashMap<>();
        try {
            ProcessHandle.allProcesses().forEach(p -> {
                int pid = (int) p.pid();
                String name = p.info().command()
                        .map(c -> new java.io.File(c).getName())
                        .orElse("Process " + pid);
                map.put(pid, name);
            });
        } catch (Exception ignored) {}
        return map;
    }
}
