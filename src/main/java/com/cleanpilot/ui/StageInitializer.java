package com.cleanpilot.ui;

import com.cleanpilot.event.StageReadyEvent;
import com.cleanpilot.service.LocalizationService;
import com.cleanpilot.service.SystemTrayManagerService;
import com.cleanpilot.service.UserPreferencesService;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
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
    private final SystemTrayManagerService trayManagerService;
    private final LocalizationService localizationService;
    private final UserPreferencesService preferencesService;

    public StageInitializer(
            SpringFXMLLoader fxmlLoader,
            ThemeManager themeManager,
            SystemTrayManagerService trayManagerService,
            LocalizationService localizationService,
            UserPreferencesService preferencesService
    ) {
        this.fxmlLoader = fxmlLoader;
        this.themeManager = themeManager;
        this.trayManagerService = trayManagerService;
        this.localizationService = localizationService;
        this.preferencesService = preferencesService;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        try {
            themeManager.initializeTheme();

            Stage stage = event.getStage();
            Parent root = fxmlLoader.load("/fxml/MainView.fxml");
            root.setNodeOrientation(localizationService.getNodeOrientation());

            // 1. Query visual screen bounds (respects Windows taskbar & HiDPI scaling)
            Rectangle2D primaryVisualBounds = Screen.getPrimary().getVisualBounds();
            double screenW = primaryVisualBounds.getWidth();
            double screenH = primaryVisualBounds.getHeight();

            // 2. Compute safe dimensions that are guaranteed to fit within the visible display
            double safeMaxW = screenW * 0.95;
            double safeMaxH = screenH * 0.92;

            double defaultW = Math.min(1140.0, safeMaxW);
            double defaultH = Math.min(650.0, safeMaxH);

            double minW = Math.min(840.0, screenW * 0.70);
            double minH = Math.min(520.0, screenH * 0.75);

            stage.setMinWidth(minW);
            stage.setMinHeight(minH);

            // 3. Load saved user preferences or fallback to centered bounds
            boolean startMaximized = preferencesService.isWindowMaximized();
            double targetW = preferencesService.getWindowWidth(defaultW);
            double targetH = preferencesService.getWindowHeight(defaultH);

            // Ensure window width/height never exceeds available screen visual bounds
            targetW = Math.max(minW, Math.min(targetW, safeMaxW));
            targetH = Math.max(minH, Math.min(targetH, safeMaxH));

            double defaultX = primaryVisualBounds.getMinX() + Math.max(0.0, (screenW - targetW) / 2.0);
            double defaultY = primaryVisualBounds.getMinY() + Math.max(0.0, (screenH - targetH) / 2.0);

            double targetX = preferencesService.getWindowX(defaultX);
            double targetY = preferencesService.getWindowY(defaultY);

            // CRITICAL ANTI-OFFSCREEN CLAMP:
            // targetY can NEVER be lower than visualBounds.getMinY() (which would hide the top title/menu bar)
            if (targetY < primaryVisualBounds.getMinY()) {
                targetY = primaryVisualBounds.getMinY();
            }
            if (targetY + 80.0 > primaryVisualBounds.getMaxY()) {
                targetY = primaryVisualBounds.getMinY() + 20.0;
            }
            if (targetX < primaryVisualBounds.getMinX() || targetX > primaryVisualBounds.getMaxX() - 100.0) {
                targetX = defaultX;
            }

            Scene scene = new Scene(root, targetW, targetH);
            String customCss = getClass().getResource("/css/app.css") != null
                    ? getClass().getResource("/css/app.css").toExternalForm()
                    : null;
            if (customCss != null) {
                scene.getStylesheets().add(customCss);
            }

            stage.setTitle("CleanPilot - Honest & Privacy-First System Optimizer");
            stage.getIcons().addAll(
                    createAppIcon(16),
                    createAppIcon(32),
                    createAppIcon(48),
                    createAppIcon(64),
                    createAppIcon(128)
            );
            stage.setScene(scene);

            // Apply safe coordinates and dimensions before showing
            stage.setWidth(targetW);
            stage.setHeight(targetH);
            stage.setX(targetX);
            stage.setY(targetY);

            // 4. Continuous clamp listener: prevents Windows or restore events from pushing top bar off-screen
            stage.yProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !stage.isMaximized() && !stage.isIconified()) {
                    Rectangle2D bounds = getActiveScreenBounds(stage);
                    if (newVal.doubleValue() < bounds.getMinY()) {
                        Platform.runLater(() -> stage.setY(bounds.getMinY()));
                    }
                }
            });

            // 5. Window state persistence
            stage.maximizedProperty().addListener((obs, oldVal, isMax) -> {
                preferencesService.setWindowMaximized(isMax);
            });

            stage.showingProperty().addListener((obs, oldVal, showing) -> {
                if (!showing) {
                    saveWindowBounds(stage);
                }
            });

            stage.setOnCloseRequest(e -> saveWindowBounds(stage));

            if (startMaximized) {
                stage.setMaximized(true);
            }

            stage.show();

            // Post-show guarantee: ensure top bar is fully visible within screen bounds
            if (!stage.isMaximized() && stage.getY() < primaryVisualBounds.getMinY()) {
                stage.setY(primaryVisualBounds.getMinY());
            }

            trayManagerService.setupTray(stage);

            log.info("CleanPilot Desktop UI started successfully. Screen: {}x{}, Window: {}x{} at ({},{})",
                    (int) screenW, (int) screenH, (int) targetW, (int) targetH, (int) stage.getX(), (int) stage.getY());
        } catch (Exception e) {
            log.error("Failed to initialize JavaFX Primary Stage: {}", e.getMessage(), e);
        }
    }

    private Rectangle2D getActiveScreenBounds(Stage stage) {
        try {
            ObservableList<Screen> screens = Screen.getScreensForRectangle(
                    stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()
            );
            if (!screens.isEmpty()) {
                return screens.get(0).getVisualBounds();
            }
        } catch (Exception ignored) {
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private void saveWindowBounds(Stage stage) {
        if (!stage.isMaximized() && !stage.isIconified() && stage.getWidth() > 300 && stage.getHeight() > 300) {
            preferencesService.setWindowWidth(stage.getWidth());
            preferencesService.setWindowHeight(stage.getHeight());
            preferencesService.setWindowX(stage.getX());
            preferencesService.setWindowY(stage.getY());
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
