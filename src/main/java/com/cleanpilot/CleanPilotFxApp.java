package com.cleanpilot;

import com.cleanpilot.event.StageReadyEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class CleanPilotFxApp extends Application {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        try {
            java.nio.file.Path appDir = java.nio.file.Paths.get(System.getProperty("user.home", "."), ".cleanpilot");
            if (!java.nio.file.Files.exists(appDir)) {
                java.nio.file.Files.createDirectories(appDir);
            }
        } catch (Exception ignored) {
        }
        String[] args = getParameters().getRaw().toArray(new String[0]);
        this.applicationContext = new SpringApplicationBuilder()
                .sources(CleanPilotApplication.class)
                .headless(false)
                .run(args);
    }

    @Override
    public void start(Stage stage) {
        Thread.currentThread().setUncaughtExceptionHandler((t, ex) -> {
            if (ex instanceof java.util.concurrent.RejectedExecutionException) {
                // Ignore benign executor rejection during shutdown/context tear-down
                return;
            }
            org.slf4j.LoggerFactory.getLogger(CleanPilotFxApp.class)
                    .error("Uncaught exception on JavaFX Application Thread: {}", ex.getMessage(), ex);
        });

        applicationContext.publishEvent(new StageReadyEvent(stage, getParameters().getRaw()));
    }

    @Override
    public void stop() {
        if (applicationContext != null) {
            try {
                applicationContext.close();
            } catch (Exception ignored) {
            }
        }
        Platform.exit();
    }
}
