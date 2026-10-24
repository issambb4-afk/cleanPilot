package com.cleanpilot.service;

import com.cleanpilot.model.HardwareDriverInfo;
import com.cleanpilot.util.ProcessExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.Display;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.hardware.UsbDevice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers and inspects installed drivers and physical/virtual hardware devices.
 * Evaluates operational status, device errors (ConfigManager codes), digital signatures,
 * and hardware compatibility with the laptop/system.
 */
@Service
public class HardwareDriverService {

    private static final Logger log = LoggerFactory.getLogger(HardwareDriverService.class);

    private final HardwareAbstractionLayer oshiHardware;
    private final boolean isWindows;
    private final Map<String, HardwareDriverInfo> cachedDrivers = new ConcurrentHashMap<>();

    public HardwareDriverService() {
        SystemInfo systemInfo = new SystemInfo();
        this.oshiHardware = systemInfo.getHardware();
        this.isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Scans and returns all detected hardware devices and installed system drivers.
     */
    public List<HardwareDriverInfo> scanHardwareAndDrivers() {
        List<HardwareDriverInfo> results;
        if (isWindows) {
            results = scanWindowsHardwareAndDrivers();
        } else {
            results = scanCrossPlatformHardware();
        }

        // Cache results by device ID
        cachedDrivers.clear();
        for (HardwareDriverInfo info : results) {
            if (info.deviceId() != null) {
                cachedDrivers.put(info.deviceId(), info);
            }
        }
        return results;
    }

    /**
     * Looks up an individual driver by its device ID.
     */
    public Optional<HardwareDriverInfo> findByDeviceId(String deviceId) {
        if (deviceId == null) return Optional.empty();
        return Optional.ofNullable(cachedDrivers.get(deviceId));
    }

    /**
     * Backs up a single INF driver package to a target directory using Windows pnputil.
     * Standard user account permissions are supported.
     */
    public boolean exportDriverPackage(String infName, String destinationDir) {
        if (infName == null || infName.isBlank() || "N/A".equalsIgnoreCase(infName)) {
            log.warn("Cannot export driver: invalid or missing infName '{}'", infName);
            return false;
        }
        try {
            java.io.File dest = new java.io.File(destinationDir);
            if (!dest.exists()) {
                dest.mkdirs();
            }
            ProcessBuilder pb = new ProcessBuilder("pnputil", "/export-driver", infName, destinationDir);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 30);
            return result.isSuccess();
        } catch (Exception e) {
            log.error("Failed to export driver package {}: {}", infName, e.getMessage());
            return false;
        }
    }

