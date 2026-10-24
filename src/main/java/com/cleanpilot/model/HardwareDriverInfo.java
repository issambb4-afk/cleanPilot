package com.cleanpilot.model;

/**
 * Encapsulates authentic hardware device and driver metadata gathered directly from
 * OS hardware abstraction layers (WMI/CIM, sysfs/udev, IORegistry).
 *
 * @param name Hardware or device display name
 * @param deviceId Unique Plug and Play hardware / device instance path (e.g. PCI\VEN_8086...)
 * @param status Operational status reported by the OS (e.g. "OK", "Error", "Degraded")
 * @param errorCode ConfigManager problem error code (0 = CM_PROB_NONE / healthy)
 * @param manufacturer Device vendor or manufacturer name
 * @param service Driver kernel service name (e.g. "BTHUSB", "Netwtw10", "stornvme")
 * @param isPresent Whether the device is physically connected and recognized by the bus
 * @param pnpClass Hardware device class (e.g. "Net", "Display", "SCSIAdapter", "USB", "System")
 * @param driverProvider Driver software provider (e.g. "Intel Corporation", "Microsoft", "NVIDIA")
 * @param driverVersion Installed driver file version string
 * @param driverDate Release date of the installed driver package
 * @param isSigned Whether the driver binary is digitally signed and validated
 * @param compatibilityStatus Computed compatibility evaluation ("Compatible & Operational", "Warning: Disabled/Phantom", "Error: Driver Fault", "Legacy / Outdated")
 * @param problemDescription Clear human-readable description of error or compatibility state
 */
public record HardwareDriverInfo(
        String name,
        String deviceId,
        String status,
        int errorCode,
        String manufacturer,
        String service,
        boolean isPresent,
        String pnpClass,
        String driverProvider,
        String driverVersion,
        String driverDate,
        boolean isSigned,
        String compatibilityStatus,
        String problemDescription,
        String infName,
        String signer,
        String hardwareId
) {

    /**
     * Helper to check if the driver/device is in a healthy, operational state.
     */
    public boolean isHealthy() {
        return errorCode == 0 && "OK".equalsIgnoreCase(status);
    }

    /**
     * Formats error code with label.
     */
    public String formattedErrorCode() {
        if (errorCode == 0) {
            return "Code 0 (CM_PROB_NONE)";
        }
        return "Code " + errorCode;
    }

    /**
     * Clean manufacturer string with fallback.
     */
    public String displayManufacturer() {
        return (manufacturer != null && !manufacturer.isBlank()) ? manufacturer : "Standard / Generic";
    }

    /**
     * Clean driver provider with fallback.
     */
    public String displayDriverProvider() {
        return (driverProvider != null && !driverProvider.isBlank()) ? driverProvider : "Unknown Provider";
    }

    /**
     * Clean driver version with fallback.
     */
    public String displayDriverVersion() {
        return (driverVersion != null && !driverVersion.isBlank()) ? driverVersion : "N/A";
    }

    /**
     * Clean digital signer string with fallback.
     */
    public String displaySigner() {
        return (signer != null && !signer.isBlank()) ? signer : (isSigned ? "WHQL / Verified Signer" : "None / Unsigned");
    }

    /**
     * Clean INF package name.
     */
    public String displayInfName() {
        return (infName != null && !infName.isBlank()) ? infName : "N/A";
    }

    /**
     * Clean Hardware ID.
     */
    public String displayHardwareId() {
        return (hardwareId != null && !hardwareId.isBlank()) ? hardwareId : "N/A";
    }

    /**
     * Checks if the driver is older than 5 years based on its driverDate string.
     */
    public boolean isOutdated() {
        if (driverDate == null || driverDate.isBlank() || "N/A".equalsIgnoreCase(driverDate)) {
            return false;
        }
        try {
            // DriverDate format is "yyyy-MM-dd"
            if (driverDate.length() >= 4) {
                int year = Integer.parseInt(driverDate.substring(0, 4));
                int currentYear = java.time.LocalDate.now().getYear();
                return (currentYear - year) >= 5;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
