package com.cleanpilot;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CleanPilotApplication {

    public static void main(String[] args) {
        Application.launch(CleanPilotFxApp.class, args);
    }
}
