package com.cleanpilot.model;

import com.cleanpilot.ui.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Metadata for a software package installed on the Windows operating system.
 */
public class InstalledAppInfo {

    private final String id;
    private final String displayName;
    private final String displayVersion;
    private final String publisher;
    private final String installDate;
    private final String installLocation;
    private final String uninstallString;
    private final String quietUninstallString;
    private final long estimatedSizeBytes;
    private final boolean is64Bit;
    private final String registryKey;
    private final List<AppResidualInfo> residualFolders = new ArrayList<>();

    public InstalledAppInfo(
            String displayName,
            String displayVersion,
            String publisher,
            String installDate,
            String installLocation,
            String uninstallString,
            String quietUninstallString,
            long estimatedSizeBytes,
            boolean is64Bit,
            String registryKey
    ) {
        this.id = UUID.randomUUID().toString();
        this.displayName = displayName;
        this.displayVersion = displayVersion != null && !displayVersion.isBlank() ? displayVersion : "N/A";
        this.publisher = publisher != null && !publisher.isBlank() ? publisher : "Unknown Publisher";
        this.installDate = installDate != null && !installDate.isBlank() ? installDate : "Unknown";
        this.installLocation = installLocation;
        this.uninstallString = uninstallString;
        this.quietUninstallString = quietUninstallString;
        this.estimatedSizeBytes = estimatedSizeBytes;
        this.is64Bit = is64Bit;
        this.registryKey = registryKey;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayVersion() {
        return displayVersion;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getInstallDate() {
        return installDate;
    }

    public String getInstallLocation() {
        return installLocation != null ? installLocation : "";
    }

    public String getUninstallString() {
        return uninstallString;
    }

    public String getQuietUninstallString() {
        return quietUninstallString;
    }

    public long getEstimatedSizeBytes() {
        return estimatedSizeBytes;
    }

    public boolean is64Bit() {
        return is64Bit;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public List<AppResidualInfo> getResidualFolders() {
        return residualFolders;
    }

    public String getFormattedSize() {
        return estimatedSizeBytes > 0 ? FormatUtils.formatBytes(estimatedSizeBytes) : "Unknown";
    }

    public boolean hasQuietUninstall() {
        return quietUninstallString != null && !quietUninstallString.isBlank();
    }
}
