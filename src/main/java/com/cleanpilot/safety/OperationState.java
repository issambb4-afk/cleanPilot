package com.cleanpilot.safety;

/**
 * Explicit state machine for maintenance operations from discovery through completion or recovery.
 */
public enum OperationState {

    // Active lifecycle phases
    DISCOVER(false, false),
    VALIDATE(false, false),
    PREVIEW(false, false),
    AUTHORIZED(false, false),
    EXECUTING(false, false),
    VERIFYING(false, false),

    // Terminal outcome states
    COMPLETED_SUCCESSFULLY(true, true),
    COMPLETED_WITH_WARNINGS(true, true),
    PARTIALLY_COMPLETED(true, false),
    CANCELLED(true, false),
    FAILED(true, false),
    INTERRUPTED(true, false),
    RECOVERY_REQUIRED(true, false);

    private final boolean terminal;
    private final boolean success;

    OperationState(boolean terminal, boolean success) {
        this.terminal = terminal;
        this.success = success;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isSuccess() {
        return success;
    }
}