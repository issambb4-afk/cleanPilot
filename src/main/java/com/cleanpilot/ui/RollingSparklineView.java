package com.cleanpilot.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.LinkedList;

/**
 * Lightweight rolling historical sparkline chart component (60 samples)
 * rendering smooth real-time CPU or RAM consumption.
 */
public class RollingSparklineView extends Pane {

    private final Canvas canvas = new Canvas();
    private final LinkedList<Double> history = new LinkedList<>();
    private final int maxPoints = 60;
    private Color strokeColor = Color.web("#0969da");
    private Color fillColor = Color.web("#0969da", 0.2);

    public RollingSparklineView() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        widthProperty().addListener((obs, o, n) -> render());
        heightProperty().addListener((obs, o, n) -> render());

        // Pre-fill history with zeroes
        for (int i = 0; i < maxPoints; i++) {
            history.add(0.0);
        }
    }

    public void setColors(Color stroke, Color fill) {
        this.strokeColor = stroke;
        this.fillColor = fill;
        render();
    }

    public void addSample(double value) {
        if (history.size() >= maxPoints) {
            history.pollFirst();
        }
        history.addLast(Math.max(0.0, Math.min(100.0, value)));
        render();
    }

    public void render() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // Draw subtle grid lines
        gc.setStroke(Color.web("#8c959f", 0.15));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, h * 0.25, w, h * 0.25);
        gc.strokeLine(0, h * 0.50, w, h * 0.50);
        gc.strokeLine(0, h * 0.75, w, h * 0.75);

        if (history.size() < 2) return;

        double stepX = w / (maxPoints - 1);

        // Draw filled polygon under line
        gc.beginPath();
        gc.moveTo(0, h);

        double x = 0;
        for (Double pt : history) {
            double y = h - (pt / 100.0 * (h - 4)) - 2;
            gc.lineTo(x, y);
            x += stepX;
        }
        gc.lineTo(w, h);
        gc.closePath();
        gc.setFill(fillColor);
        gc.fill();

        // Draw line
        gc.beginPath();
        x = 0;
        boolean first = true;
        for (Double pt : history) {
            double y = h - (pt / 100.0 * (h - 4)) - 2;
            if (first) {
                gc.moveTo(x, y);
                first = false;
            } else {
                gc.lineTo(x, y);
            }
            x += stepX;
        }
        gc.setStroke(strokeColor);
        gc.setLineWidth(2.0);
        gc.stroke();
    }
}
