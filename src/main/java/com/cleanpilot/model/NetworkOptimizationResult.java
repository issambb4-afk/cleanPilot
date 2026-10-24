package com.cleanpilot.model;

/**
 * Result data snapshot of network and DNS cache optimization.
 */
public record NetworkOptimizationResult(
        boolean dnsFlushed,
        boolean winsockReset,
        int closedWaitSocketsCount,
        String statusMessage
) {}
