package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

/**
 * Storage device hardware health: model, I/O wear, active queue length, and bottleneck detection.
 */
public record StorageHealthInfo(
        String name,
        String model,
        String serial,
        long sizeBytes,
        long bytesRead,
        long bytesWritten,
        long readOps,
        long writeOps,
        long currentQueueLength,
        long transferTimeMs,
        String healthStatus
) {

    public String formattedSize() {
        return FormatUtils.formatBytes(sizeBytes);
    }

    public String formattedBytesRead() {
        return FormatUtils.formatBytes(bytesRead);
    }

    public String formattedBytesWritten() {
        return FormatUtils.formatBytes(bytesWritten);
    }

    public String formattedTotalTransfer() {
        return FormatUtils.formatBytes(bytesRead + bytesWritten);
    }

    public boolean isQueueBottleneck() {
        return currentQueueLength >= 4;
    }
}
