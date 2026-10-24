package com.cleanpilot.safety;

import java.util.Collections;
import java.util.List;

/**
 * Validated immutable preview detailing the exact targets, estimated impact,
 * required permissions, and consequences before executing a maintenance action.
 */
public record OperationPreview(
        String operationId,
        String operationType,
        RiskClassification riskClassification,
        int targetCount,
        long estimatedBytes,
        boolean exactByteEstimate,
        List<String> targetDescriptions,
        List<String> excludedTargets,
        List<String> warnings,
        boolean requiresElevation,
        boolean irreversible,
        String recoveryMechanism
) {
    public OperationPreview {
        targetDescriptions = targetDescriptions != null ? List.copyOf(targetDescriptions) : Collections.emptyList();
        excludedTargets = excludedTargets != null ? List.copyOf(excludedTargets) : Collections.emptyList();
        warnings = warnings != null ? List.copyOf(warnings) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String operationType, RiskClassification risk) {
        return new Builder(operationType, risk);
    }

    public static class Builder {
        private String operationType;
        private RiskClassification risk;
        private String operationId;
        private int targetCount;
        private long estimatedBytes;
        private boolean exactByteEstimate = true;
        private List<String> targetDescriptions = Collections.emptyList();
        private List<String> excludedTargets = Collections.emptyList();
        private List<String> warnings = Collections.emptyList();
        private boolean requiresElevation;
        private boolean irreversible;
        private String recoveryMechanism = "CleanPilot Quarantine Vault (Soft-Delete)";

        public Builder() {
            this("MAINTENANCE_OPERATION", RiskClassification.MODERATE_RISK);
        }

        public Builder(String operationType, RiskClassification risk) {
            this.operationType = operationType;
            this.risk = risk;
            this.requiresElevation = risk != null && risk.isRequiresElevation();
            this.irreversible = risk != null && risk.isIrreversible();
        }

        public Builder operationType(String operationType) {
            this.operationType = operationType;
            return this;
        }

        public Builder riskClassification(RiskClassification risk) {
            this.risk = risk;
            if (risk != null) {
                this.requiresElevation = risk.isRequiresElevation();
                this.irreversible = risk.isIrreversible();
            }
            return this;
        }

        public Builder operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public Builder targets(List<String> targets) {
            this.targetDescriptions = targets != null ? targets : Collections.emptyList();
            this.targetCount = this.targetDescriptions.size();
            return this;
        }

        public Builder targetDescriptions(List<String> targets) {
            return targets(targets);
        }

        public Builder targetPaths(List<java.nio.file.Path> paths) {
            if (paths == null) {
                return targets(Collections.emptyList());
            }
            return targets(paths.stream().map(java.nio.file.Path::toString).toList());
        }

        public Builder targetCount(int count) {
            this.targetCount = count;
            return this;
        }

        public Builder estimatedBytes(long bytes) {
            this.estimatedBytes = bytes;
            return this;
        }

        public Builder estimatedBytes(long bytes, boolean exact) {
            this.estimatedBytes = bytes;
            this.exactByteEstimate = exact;
            return this;
        }

        public Builder exactByteEstimate(boolean exact) {
            this.exactByteEstimate = exact;
            return this;
        }

        public Builder addWarning(String warning) {
            if (this.warnings.isEmpty() || !(this.warnings instanceof java.util.ArrayList)) {
                this.warnings = new java.util.ArrayList<>(this.warnings);
            }
            this.warnings.add(warning);
            return this;
        }

        public Builder excludedTargets(List<String> excluded) {
            this.excludedTargets = excluded;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public Builder requiresElevation(boolean elevation) {
            this.requiresElevation = elevation;
            return this;
        }

        public Builder irreversible(boolean irreversible) {
            this.irreversible = irreversible;
            return this;
        }

        public Builder recoveryMechanism(String mechanism) {
            this.recoveryMechanism = mechanism;
            return this;
        }

        public OperationPreview build() {
            return new OperationPreview(
                    operationId != null ? operationId : java.util.UUID.randomUUID().toString(),
                    operationType,
                    risk,
                    targetCount,
                    estimatedBytes,
                    exactByteEstimate,
                    targetDescriptions,
                    excludedTargets,
                    warnings,
                    requiresElevation,
                    irreversible,
                    recoveryMechanism
            );
        }
    }
}