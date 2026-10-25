package com.cleanpilot.service;

import com.cleanpilot.model.LicenseDetails;
import com.cleanpilot.model.LicenseStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * Enterprise-grade Lemon Squeezy Licensing Service for CleanPilot Pro.
 * Provides online activation/deactivation and tamper-proof offline license caching.
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private static final String PREF_KEY = "cleanpilot.license.key";
    private static final String PREF_INSTANCE_ID = "cleanpilot.license.instance_id";
    private static final String PREF_CUSTOMER_NAME = "cleanpilot.license.customer_name";
    private static final String PREF_CUSTOMER_EMAIL = "cleanpilot.license.customer_email";
    private static final String PREF_PRODUCT_NAME = "cleanpilot.license.product_name";
    private static final String PREF_STATUS = "cleanpilot.license.status";
    private static final String PREF_ACTIVATED_AT = "cleanpilot.license.activated_at";
    private static final String PREF_EXPIRES_AT = "cleanpilot.license.expires_at";
    private static final String PREF_SIGNATURE = "cleanpilot.license.signature";

    // Tamper-resistance salt mixed with machine attributes
    private static final String INTEGRITY_SALT = "CleanPilot_Pro_Tamper_Shield_2026_x86_64";

    private final String lemonApiBaseUrl;
    private final String checkoutUrl;
    private final Preferences prefs;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile LicenseDetails cachedDetails;

    public LicenseService(
            @Value("${cleanpilot.licensing.lemon-squeezy-api-url:https://api.lemonsqueezy.com/v1/licenses}") String lemonApiBaseUrl,
            @Value("${cleanpilot.licensing.checkout-url:https://cleanpilot.lemonsqueezy.com/checkout/buy/cleanpilot-pro}") String checkoutUrl
    ) {
        this.lemonApiBaseUrl = lemonApiBaseUrl;
        this.checkoutUrl = checkoutUrl;
        this.prefs = Preferences.userNodeForPackage(LicenseService.class);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();

        // Load and verify local license from disk/registry
        this.cachedDetails = loadLocalLicense();
    }

    /**
     * Checks if CleanPilot Pro features are unlocked.
     */
    public boolean isPro() {
        return cachedDetails != null && cachedDetails.isPro();
    }

    /**
     * Retrieves current active license metadata.
     */
    public LicenseDetails getLicenseDetails() {
        return cachedDetails;
    }

    /**
     * Opens the official Lemon Squeezy checkout page in the system browser.
     */
    public void openPurchasePage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(checkoutUrl));
            } else {
                new ProcessBuilder("cmd", "/c", "start", checkoutUrl).start();
            }
        } catch (Exception e) {
            log.error("Failed to open checkout URL: {}", e.getMessage());
        }
    }

    /**
     * Activates a license key with Lemon Squeezy License API.
     *
     * @param rawKey The license key entered by the user.
     * @return Future containing activation success or error message.
     */
    public CompletableFuture<ActivationResult> activateLicense(String rawKey) {
        if (rawKey == null || rawKey.trim().isEmpty()) {
            return CompletableFuture.completedFuture(new ActivationResult(false, "License key cannot be empty."));
        }

        final String licenseKey = rawKey.trim();
        final String instanceName = generateInstanceIdentifier();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = lemonApiBaseUrl + "/activate";
                String formBody = "license_key=" + URLEncoder.encode(licenseKey, StandardCharsets.UTF_8)
                        + "&instance_name=" + URLEncoder.encode(instanceName, StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(formBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode root = objectMapper.readTree(response.body());
                    boolean activated = root.path("activated").asBoolean(false);

                    if (activated) {
                        String instanceId = root.path("instance").path("id").asText("");
                        JsonNode meta = root.path("meta");
                        String customerName = meta.path("user_name").asText("CleanPilot Pro Customer");
                        String customerEmail = meta.path("user_email").asText("");
                        String productName = meta.path("product_name").asText("CleanPilot Pro");

                        JsonNode licNode = root.path("license_key");
                        String expiresAtStr = licNode.path("expires_at").asText(null);
                        Instant expiresAt = (expiresAtStr != null && !expiresAtStr.equalsIgnoreCase("null") && !expiresAtStr.isBlank())
                                ? Instant.parse(expiresAtStr)
                                : null;

                        LicenseDetails details = new LicenseDetails(
                                licenseKey,
                                instanceId,
                                customerName,
                                customerEmail,
                                productName,
                                LicenseStatus.ACTIVE_PRO,
                                Instant.now(),
                                expiresAt
                        );

                        saveLocalLicense(details);
                        this.cachedDetails = details;
                        log.info("CleanPilot Pro activated successfully for {}", customerName);
                        return new ActivationResult(true, "CleanPilot Pro activated successfully!");
                    } else {
                        String errorMsg = root.path("error").asText("License activation failed.");
                        return new ActivationResult(false, errorMsg);
                    }
                } else {
                    return new ActivationResult(false, "Activation server responded with HTTP error: " + response.statusCode());
                }
            } catch (Exception ex) {
                log.error("License activation failed: {}", ex.getMessage());
                return new ActivationResult(false, "Connection error: Unable to reach activation server. (" + ex.getMessage() + ")");
            }
        });
    }

    /**
     * Deactivates the current active license key with Lemon Squeezy to free up the instance limit.
     */
    public CompletableFuture<Boolean> deactivateLicense() {
        if (!isPro() || cachedDetails == null) {
            return CompletableFuture.completedFuture(true);
        }

        final String licenseKey = cachedDetails.key();
        final String instanceId = cachedDetails.instanceId();

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!instanceId.isBlank()) {
                    String endpoint = lemonApiBaseUrl + "/deactivate";
                    String formBody = "license_key=" + URLEncoder.encode(licenseKey, StandardCharsets.UTF_8)
                            + "&instance_id=" + URLEncoder.encode(instanceId, StandardCharsets.UTF_8);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.ofString(formBody))
                            .build();

                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                }
            } catch (Exception e) {
                log.warn("Remote deactivation request failed: {}", e.getMessage());
            }

            clearLocalLicense();
            this.cachedDetails = LicenseDetails.free();
            log.info("License deactivated locally.");
            return true;
        });
    }

    // =========================================================================
    // LOCAL TAMPER-PROOF PERSISTENCE & OFFLINE VERIFICATION
    // =========================================================================

    private LicenseDetails loadLocalLicense() {
        String key = prefs.get(PREF_KEY, "");
        String statusStr = prefs.get(PREF_STATUS, LicenseStatus.FREE.name());
        String instanceId = prefs.get(PREF_INSTANCE_ID, "");
        String customerName = prefs.get(PREF_CUSTOMER_NAME, "");
        String customerEmail = prefs.get(PREF_CUSTOMER_EMAIL, "");
        String productName = prefs.get(PREF_PRODUCT_NAME, "CleanPilot Community");
        String activatedAtStr = prefs.get(PREF_ACTIVATED_AT, "");
        String expiresAtStr = prefs.get(PREF_EXPIRES_AT, "");
        String storedSignature = prefs.get(PREF_SIGNATURE, "");

        if (key.isBlank() || !LicenseStatus.ACTIVE_PRO.name().equalsIgnoreCase(statusStr)) {
            return LicenseDetails.free();
        }

        // Verify cryptographic integrity hash to protect against local registry edits
        String expectedSignature = calculateIntegritySignature(key, instanceId, customerEmail);
        if (!expectedSignature.equalsIgnoreCase(storedSignature)) {
            log.warn("Tampered or corrupted local license detected. Reverting to Community edition.");
            clearLocalLicense();
            return LicenseDetails.free();
        }

        Instant activatedAt = !activatedAtStr.isBlank() ? Instant.parse(activatedAtStr) : null;
        Instant expiresAt = !expiresAtStr.isBlank() ? Instant.parse(expiresAtStr) : null;

        // Check expiration date if not perpetual
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return new LicenseDetails(key, instanceId, customerName, customerEmail, productName, LicenseStatus.EXPIRED, activatedAt, expiresAt);
        }

        return new LicenseDetails(key, instanceId, customerName, customerEmail, productName, LicenseStatus.ACTIVE_PRO, activatedAt, expiresAt);
    }

    private void saveLocalLicense(LicenseDetails details) {
        prefs.put(PREF_KEY, details.key());
        prefs.put(PREF_INSTANCE_ID, details.instanceId());
        prefs.put(PREF_CUSTOMER_NAME, details.customerName());
        prefs.put(PREF_CUSTOMER_EMAIL, details.customerEmail());
        prefs.put(PREF_PRODUCT_NAME, details.productName());
        prefs.put(PREF_STATUS, details.status().name());
        prefs.put(PREF_ACTIVATED_AT, details.activatedAt() != null ? details.activatedAt().toString() : "");
        prefs.put(PREF_EXPIRES_AT, details.expiresAt() != null ? details.expiresAt().toString() : "");

        String signature = calculateIntegritySignature(details.key(), details.instanceId(), details.customerEmail());
        prefs.put(PREF_SIGNATURE, signature);
    }

    private void clearLocalLicense() {
        prefs.remove(PREF_KEY);
        prefs.remove(PREF_INSTANCE_ID);
        prefs.remove(PREF_CUSTOMER_NAME);
        prefs.remove(PREF_CUSTOMER_EMAIL);
        prefs.remove(PREF_PRODUCT_NAME);
        prefs.remove(PREF_STATUS);
        prefs.remove(PREF_ACTIVATED_AT);
        prefs.remove(PREF_EXPIRES_AT);
        prefs.remove(PREF_SIGNATURE);
    }

    private String calculateIntegritySignature(String key, String instanceId, String email) {
        try {
            String machineId = System.getenv("COMPUTERNAME") + ":" + System.getProperty("user.name");
            String payload = key + ":" + instanceId + ":" + email + ":" + machineId + ":" + INTEGRITY_SALT;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "signature_error";
        }
    }

    private String generateInstanceIdentifier() {
        String pcName = System.getenv("COMPUTERNAME");
        if (pcName == null || pcName.isBlank()) {
            pcName = "Windows-PC";
        }
        return pcName + " (" + System.getProperty("user.name") + ")";
    }

    public record ActivationResult(boolean success, String message) {}
}
