package com.cleanpilot.model;

import java.time.LocalDateTime;

/**
 * Represents a critical operating system crash or hardware event parsed from the System Event Log
 * (e.g., BSOD BugCheck, Kernel-Power dirty shutdown, or disk hardware controller error).
 */
public record SystemStabilityEvent(
        LocalDateTime timestamp,
        String eventType,
        int eventId,
        String source,
        String description
) {
}
