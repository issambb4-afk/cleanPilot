package com.cleanpilot.service;

import com.cleanpilot.model.QuarantinedItem;
import com.cleanpilot.repository.QuarantineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service managing Tier A: Application Quarantine Vault (Internal Soft-Delete).
 * Safe-moves files into ~/.cleanpilot/quarantine/, indexes metadata in SQLite,
 * handles collision-safe restorations, and runs auto-purge policies for expired items.
 */
@Service
public class QuarantineService {

    private static final Logger log = LoggerFactory.getLogger(QuarantineService.class);
    public static final int DEFAULT_RETENTION_DAYS = 30;

    private final QuarantineRepository repository;
    private final Path quarantineDirectory;

    @org.springframework.beans.factory.annotation.Autowired
    public QuarantineService(QuarantineRepository repository) {
        this(repository, Paths.get(System.getProperty("user.home", "."), ".cleanpilot", "quarantine"));
    }

    public QuarantineService(QuarantineRepository repository, Path quarantineDirectory) {
        this.repository = repository;
        this.quarantineDirectory = quarantineDirectory;
        initVaultDirectory();
    }

    private void initVaultDirectory() {
        try {
            if (!Files.exists(quarantineDirectory)) {
                Files.createDirectories(quarantineDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to initialize quarantine vault directory {}: {}", quarantineDirectory, e.getMessage(), e);
        }
    }

    public Path getQuarantineDirectory() {
        return quarantineDirectory;
    }

    /**
     * Moves a file to the quarantine vault and records its metadata.
     *
     * @param source Source file path to quarantine
     * @return Persisted QuarantinedItem entity
     * @throws IOException If file movement or hashing fails
     */
    @Transactional
    public QuarantinedItem quarantineFile(Path source) throws IOException {
        if (source == null || !Files.exists(source)) {
            throw new IllegalArgumentException("Source file does not exist: " + source);
        }

        initVaultDirectory();
        long size = Files.size(source);
        String fileName = source.getFileName().toString();
        String hash = computeFastSha256(source);

        String uuid = UUID.randomUUID().toString();
        String safeVaultName = uuid + "_" + fileName;
        Path targetPath = quarantineDirectory.resolve(safeVaultName);

        // Move to vault (with cross-volume copy fallback)
        moveOrCopyAcrossVolumes(source, targetPath);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(DEFAULT_RETENTION_DAYS);

        QuarantinedItem item = new QuarantinedItem(
                source.toAbsolutePath().toString(),
                targetPath.toAbsolutePath().toString(),
                fileName,
                size,
                hash,
                now,
                expiresAt
        );

        return repository.save(item);
    }

    /**
     * Restores a quarantined item back to its original location (collision-safe).
     */
    @Transactional
    public Path restoreItem(Long id) throws IOException {
        QuarantinedItem item = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Quarantined item not found with id: " + id));

        Path vaultFile = Paths.get(item.getQuarantinePath());
        if (!Files.exists(vaultFile)) {
            repository.delete(item);
            throw new FileNotFoundException("Quarantined file missing from vault: " + vaultFile);
        }

        Path targetOriginal = Paths.get(item.getOriginalPath());
        Path parentDir = targetOriginal.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Check for collision at original destination
        Path finalDestination = targetOriginal;
        if (Files.exists(finalDestination)) {
            String name = item.getFileName();
            int dotIdx = name.lastIndexOf('.');
            String base = (dotIdx > 0) ? name.substring(0, dotIdx) : name;
            String ext = (dotIdx > 0) ? name.substring(dotIdx) : "";

            int counter = 1;
            do {
                String candidate = base + "_restored" + (counter > 1 ? "_" + counter : "") + ext;
                finalDestination = parentDir != null ? parentDir.resolve(candidate) : Paths.get(candidate);
                counter++;
            } while (Files.exists(finalDestination));
        }

        // Move back from vault to destination (with cross-volume copy fallback)
        moveOrCopyAcrossVolumes(vaultFile, finalDestination);

        // Remove from database
        repository.delete(item);
        log.info("Restored item {} to {}", item.getFileName(), finalDestination);
        return finalDestination;
    }

    /**
     * Permanently deletes a single item from the quarantine vault.
     */
    @Transactional
    public boolean permanentlyDeleteItem(Long id) {
        Optional<QuarantinedItem> opt = repository.findById(id);
        if (opt.isPresent()) {
            QuarantinedItem item = opt.get();
            deletePhysicalVaultFile(item.getQuarantinePath());
            repository.delete(item);
            return true;
        }
        return false;
    }

    /**
     * Alias for permanentlyDeleteItem.
     */
    @Transactional
    public boolean deleteItem(Long id) {
        return permanentlyDeleteItem(id);
    }

    /**
     * Permanently deletes all items from the quarantine vault.
     */
    @Transactional
    public int emptyVault() {
        List<QuarantinedItem> all = repository.findAll();
        int count = 0;
        for (QuarantinedItem item : all) {
            deletePhysicalVaultFile(item.getQuarantinePath());
            count++;
        }
        repository.deleteAll();
        return count;
    }

    /**
     * Retrieves all expired items according to the retention window.
     */
    public List<QuarantinedItem> getExpiredItems() {
        return repository.findExpiredItems(LocalDateTime.now());
    }

    /**
     * Purges expired items according to the retention window.
     */
    @Transactional
    public int purgeExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<QuarantinedItem> expired = repository.findExpiredItems(now);
        int purged = 0;
        for (QuarantinedItem item : expired) {
            deletePhysicalVaultFile(item.getQuarantinePath());
            purged++;
            repository.delete(item);
        }
        if (purged > 0) {
            log.info("Purged {} expired items from Quarantine Vault.", purged);
        }
        return purged;
    }

    private void deletePhysicalVaultFile(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) return;
        try {
            Path p = Paths.get(pathStr).toAbsolutePath().normalize();
            Path vaultDir = quarantineDirectory.toAbsolutePath().normalize();
            if (p.startsWith(vaultDir)) {
                Files.deleteIfExists(p);
            } else {
                log.error("SECURITY VIOLATION: Refusing to delete physical file outside vault directory: {}", p);
            }
        } catch (IOException e) {
            log.warn("Could not delete physical vault file {}: {}", pathStr, e.getMessage());
        }
    }

    public List<QuarantinedItem> listAllQuarantined() {
        return repository.findAllByOrderByDeletedAtDesc();
    }

    public List<QuarantinedItem> getAllQuarantined() {
        return listAllQuarantined();
    }

    public long getVaultItemCount() {
        return repository.count();
    }

    public long getTotalVaultBytes() {
        return repository.getTotalQuarantineSizeBytes();
    }

    private void moveOrCopyAcrossVolumes(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException amnse) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException crossDeviceEx) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(source);
            }
        } catch (IOException crossDeviceEx) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(source);
        }
    }

    private String computeFastSha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(in, md)) {
                while (dis.read(buf) != -1) {
                    // stream through digest
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("Could not compute SHA-256 for quarantine file {}: {}", file, e.getMessage());
            return "";
        }
    }
}
