package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.Locale;

/**
 * Encapsulates authentic battery diagnostics: design vs max capacity wear,
 * power draw rate, cycle counts, voltage, and remaining runtime.
 */
public record BatteryHealthInfo(
        boolean hasBattery,
        String name,
        String deviceName,
        String chemistry,
        double remainingPercent, // 0.0 to 100.0
        boolean isPluggedIn,
        boolean isCharging,
        boolean isDischarging,
        double estimatedTimeRemainingSeconds,
        double powerUsageWatts,
        double voltageVolts,
        double amperageMa,
        int currentCapacityMah,
        int maxCapacityMah,
        int designCapacityMah,
        double healthPercentage, // (maxCapacity / designCapacity) * 100
        int cycleCount,
        double temperatureCelsius,
        String healthStatus
) {

    public static BatteryHealthInfo noBattery() {
        return new BatteryHealthInfo(
                false,
                "No Battery Detected",
                "Desktop / Direct AC",
                "N/A",
                100.0,
                true,
                false,
                false,
                -1,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                100.0,
                -1,
                0.0,
                "AC Desktop / Direct Power"
        );
    }

    public String formattedRemaining() {
        return String.format(Locale.ROOT, "%.1f%%", remainingPercent);
    }

    public String formattedHealth() {
        if (healthPercentage <= 0) return "N/A (OS Protected)";
        return String.format(Locale.ROOT, "%.1f%%", healthPercentage);
    }

    public String formattedPowerRate() {
        if (powerUsageWatts == 0) return "0.00 W";
        return String.format(Locale.ROOT, "%.2f W", powerUsageWatts);
    }

    public String formattedVoltage() {
        if (voltageVolts <= 0) return "-";
        return String.format(Locale.ROOT, "%.2f V", voltageVolts);
    }

    public String formattedCycleCount() {
        if (cycleCount < 0) return "Not Reported by ACPI";
        return cycleCount + " cycles";
    }

    public String formattedTimeRemaining() {
        if (isPluggedIn && remainingPercent >= 99.0) {
            return "Fully Charged (AC Connected)";
        }
        if (isPluggedIn && isCharging) {
            if (estimatedTimeRemainingSeconds > 0) {
                return "Charging (" + FormatUtils.formatDuration((long) (estimatedTimeRemainingSeconds * 1000)) + " to full)";
            }
            return "Charging on AC...";
        }
        if (estimatedTimeRemainingSeconds > 0) {
            return FormatUtils.formatDuration((long) (estimatedTimeRemainingSeconds * 1000)) + " remaining";
        }
        return isPluggedIn ? "AC Connected (Not Discharging)" : "Calculating remaining runtime...";
    }
}
