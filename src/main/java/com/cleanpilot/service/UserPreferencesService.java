package com.cleanpilot.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Manages local user preferences such as theme, default scan thresholds, and excluded paths.
 */
@Service
public class UserPreferencesService {

    private static final String PREF_THEME = "cleanpilot.theme";
    private static final String PREF_LANGUAGE = "cleanpilot.language";
    private static final String PREF_MIN_LARGE_FILE_MB = "cleanpilot.min_large_file_mb";
    private static final String PREF_PREFER_TRASH = "cleanpilot.prefer_trash";
    private static final String PREF_EXCLUDED_PATHS = "cleanpilot.excluded_paths";
    private static final String PREF_AUTO_MAINTENANCE_SCHEDULE = "cleanpilot.auto_maintenance_schedule";
    private static final String PREF_AUTO_PURGE_QUARANTINE = "cleanpilot.auto_purge_quarantine";
    private static final String PREF_AUTO_CLEAN_TEMP = "cleanpilot.auto_clean_temp";
    private static final String PREF_MAINTENANCE_NOTIFICATIONS = "cleanpilot.maintenance_notifications";
    private static final String PREF_STORAGE_SENTINEL_ENABLED = "cleanpilot.storage_sentinel_enabled";

    private final Preferences prefs = Preferences.userNodeForPackage(UserPreferencesService.class);

    public String getTheme() {
        return prefs.get(PREF_THEME, "DARK");
    }

    public void setTheme(String theme) {
        prefs.put(PREF_THEME, theme);
    }

    public String getLanguage() {
        return prefs.get(PREF_LANGUAGE, "en");
    }

    public void setLanguage(String language) {
        prefs.put(PREF_LANGUAGE, language);
    }

    public long getMinLargeFileSizeMb() {
        return prefs.getLong(PREF_MIN_LARGE_FILE_MB, 100);
    }

    public void setMinLargeFileSizeMb(long mb) {
        prefs.putLong(PREF_MIN_LARGE_FILE_MB, mb);
    }

    public boolean isPreferTrash() {
        return prefs.getBoolean(PREF_PREFER_TRASH, true);
    }

    public void setPreferTrash(boolean preferTrash) {
        prefs.putBoolean(PREF_PREFER_TRASH, preferTrash);
    }

    public Set<String> getExcludedPaths() {
        String raw = prefs.get(PREF_EXCLUDED_PATHS, "");
        Set<String> set = new HashSet<>();
        if (!raw.isBlank()) {
            for (String p : raw.split(";")) {
                if (!p.isBlank()) {
                    set.add(p.trim());
                }
            }
        }
        return set;
    }

    public void setExcludedPaths(Set<String> paths) {
        if (paths == null || paths.isEmpty()) {
            prefs.put(PREF_EXCLUDED_PATHS, "");
        } else {
            prefs.put(PREF_EXCLUDED_PATHS, String.join(";", paths));
        }
    }

    public void addExcludedPath(String path) {
        Set<String> set = getExcludedPaths();
        set.add(path);
        setExcludedPaths(set);
    }

    public void removeExcludedPath(String path) {
        Set<String> set = getExcludedPaths();
        set.remove(path);
        setExcludedPaths(set);
    }

    public String getAutoMaintenanceSchedule() {
        return prefs.get(PREF_AUTO_MAINTENANCE_SCHEDULE, "DISABLED");
    }

    public void setAutoMaintenanceSchedule(String schedule) {
        prefs.put(PREF_AUTO_MAINTENANCE_SCHEDULE, schedule);
    }

    public boolean isStorageSentinelEnabled() {
        return prefs.getBoolean(PREF_STORAGE_SENTINEL_ENABLED, false);
    }

    public void setStorageSentinelEnabled(boolean val) {
        prefs.putBoolean(PREF_STORAGE_SENTINEL_ENABLED, val);
    }

    public boolean isAutoPurgeQuarantine() {
        return prefs.getBoolean(PREF_AUTO_PURGE_QUARANTINE, true);
    }

    public void setAutoPurgeQuarantine(boolean val) {
        prefs.putBoolean(PREF_AUTO_PURGE_QUARANTINE, val);
    }

    public boolean isAutoCleanTemp() {
        return prefs.getBoolean(PREF_AUTO_CLEAN_TEMP, true);
    }

    public void setAutoCleanTemp(boolean val) {
        prefs.putBoolean(PREF_AUTO_CLEAN_TEMP, val);
    }

    public boolean isMaintenanceNotifications() {
        return prefs.getBoolean(PREF_MAINTENANCE_NOTIFICATIONS, true);
    }

    public void setMaintenanceNotifications(boolean val) {
        prefs.putBoolean(PREF_MAINTENANCE_NOTIFICATIONS, val);
    }
}
