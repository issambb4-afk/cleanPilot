package com.cleanpilot.service;

import javafx.geometry.NodeOrientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages internationalization (i18n), resource bundles, and text localization
 * supporting English (en), French (fr), and Arabic (ar with RTL).
 */
@Service
public class LocalizationService {

    private static final Logger log = LoggerFactory.getLogger(LocalizationService.class);
    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    public static final Locale LOCALE_EN = Locale.ENGLISH;
    public static final Locale LOCALE_FR = Locale.FRENCH;
    public static final Locale LOCALE_AR = Locale.forLanguageTag("ar");

    private final UserPreferencesService preferencesService;
    private final List<Consumer<Locale>> listeners = new CopyOnWriteArrayList<>();

    private Locale currentLocale;
    private ResourceBundle resourceBundle;

    public LocalizationService(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
        String savedLang = preferencesService.getLanguage();
        this.currentLocale = parseLocale(savedLang);
        reloadBundle();
    }

    private Locale parseLocale(String langCode) {
        if (langCode == null) return LOCALE_EN;
        return switch (langCode.toLowerCase(Locale.ROOT)) {
            case "fr", "fr_fr" -> LOCALE_FR;
            case "ar", "ar_sa", "ar_eg" -> LOCALE_AR;
            default -> LOCALE_EN;
        };
    }

    public synchronized void reloadBundle() {
        try {
            this.resourceBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, currentLocale);
        } catch (MissingResourceException e) {
            log.warn("Resource bundle not found for locale {}. Falling back to default.", currentLocale, e);
            try {
                this.resourceBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, LOCALE_EN);
            } catch (Exception ex) {
                log.error("Could not load fallback bundle.", ex);
            }
        }
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            reloadBundle();
        }
        return resourceBundle;
    }

    public synchronized void setLocale(Locale locale) {
        if (locale == null) locale = LOCALE_EN;
        if (!locale.getLanguage().equalsIgnoreCase(currentLocale.getLanguage())) {
            this.currentLocale = locale;
            preferencesService.setLanguage(locale.getLanguage());
            reloadBundle();
            notifyListeners();
        }
    }

    public void setLanguage(String langCode) {
        setLocale(parseLocale(langCode));
    }

    public boolean isRtl() {
        return "ar".equalsIgnoreCase(currentLocale.getLanguage());
    }

    public NodeOrientation getNodeOrientation() {
        return isRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT;
    }

    public String get(String key) {
        if (key == null) return "";
        try {
            if (resourceBundle != null && resourceBundle.containsKey(key)) {
                return resourceBundle.getString(key);
            }
        } catch (Exception e) {
            log.debug("Error looking up localization key: {}", key);
        }
        return key;
    }

    public String get(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            log.warn("Failed to format message pattern '{}' with arguments: {}", pattern, Arrays.toString(args));
            return pattern;
        }
    }

    public String format(String key, Object... args) {
        return get(key, args);
    }

    public void addLanguageChangeListener(Consumer<Locale> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeLanguageChangeListener(Consumer<Locale> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Consumer<Locale> listener : listeners) {
            try {
                listener.accept(currentLocale);
            } catch (Exception e) {
                log.error("Error in language change listener", e);
            }
        }
    }
}
