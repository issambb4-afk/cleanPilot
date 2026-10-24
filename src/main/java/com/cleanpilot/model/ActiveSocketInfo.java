package com.cleanpilot.model;

/**
 * Encapsulates an active network TCP connection / socket leak.
 */
public record ActiveSocketInfo(
        String protocol,
        String localAddress,
        String foreignAddress,
        String state,
        int pid,
        String processName,
        boolean isSuspectLeak
) {
    public String displayProcess() {
        return (processName != null && !processName.isBlank()) ? processName + " (PID " + pid + ")" : "PID " + pid;
    }
}
