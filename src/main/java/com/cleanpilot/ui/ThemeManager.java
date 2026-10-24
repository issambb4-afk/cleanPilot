package com.cleanpilot.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.cleanpilot.service.UserPreferencesService;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    private final UserPreferencesService preferencesService;
    private boolean isDark = true;

    public ThemeManager(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    public void initializeTheme() {
        String saved = preferencesService.getTheme();
        setTheme("DARK".equalsIgnoreCase(saved));
    }

    public void setTheme(boolean dark) {
        this.isDark = dark;
        preferencesService.setTheme(dark ? "DARK" : "LIGHT");
        try {
            if (dark) {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            } else {
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            }
        } catch (Throwable t) {
            log.warn("Could not apply AtlantaFX stylesheet: {}", t.getMessage());
        }
    }

    public void toggleTheme() {
        setTheme(!isDark);
    }

    public boolean isDark() {
        return isDark;
    }
}
