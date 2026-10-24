package com.cleanpilot.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Model representing a startup application configured to launch automatically at Windows boot.
 */
public class StartupAppInfo {

    private final String name;
    private final String command;
    private final String location; // e.g. "HKCU\\Run", "HKLM\\Run", "Startup Folder"
    private final String publisher;
    private final String impact; // "HIGH", "MEDIUM", "LOW"
    private final BooleanProperty enabled = new SimpleBooleanProperty();

    public StartupAppInfo(String name, String command, String location, String publisher, String impact, boolean enabled) {
        this.name = name;
        this.command = command;
        this.location = location;
        this.publisher = publisher;
        this.impact = impact;
        this.enabled.set(enabled);
    }

    public String getName() {
        return name;
    }

    public String getCommand() {
        return command;
    }

    public String getLocation() {
        return location;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getImpact() {
        return impact;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }
}
