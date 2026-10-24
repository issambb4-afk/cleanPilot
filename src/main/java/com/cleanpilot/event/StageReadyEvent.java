package com.cleanpilot.event;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when the primary JavaFX Stage is initialized and ready for display.
 */
public class StageReadyEvent extends ApplicationEvent {

    private final Stage stage;
    private final java.util.List<String> rawArguments;

    public StageReadyEvent(Stage stage) {
        this(stage, java.util.List.of());
    }

    public StageReadyEvent(Stage stage, java.util.List<String> rawArguments) {
        super(stage);
        this.stage = stage;
        this.rawArguments = rawArguments != null ? rawArguments : java.util.List.of();
    }

    public Stage getStage() {
        return stage;
    }

    public java.util.List<String> getRawArguments() {
        return rawArguments;
    }
}
