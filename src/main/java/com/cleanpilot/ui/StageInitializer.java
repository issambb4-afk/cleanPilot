package com.cleanpilot.ui;

import com.cleanpilot.event.StageReadyEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StageInitializer.class);

    private final SpringFXMLLoader fxmlLoader;
    private final ThemeManager themeManager;
    private final com.cleanpilot.service.SystemTrayManagerService trayManagerService;
    private final com.cleanpilot.service.LocalizationService localizationService;

    public StageInitializer(
            SpringFXMLLoader fxmlLoader,
            ThemeManager themeManager,
            com.cleanpilot.service.SystemTrayManagerService trayManagerService,
            com.cleanpilot.service.LocalizationService localizationService
    ) {
        this.fxmlLoader = fxmlLoader;
        this.themeManager = themeManager;
        this.trayManagerService = trayManagerService;
        this.localizationService = localizationService;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        try {
            themeManager.initializeTheme();

            Stage stage = event.getStage();
            Parent root = fxmlLoader.load("/fxml/MainView.fxml");
            root.setNodeOrientation(localizationService.getNodeOrientation());

            Scene scene = new Scene(root, 1180, 780);
            String customCss = getClass().getResource("/css/app.css") != null
                    ? getClass().getResource("/css/app.css").toExternalForm()
                    : null;
            if (customCss != null) {
                scene.getStylesheets().add(customCss);
            }

            stage.setTitle("CleanPilot - Honest & Privacy-First System Optimizer");
            stage.setMinWidth(960);
            stage.setMinHeight(640);
            stage.getIcons().addAll(
                    createAppIcon(16),
                    createAppIcon(32),
                    createAppIcon(48),
                    createAppIcon(64),
                    createAppIcon(128)
            );
            stage.setScene(scene);
            stage.show();

            trayManagerService.setupTray(stage);

            log.info("CleanPilot Desktop UI started successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize JavaFX Primary Stage: {}", e.getMessage(), e);
        }
    }

    private javafx.scene.image.Image createAppIcon(int size) {
        String resourcePath = "/icons/cleanpilot-" + size + "x" + size + ".png";
        java.io.InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream != null) {
            try (stream) {
                return new javafx.scene.image.Image(stream);
            } catch (Exception ignored) {
            }
        }

        // Fallback programmatic canvas icon
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(size, size);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        // Modern vibrant gradient background with rounded corners
        javafx.scene.paint.LinearGradient bg = new javafx.scene.paint.LinearGradient(
                0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.web("#2563eb")),
                new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.web("#6366f1"))
        );
        gc.setFill(bg);
        double arc = size * 0.32;
        gc.fillRoundRect(0, 0, size, size, arc, arc);

        // White monogram
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, size * 0.60));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        gc.fillText("C", size / 2.0, size / 2.0);

        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }
}
