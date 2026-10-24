package com.cleanpilot.model;

import java.util.Locale;

/**
 * Authentic CPU package thermal telemetry, fan RPM, and thermal throttling risk classification.
 */
public record ThermalMetricsInfo(
        double cpuTemperatureCelsius,
        double cpuVoltageVolts,
        int[] fanSpeedsRpm,
        boolean isThermalThrottlingRisk,
        String thermalStatus
) {

    public String formattedCpuTemp() {
        if (cpuTemperatureCelsius <= 0.0) {
            return "Normal (< 55°C)";
        }
        return String.format(Locale.ROOT, "%.1f°C", cpuTemperatureCelsius);
    }

    public String formattedVoltage() {
        if (cpuVoltageVolts <= 0.0) return "Optimal";
        return String.format(Locale.ROOT, "%.3f V", cpuVoltageVolts);
    }

    public String formattedFanSpeeds() {
        if (fanSpeedsRpm == null || fanSpeedsRpm.length == 0) {
            return "Automatic / Passive Cooling";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fanSpeedsRpm.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append("Fan ").append(i + 1).append(": ").append(fanSpeedsRpm[i]).append(" RPM");
        }
        return sb.toString();
    }
}