    /**
     * Exports all third-party OEM driver packages from the Windows Driver Store to a backup folder.
     */
    public boolean backupAllDrivers(String destinationDir) {
        try {
            java.io.File dest = new java.io.File(destinationDir);
            if (!dest.exists()) {
                dest.mkdirs();
            }
            ProcessBuilder pb = new ProcessBuilder("pnputil", "/export-driver", "*", destinationDir);
            pb.redirectErrorStream(true);
            ProcessExecutionUtils.ProcessResult result = ProcessExecutionUtils.execute(pb, 180);
            return result.isSuccess();
        } catch (Exception e) {
            log.error("Failed to backup all driver packages: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gathers Windows devices and drivers via dual CIM query joining Win32_PnPSignedDriver with Win32_PnPEntity.
     * This captures authentic driver version, release date, digital signer, and ConfigManager error codes.
     */
    private List<HardwareDriverInfo> scanWindowsHardwareAndDrivers() {
        Map<String, PnpEntityStatus> entityMap = queryPnPEntities();

        String driverScript = "Get-CimInstance Win32_PnPSignedDriver | Where-Object DeviceClass | Where-Object DeviceName | " +
                "Select-Object DeviceName, DeviceID, HardWareID, DriverVersion, DriverDate, IsSigned, DriverProviderName, Signer, DeviceClass, InfName | " +
                "ConvertTo-Json -Compress";

        List<HardwareDriverInfo> list = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", driverScript);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0 && sb.length() > 2) {
                list = parseSignedDriversJson(sb.toString(), entityMap);
            } else {
                process.destroyForcibly();
                log.warn("PowerShell driver query timed out or failed, falling back to OSHI hardware scan");
                list = scanCrossPlatformHardware();
            }
        } catch (Exception e) {
            log.error("Failed to query Windows drivers via PowerShell: {}", e.getMessage(), e);
            list = scanCrossPlatformHardware();
        }

        if (list.isEmpty()) {
            list = scanCrossPlatformHardware();
        }

        return list;
    }

    private record PnpEntityStatus(String deviceId, int errorCode, String status, boolean present, String service, String manufacturer) {}

    /**
     * Queries Win32_PnPEntity to extract actual device error codes and operational state.
     */
    private Map<String, PnpEntityStatus> queryPnPEntities() {
        Map<String, PnpEntityStatus> map = new HashMap<>();
        String entityScript = "Get-CimInstance Win32_PnPEntity | Select-Object DeviceID, ConfigManagerErrorCode, Status, Present, Service, Manufacturer | ConvertTo-Json -Compress";

        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", entityScript);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            boolean finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0 && sb.length() > 2) {
                parsePnpEntitiesJson(sb.toString(), map);
            }
        } catch (Exception e) {
            log.warn("Failed to query Win32_PnPEntity: {}", e.getMessage());
        }
        return map;
    }

    private void parsePnpEntitiesJson(String json, Map<String, PnpEntityStatus> map) {
        List<String> objects = splitJsonObjects(json);
        for (String objJson : objects) {
            String deviceId = extractJsonString(objJson, "DeviceID");
            if (!deviceId.isBlank()) {
                int errorCode = extractJsonInt(objJson, "ConfigManagerErrorCode", 0);
                String status = extractJsonString(objJson, "Status");
                boolean present = extractJsonBool(objJson, "Present", true);
                String service = extractJsonString(objJson, "Service");
                String manufacturer = extractJsonString(objJson, "Manufacturer");
                map.put(deviceId, new PnpEntityStatus(deviceId, errorCode, status.isBlank() ? "OK" : status, present, service, manufacturer));
            }
        }
    }

    /**
     * Parses the JSON array of driver objects returned from PowerShell Win32_PnPSignedDriver.
     */
    private List<HardwareDriverInfo> parseSignedDriversJson(String json, Map<String, PnpEntityStatus> entityMap) {
        List<HardwareDriverInfo> result = new ArrayList<>();
        List<String> objects = splitJsonObjects(json);

        for (String objJson : objects) {
            HardwareDriverInfo item = parseSingleDriverItem(objJson, entityMap);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private List<String> splitJsonObjects(String json) {
        List<String> list = new ArrayList<>();
        if (!json.startsWith("[")) {
            if (json.startsWith("{")) {
                list.add(json);
            }
            return list;
        }

        int depth = 0;
        int startIndex = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) startIndex = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && startIndex != -1) {
                    list.add(json.substring(startIndex, i + 1));
                    startIndex = -1;
                }
            }
        }
        return list;
    }

    private HardwareDriverInfo parseSingleDriverItem(String objJson, Map<String, PnpEntityStatus> entityMap) {
        String name = extractJsonString(objJson, "DeviceName");
        String deviceId = extractJsonString(objJson, "DeviceID");
        String hardwareId = extractJsonString(objJson, "HardWareID");
        String driverVersion = extractJsonString(objJson, "DriverVersion");
        String rawDate = extractJsonString(objJson, "DriverDate");
        boolean isSigned = extractJsonBool(objJson, "IsSigned", false);
        String driverProvider = extractJsonString(objJson, "DriverProviderName");
        String signer = extractJsonString(objJson, "Signer");
        String pnpClass = extractJsonString(objJson, "DeviceClass");
        String infName = extractJsonString(objJson, "InfName");

        if (name.isBlank() && !deviceId.isBlank()) {
            name = deviceId;
        }
        if (pnpClass.isBlank()) {
            pnpClass = "Other";
        }

        // Format Date from /Date(millis)/ or raw ISO
        String driverDate = parseDateString(rawDate);

        // Match with PnPEntity status if available
        int errorCode = 0;
        String status = "OK";
        boolean present = true;
        String service = "";
        String manufacturer = driverProvider;

        PnpEntityStatus entity = entityMap.get(deviceId);
        if (entity != null) {
            errorCode = entity.errorCode();
            status = entity.status();
            present = entity.present();
            if (entity.service() != null && !entity.service().isBlank()) {
                service = entity.service();
            }
            if (entity.manufacturer() != null && !entity.manufacturer().isBlank()) {
                manufacturer = entity.manufacturer();
            }
        }

        String compatibility = evaluateCompatibility(status, errorCode, present, isSigned, driverDate);
        String problemDesc = resolveProblemDescription(errorCode, status, present);

        return new HardwareDriverInfo(
                name,
                deviceId,
                status,
                errorCode,
                manufacturer,
                service,
                present,
                pnpClass,
                driverProvider,
                driverVersion,
                driverDate,
                isSigned,
                compatibility,
                problemDesc,
                infName,
                signer,
                hardwareId
        );
    }

    /**
     * Converts "/Date(1150848000000)/" or Unix epoch millis to "yyyy-MM-dd"
     */
    public String parseDateString(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "N/A";
        }
        try {
            if (rawDate.contains("/Date(") || rawDate.contains("Date(")) {
                int start = rawDate.indexOf('(') + 1;
                int end = rawDate.indexOf(')');
                if (start > 0 && end > start) {
                    long millis = Long.parseLong(rawDate.substring(start, end));
                    return java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toString();
                }
            } else if (rawDate.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                return rawDate.substring(0, 10);
            }
        } catch (Exception ignored) {}
        return rawDate;
    }

    /**
     * Determines the compatibility status for the device/driver with the laptop hardware.
     */
    public String evaluateCompatibility(String status, int errorCode, boolean isPresent, boolean isSigned, String driverDate) {
        if (errorCode != 0) {
            if (errorCode == 22) {
                return "Disabled in Windows (Code 22)";
            } else if (errorCode == 28) {
                return "Incompatible / Missing Driver (Code 28)";
            } else if (errorCode == 10 || errorCode == 43) {
                return "Hardware / Driver Failure (Code " + errorCode + ")";
            } else if (errorCode == 45) {
                return "Disconnected / Phantom (Code 45)";
            }
            return "Configuration Error (Code " + errorCode + ")";
        }

        if (!"OK".equalsIgnoreCase(status) && !"".equalsIgnoreCase(status)) {
            return "Degraded / " + status;
        }

        if (!isPresent) {
            return "Disconnected Device";
        }

        // Check if legacy driver or unsigned
        if (!isSigned) {
            return "Operational (Unsigned Driver)";
        }

        return "Compatible & Operational";
    }

    /**
     * Provides a clear human-readable explanation of error codes and operational state.
     */
    public String resolveProblemDescription(int errorCode, String status, boolean isPresent) {
        return switch (errorCode) {
            case 0 -> isPresent ? "This device is working properly and recognized by the system bus."
                               : "Device is configured in registry but not currently connected.";
            case 1 -> "The device is not configured correctly (Code 1). Update or reinstall the driver.";
            case 10 -> "This device cannot start (Code 10). Possible hardware fault or driver incompatibility with this laptop.";
            case 12 -> "This device cannot find enough free resources that it can use (Code 12). Resource conflict with motherboard IRQ/DMA.";
            case 14 -> "This device cannot work properly until you restart your computer (Code 14).";
            case 18 -> "Reinstall the drivers for this device (Code 18).";
            case 22 -> "This device is disabled by the user or system policy (Code 22). Enable the device to use it.";
            case 28 -> "The drivers for this device are not installed (Code 28). Compatibility driver download required.";
            case 31 -> "This device is not working properly because Windows cannot load the drivers required for this device (Code 31).";
            case 32 -> "A driver (service) for this device has been disabled (Code 32). An alternate driver may be providing this functionality.";
            case 39 -> "Windows cannot load the device driver for this hardware. The driver may be corrupted or missing (Code 39).";
            case 43 -> "Windows has stopped this device because it has reported problems (Code 43). Likely GPU/hardware glitch or firmware conflict.";
            case 45 -> "Currently, this hardware device is not connected to the computer (Code 45). Reconnect the peripheral.";
            case 48 -> "The software for this device has been blocked from starting because it is known to have issues with Windows (Code 48).";
            default -> "Operational status: " + status + " (ConfigManager error code " + errorCode + ").";
        };
    }

    /**
     * Cross-platform fallback using OSHI (Linux / macOS / BSD).
     */
    private List<HardwareDriverInfo> scanCrossPlatformHardware() {
        List<HardwareDriverInfo> list = new ArrayList<>();

        // Graphics Cards
        for (GraphicsCard card : oshiHardware.getGraphicsCards()) {
            list.add(new HardwareDriverInfo(
                    card.getName(),
                    card.getDeviceId(),
                    "OK",
                    0,
                    card.getVendor(),
                    "display",
                    true,
                    "Display",
                    card.getVendor(),
                    card.getVersionInfo(),
                    "",
                    true,
                    "Compatible & Operational",
                    "Graphics adapter active and responding.",
                    "",
                    card.getVendor(),
                    card.getDeviceId()
            ));
        }

        // Network Interfaces
        for (NetworkIF net : oshiHardware.getNetworkIFs()) {
            boolean up = net.getIfOperStatus() == NetworkIF.IfOperStatus.UP;
            list.add(new HardwareDriverInfo(
                    net.getDisplayName(),
                    net.getName(),
                    up ? "OK" : "Inactive",
                    0,
                    "Network Adapter",
                    "net",
                    true,
                    "Net",
                    "System Driver",
                    "",
                    "",
                    true,
                    up ? "Compatible & Operational" : "Disconnected / Inactive",
                    "MAC: " + net.getMacaddr() + " - Speed: " + (net.getSpeed() / 1_000_000) + " Mbps",
                    "",
                    "Microsoft Windows",
                    net.getMacaddr()
            ));
        }

        // Displays
        for (Display disp : oshiHardware.getDisplays()) {
            list.add(new HardwareDriverInfo(
                    "Display Monitor",
                    "MONITOR_" + Integer.toHexString(disp.hashCode()),
                    "OK",
                    0,
                    "Display Vendor",
                    "monitor",
                    true,
                    "Monitor",
                    "Generic Monitor Driver",
                    "",
                    "",
                    true,
                    "Compatible & Operational",
                    "Laptop built-in or external display panel.",
                    "",
                    "Standard Monitor",
                    ""
            ));
        }

        // USB Controllers & Devices
        for (UsbDevice usb : oshiHardware.getUsbDevices(true)) {
            collectUsbDevices(usb, list);
        }

        return list;
    }

    private void collectUsbDevices(UsbDevice usb, List<HardwareDriverInfo> list) {
        if (usb == null) return;
        if (!usb.getName().isBlank()) {
            list.add(new HardwareDriverInfo(
                    usb.getName(),
                    usb.getUniqueDeviceId(),
                    "OK",
                    0,
                    usb.getVendor(),
                    "usb",
                    true,
                    "USB",
                    usb.getVendor(),
                    "",
                    "",
                    true,
                    "Compatible & Operational",
                    "Connected USB peripheral (Vendor ID: " + usb.getVendorId() + ", Product ID: " + usb.getProductId() + ")",
                    "",
                    usb.getVendor(),
                    "USB\\VID_" + usb.getVendorId() + "&PID_" + usb.getProductId()
            ));
        }
        for (UsbDevice child : usb.getConnectedDevices()) {
            collectUsbDevices(child, list);
        }
    }

    // JSON Helper Extraction Functions
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            // Check for null or boolean/int
            String nullPattern = "\"" + key + "\":null";
            if (json.contains(nullPattern)) return "";
            return "";
        }
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return unescapeJson(json.substring(start, end));
    }

    private int extractJsonInt(String json, String key, int defaultVal) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultVal;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private boolean extractJsonBool(String json, String key, boolean defaultVal) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return defaultVal;
        int start = idx + pattern.length();
        if (json.startsWith("true", start)) return true;
        if (json.startsWith("false", start)) return false;
        return defaultVal;
    }

    private String unescapeJson(String input) {
        return input.replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\/", "/")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
    }
}
