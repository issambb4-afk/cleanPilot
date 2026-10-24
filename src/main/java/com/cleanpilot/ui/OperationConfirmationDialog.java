package com.cleanpilot.ui;

import com.cleanpilot.safety.OperationPreview;
import com.cleanpilot.safety.RiskClassification;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reusable modal confirmation dialog presenting exact operation targets,
 * risk classifications, irreversible warnings, and recovery guarantees.
 */
public class OperationConfirmationDialog extends Stage {

    private final OperationPreview preview;
    private final AtomicBoolean confirmed = new AtomicBoolean(false);

    public OperationConfirmationDialog(OperationPreview preview) {
        this.preview = preview;

        initModality(Modality.APPLICATION_MODAL);
        setTitle("Confirm Maintenance Action: " + preview.operationType());
        setMinWidth(620);
        setMinHeight(460);

        buildUi();
    }

    public static boolean confirm(Window owner, OperationPreview preview) {
        OperationConfirmationDialog dialog = new OperationConfirmationDialog(preview);
        if (owner != null) dialog.initOwner(owner);
        dialog.showAndWait();
        return dialog.isConfirmed();
    }

    public boolean isConfirmed() {
        return confirmed.get();
    }

    private void buildUi() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: -color-bg-default;");

        // Header: Operation Type & Risk Badge
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(preview.operationType());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label riskBadge = new Label(preview.riskClassification().getDisplayLabel());
        riskBadge.getStyleClass().addAll("badge", preview.riskClassification().getBadgeStyleClass());

        header.getChildren().addAll(titleLabel, riskBadge);

        // Impact & Recovery Summary Card
        VBox summaryCard = new VBox(8);
        summaryCard.setPadding(new Insets(14));
        summaryCard.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-subtle; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label targetsLabel = new Label(String.format("Targets: %d item(s) selected | Estimated space: %s (%s)",
                preview.targetCount(),
                FormatUtils.formatBytes(preview.estimatedBytes()),
                preview.exactByteEstimate() ? "Exact" : "Approximate"
        ));
        targetsLabel.setStyle("-fx-font-weight: bold;");

        Label recoveryLabel = new Label("Disposition / Recovery: " + preview.recoveryMechanism());
        recoveryLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");

        summaryCard.getChildren().addAll(targetsLabel, recoveryLabel);

        // Warning banner if Irreversible or High Risk
        if (preview.irreversible() || preview.riskClassification() == RiskClassification.HIGH_RISK) {
            HBox warningBox = new HBox(8);
            warningBox.setPadding(new Insets(10));
            warningBox.setAlignment(Pos.CENTER_LEFT);
            warningBox.setStyle("-fx-background-color: -color-danger-subtle; -fx-border-color: -color-danger-emphasis; -fx-border-radius: 6px; -fx-background-radius: 6px;");

            Label warnText = new Label("⚠ CAUTION: This operation cannot be undone. Verify all targets before proceeding.");
            warnText.setStyle("-fx-text-fill: -color-danger-fg; -fx-font-weight: bold;");
            warningBox.getChildren().add(warnText);

            root.getChildren().add(warningBox);
        }

        // Target items list view
        Label targetListHeader = new Label("Affected Targets & Resources:");
        targetListHeader.setStyle("-fx-font-weight: bold;");

        ListView<String> targetListView = new ListView<>();
        targetListView.getItems().addAll(preview.targetDescriptions());
        targetListView.setPrefHeight(180);
        VBox.setVgrow(targetListView, Priority.ALWAYS);

        // Action Buttons
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> {
            confirmed.set(false);
            close();
        });

        Button confirmBtn = new Button(preview.irreversible() ? "Permanently Execute" : "Authorize & Execute");
        if (preview.irreversible() || preview.riskClassification() == RiskClassification.HIGH_RISK) {
            confirmBtn.getStyleClass().add("danger");
        } else {
            confirmBtn.getStyleClass().add("accent");
        }

        confirmBtn.setOnAction(e -> {
            confirmed.set(false);
            confirmed.set(true);
            close();
        });

        buttonBar.getChildren().addAll(cancelBtn, confirmBtn);

        root.getChildren().addAll(header, summaryCard, targetListHeader, targetListView, buttonBar);

        Scene scene = new Scene(root);
        setScene(scene);
    }
}