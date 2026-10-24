package com.cleanpilot.ui;

import com.cleanpilot.service.LocalizationService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;

/**
 * Loads FXML hierarchies where controllers are instantiated and injected by Spring ApplicationContext,
 * and sets the active ResourceBundle from LocalizationService.
 */
@Component
public class SpringFXMLLoader {

    private final ApplicationContext applicationContext;
    private final LocalizationService localizationService;

    public SpringFXMLLoader(ApplicationContext applicationContext, LocalizationService localizationService) {
        this.applicationContext = applicationContext;
        this.localizationService = localizationService;
    }

    public <T> T load(String fxmlPath) throws IOException {
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalArgumentException("Cannot find FXML file at: " + fxmlPath);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(applicationContext::getBean);
        loader.setResources(localizationService.getResourceBundle());
        return loader.load();
    }

    public <T> FXMLLoader getLoader(String fxmlPath) {
        URL resource = getClass().getResource(fxmlPath);
        if (resource == null) {
            throw new IllegalArgumentException("Cannot find FXML file at: " + fxmlPath);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(applicationContext::getBean);
        loader.setResources(localizationService.getResourceBundle());
        return loader;
    }
}
