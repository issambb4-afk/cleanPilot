package com.cleanpilot.service;

@FunctionalInterface
public interface ScanProgressListener {

    void onProgress(long filesScanned, long bytesScanned, String currentPath);

    default void onThroughput(long filesScanned, long bytesScanned, String currentPath, double filesPerSecond, double bytesPerSecond) {
        onProgress(filesScanned, bytesScanned, currentPath);
    }

    default void onStatus(String statusMessage) {
        // Optional status update
    }
}
