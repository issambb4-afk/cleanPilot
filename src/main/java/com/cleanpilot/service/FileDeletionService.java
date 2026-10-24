package com.cleanpilot.service;

import com.cleanpilot.model.ScannedFileItem;
import com.cleanpilot.safety.RiskClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Executes safe file removals via native system trash, with audited fallback to permanent deletion.
 */
@Service
public class FileDeletionService {

    private static final Logger log = LoggerFactory.getLogger(FileDeletionService.class);

    private final QuarantineService quarantineService;
    private final OperationLifecycleManager lifecycleManager;

    public FileDeletionService() {
        this(null, null);
    }

    public FileDeletionService(@org.springframework.context.annotation.Lazy QuarantineService quarantineService) {
        this(quarantineService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FileDeletionService(@org.springframework.context.annotation.Lazy QuarantineService quarantineService,
                               @org.springframework.context.annotation.Lazy OperationLifecycleManager lifecycleManager) {
        this.quarantineService = quarantineService;
        this.lifecycleManager = lifecycleManager;
    }

    public record DeletionResult(
            Path path,
            boolean success,
            boolean trashed,
            long freedBytes,
            String message
    ) {
    }

    /**
     * Checks whether the current operating system and desktop environment supports native trash.
     */
    public boolean isNativeTrashSupported() {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates that the path is not a critical operating system file or root directory.
     */
    public boolean isSafeDeletablePath(Path path) {
        if (path == null) return false;
        Path normalized = path.toAbsolutePath().normalize();

        // 1. Root or shallow directory check: must have at least 2 segments (e.g. C:\folder\file)
        if (normalized.getNameCount() < 2) {
            return false;
        }

        String pathStr = normalized.toString().toLowerCase(Locale.ROOT);

        // 2. Reject drive roots, e.g. C:\ or D:\
        if (normalized.getParent() == null) {
            return false;
        }

        // 3. Reject Windows OS directories
        String sysRoot = System.getenv("SystemRoot");
        if (sysRoot == null || sysRoot.isBlank()) {
            sysRoot = "C:\\Windows";
        }
        String normSysRoot = Path.of(sysRoot).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
        if (pathStr.equals(normSysRoot) || pathStr.startsWith(normSysRoot + File.separator)) {
            return false;
        }

        // 4. Reject Program Files roots and subdirectories
        String progFiles = System.getenv("ProgramFiles");
        if (progFiles != null) {
            String pf = Path.of(progFiles).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (pathStr.equals(pf) || pathStr.startsWith(pf + File.separator)) return false;
        }
        String progFilesX86 = System.getenv("ProgramFiles(x86)");
        if (progFilesX86 != null) {
            String pfx = Path.of(progFilesX86).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (pathStr.equals(pfx) || pathStr.startsWith(pfx + File.separator)) return false;
        }

        // 5. Reject User Home directory root directly
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            String uh = Path.of(userHome).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (pathStr.equals(uh)) return false;
        }

        // 6. Reject critical OS boot/paging/kernel files
        Path fileName = normalized.getFileName();
        if (fileName != null) {
            String fn = fileName.toString().toLowerCase(Locale.ROOT);
            if (fn.equals("pagefile.sys") || fn.equals("swapfile.sys") || fn.equals("hiberfil.sys")
                    || fn.equals("bootmgr") || fn.equals("bootnxt") || fn.equals("ntldr")
                    || fn.equals("ntoskrnl.exe") || fn.equals("hal.dll")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Moves a file to the CleanPilot Application Quarantine Vault (Soft-Delete).
     */
    public DeletionResult quarantineFile(Path path) {
        if (path == null) {
            return new DeletionResult(null, false, false, 0, "Null path provided");
        }
        if (!Files.exists(path)) {
            return new DeletionResult(path, false, false, 0, "File does not exist: " + path);
        }

        if (!isSafeDeletablePath(path)) {
            log.error("SAFETY VIOLATION: Refusing to quarantine protected system path: {}", path);
            return new DeletionResult(path, false, false, 0, "SAFETY VIOLATION: Protected system path");
        }

        try {
            long size = Files.size(path);
            if (quarantineService != null) {
                quarantineService.quarantineFile(path);
            }
            log.info("Safely moved to CleanPilot Quarantine Vault: {}", path);
            return new DeletionResult(path, true, true, size, "Moved to Quarantine Vault");
        } catch (Exception e) {
            log.warn("Quarantine move failed for {}: {}", path, e.getMessage());
            return new DeletionResult(path, false, false, 0, "Quarantine failed: " + e.getMessage());
        }
    }

    /**
     * Attempts to delete a single file, prioritizing the native operating system recycle bin / trash.
     *
     * @param path                   File path to delete
     * @param preferTrash            If true, attempts native trash first
     * @param allowPermanentFallback If true and trash fails or is unsupported, performs hard deletion
     */
    public DeletionResult deleteFile(Path path, boolean preferTrash, boolean allowPermanentFallback) {
        if (path == null) {
            return new DeletionResult(null, false, false, 0, "Null path provided");
        }
        if (!Files.exists(path)) {
            return new DeletionResult(path, false, false, 0, "File does not exist: " + path);
        }

        if (!isSafeDeletablePath(path)) {
            log.error("SAFETY VIOLATION: Refusing to delete protected system path: {}", path);
            return new DeletionResult(path, false, false, 0, "SAFETY VIOLATION: Protected system path");
        }

        long fileSize = 0;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            log.debug("Could not read size of {}: {}", path, e.getMessage());
        }

        // 1. Attempt native desktop trash
        if (preferTrash) {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                        File file = path.toFile();
                        boolean trashed = desktop.moveToTrash(file);
                        if (trashed) {
                            log.info("Safely moved to native trash: {}", path);
                            return new DeletionResult(path, true, true, fileSize, "Moved to Trash");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Desktop.moveToTrash failed for {}: {}", path, e.getMessage());
            }

            if (!allowPermanentFallback) {
                return new DeletionResult(path, false, false, 0,
                        "Native trash is unsupported or failed, and permanent deletion was not authorized.");
            }
        }

        // 2. Permanent deletion
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                log.info("Permanently deleted: {}", path);
                return new DeletionResult(path, true, false, fileSize, "Permanently deleted");
            } else {
                return new DeletionResult(path, false, false, 0, "File could not be deleted (not found)");
            }
        } catch (Exception e) {
            log.error("Failed to delete {}: {}", path, e.getMessage());
            return new DeletionResult(path, false, false, 0, "Deletion failed: " + e.getMessage());
        }
    }

    /**
     * Executes batch file deletion.
     */
    public List<DeletionResult> deleteFiles(List<ScannedFileItem> items, boolean preferTrash, boolean allowPermanentFallback) {
        List<DeletionResult> results = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return results;
        }

        String opId = lifecycleManager != null ?
                lifecycleManager.startOperation(
                        preferTrash ? "TRASH_FILE_DELETION" : "PERMANENT_FILE_DELETION",
                        allowPermanentFallback ? RiskClassification.IRREVERSIBLE : RiskClassification.MODERATE_RISK,
                        items.size() + " files"
                ) : null;
        if (opId != null) lifecycleManager.beginExecution(opId);

        long totalFreed = 0;
        for (ScannedFileItem item : items) {
            DeletionResult res = deleteFile(item.getPath(), preferTrash, allowPermanentFallback);
            results.add(res);
            if (opId != null) {
                lifecycleManager.recordItemProgress(opId, item.getPath().toString(), res.success(), res.freedBytes(), res.message());
            }
            if (res.success()) {
                totalFreed += res.freedBytes();
            }
        }

        if (opId != null) {
            lifecycleManager.completeOperation(opId, totalFreed, Collections.emptyList());
        }

        return results;
    }
}