package com.cleanpilot.ui;

import com.cleanpilot.model.HardwareDriverInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advanced modal dialog for deep-inspection of hardware devices, driver binaries,
 * PnP subsystem properties, error codes, and laptop compatibility verdicts.
 */
public class DriverDetailsDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(DriverDetailsDialog.class);

    private final HardwareDriverInfo driverInfo;
    private final com.cleanpilot.service.HardwareDriverService driverService;
    private final java.util.concurrent.ExecutorService executorService;

    public DriverDetailsDialog(HardwareDriverInfo driverInfo,
                               com.cleanpilot.service.HardwareDriverService driverService,
                               java.util.concurrent.ExecutorService executorService) {
        this.driverInfo = driverInfo;
        this.driverService = driverService;
        this.executorService = executorService;
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Hardware & Driver Inspector - " + driverInfo.name());
        setMinWidth(740);
        setMinHeight(580);
        setWidth(780);
        setHeight(620);

        buildUi();
    }

    public static void show(Window owner,
                            HardwareDriverInfo driverInfo,
                            com.cleanpilot.service.HardwareDriverService driverService,
                            java.util.concurrent.ExecutorService executorService) {
        if (driverInfo == null) return;
        DriverDetailsDialog dialog = new DriverDetailsDialog(driverInfo, driverService, executorService);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.show();
    }

    private void buildUi() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(16));

        // Header Section
        VBox headerBox = new VBox(8);
        headerBox.setPadding(new Insets(0, 0, 14, 0));

        HBox titleRow = new HBox(12);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(resolveClassIcon(driverInfo.pnpClass()));
        iconLabel.setStyle("-fx-font-size: 2em;");

        VBox titleCol = new VBox(2);
        Label titleLabel = new Label(driverInfo.name());
        titleLabel.setStyle("-fx-font-size: 1.3em; -fx-font-weight: bold;");
        titleLabel.setWrapText(true);

        Label subLabel = new Label("Class: " + driverInfo.pnpClass() + " • Vendor: " + driverInfo.displayManufacturer());
        subLabel.getStyleClass().add("text-subtle");

        titleCol.getChildren().addAll(titleLabel, subLabel);
        HBox.setHgrow(titleCol, Priority.ALWAYS);

        // Status Badges
        HBox badgeBox = new HBox(6);
        badgeBox.setAlignment(Pos.CENTER_RIGHT);

        Label statusBadge = new Label(driverInfo.status());
        statusBadge.getStyleClass().addAll("badge", driverInfo.isHealthy() ? "badge-subtle" : "badge-danger");
        if (driverInfo.isHealthy()) {
            statusBadge.setStyle("-fx-background-color: #2da44e22; -fx-text-fill: #2da44e; -fx-font-weight: bold;");
        } else {
            statusBadge.setStyle("-fx-background-color: #cf222e22; -fx-text-fill: #cf222e; -fx-font-weight: bold;");
        }

        Label signedBadge = new Label(driverInfo.isSigned() ? "✓ Signed" : "⚠ Unsigned");
        signedBadge.getStyleClass().addAll("badge");
        if (driverInfo.isSigned()) {
            signedBadge.setStyle("-fx-background-color: #0969da22; -fx-text-fill: #0969da;");
        } else {
            signedBadge.setStyle("-fx-background-color: #d4a72c22; -fx-text-fill: #9a6700;");
        }

        badgeBox.getChildren().addAll(statusBadge, signedBadge);
        titleRow.getChildren().addAll(iconLabel, titleCol, badgeBox);

        // Compatibility Notice Banner
        VBox bannerBox = new VBox(4);
        bannerBox.setPadding(new Insets(10, 14, 10, 14));
        bannerBox.setStyle("-fx-background-radius: 6; -fx-border-radius: 6;");

        if (driverInfo.isHealthy()) {
            bannerBox.setStyle(bannerBox.getStyle() + "-fx-background-color: #2da44e15; -fx-border-color: #2da44e55; -fx-border-width: 1;");
            Label compHeader = new Label("✔ " + driverInfo.compatibilityStatus());
            compHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #2da44e; -fx-font-size: 1.05em;");
            Label compText = new Label(driverInfo.problemDescription());
            compText.setWrapText(true);
            bannerBox.getChildren().addAll(compHeader, compText);
        } else {
            bannerBox.setStyle(bannerBox.getStyle() + "-fx-background-color: #cf222e15; -fx-border-color: #cf222e55; -fx-border-width: 1;");
            Label compHeader = new Label("✖ " + driverInfo.compatibilityStatus() + " (" + driverInfo.formattedErrorCode() + ")");
            compHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #cf222e; -fx-font-size: 1.05em;");
            Label compText = new Label(driverInfo.problemDescription());
            compText.setWrapText(true);
            bannerBox.getChildren().addAll(compHeader, compText);
        }

        headerBox.getChildren().addAll(titleRow, bannerBox);
        root.setTop(headerBox);

        // Center Tabs: Device Information & Driver Details
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Driver Properties
        Tab driverTab = new Tab("Driver Details");
        GridPane driverGrid = createPropertyGrid();
        addPropertyRow(driverGrid, 0, "Driver Provider:", driverInfo.displayDriverProvider());
        addPropertyRow(driverGrid, 1, "Driver Version:", driverInfo.displayDriverVersion());
        addPropertyRow(driverGrid, 2, "Driver Release Date:", driverInfo.driverDate().isBlank() ? "Not specified" : driverInfo.driverDate());
        addPropertyRow(driverGrid, 3, "Digital Signer:", driverInfo.displaySigner());
        addPropertyRow(driverGrid, 4, "Digital Signature Status:", driverInfo.isSigned() ? "Verified Authenticode Certificate (WHQL / Vendor Signed)" : "Unsigned / Self-Signed Driver");
        addPropertyRow(driverGrid, 5, "Driver INF Package:", driverInfo.displayInfName());
        addPropertyRow(driverGrid, 6, "Kernel Service Name:", driverInfo.service().isBlank() ? "N/A (Direct Hardware / ACPI Resource)" : driverInfo.service());
        addPropertyRow(driverGrid, 7, "Hardware Class:", driverInfo.pnpClass());
        addPropertyRow(driverGrid, 8, "Driver Age Classification:", driverInfo.isOutdated() ? "Outdated / Legacy (>5 years old)" : "Modern / Supported");
        driverTab.setContent(new ScrollPane(driverGrid) {{ setFitToWidth(true); }});

        // Tab 2: Hardware & PnP Subsystem
        Tab hwTab = new Tab("Hardware & Bus IDs");
        GridPane hwGrid = createPropertyGrid();
        addPropertyRow(hwGrid, 0, "Device Name:", driverInfo.name());
        addPropertyRow(hwGrid, 1, "Manufacturer:", driverInfo.displayManufacturer());
        addPropertyRow(hwGrid, 2, "PnP Device Instance ID:", driverInfo.deviceId());
        addPropertyRow(hwGrid, 3, "Hardware / Compatible ID:", driverInfo.displayHardwareId());
        addPropertyRow(hwGrid, 4, "Operational Status:", driverInfo.status());
        addPropertyRow(hwGrid, 5, "ConfigManager Code:", driverInfo.formattedErrorCode());
        addPropertyRow(hwGrid, 6, "Physically Present:", driverInfo.isPresent() ? "Yes (Connected to Bus)" : "No (Disconnected / Phantom Device)");
        hwTab.setContent(new ScrollPane(hwGrid) {{ setFitToWidth(true); }});

        // Tab 3: Compatibility & Diagnostics
        Tab diagTab = new Tab("Compatibility & Troubleshooting");
        VBox diagBox = new VBox(12);
        diagBox.setPadding(new Insets(16));

        Label diagTitle = new Label("System Compatibility Analysis");
        diagTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");

        TextArea diagArea = new TextArea();
        diagArea.setEditable(false);
        diagArea.setWrapText(true);
        diagArea.setPrefRowCount(8);

        StringBuilder diagSb = new StringBuilder();
        diagSb.append("Device Identification: ").append(driverInfo.name()).append("\n");
        diagSb.append("Hardware Class: ").append(driverInfo.pnpClass()).append("\n");
        diagSb.append("PnP Identifier: ").append(driverInfo.deviceId()).append("\n");
        diagSb.append("Hardware ID: ").append(driverInfo.displayHardwareId()).append("\n");
        diagSb.append("Driver Version: ").append(driverInfo.displayDriverVersion()).append(" (Release: ").append(driverInfo.driverDate()).append(")\n");
        diagSb.append("Driver INF: ").append(driverInfo.displayInfName()).append("\n");
        diagSb.append("Digital Signer: ").append(driverInfo.displaySigner()).append("\n");
        diagSb.append("Reported Status: ").append(driverInfo.status()).append(" (Code ").append(driverInfo.errorCode()).append(")\n\n");
        diagSb.append("DIAGNOSIS:\n");
        diagSb.append("▶ ").append(driverInfo.compatibilityStatus()).append("\n");
        diagSb.append("▶ ").append(driverInfo.problemDescription()).append("\n\n");

        if (driverInfo.errorCode() == 0) {
            diagSb.append("RECOMMENDATION:\n");
            diagSb.append("No action required. The driver is properly installed, digitally validated, and compatible with your laptop hardware.");
        } else if (driverInfo.errorCode() == 22) {
            diagSb.append("RECOMMENDATION:\n");
            diagSb.append("This device was intentionally disabled by the user or an administrative policy. If you need this component (e.g. IPv6 WAN Miniport), re-enable it via Device Manager or network adapter settings.");
        } else if (driverInfo.errorCode() == 28) {
            diagSb.append("RECOMMENDATION:\n");
            diagSb.append("Missing driver. Visit your laptop manufacturer support page (e.g. Dell Support, Lenovo Vantage, HP Assistant) to download the official chipset/device driver package.");
        } else if (driverInfo.errorCode() == 45) {
            diagSb.append("RECOMMENDATION:\n");
            diagSb.append("This USB or peripheral device is disconnected. Reconnecting it to your laptop port will restore active status.");
        } else {
            diagSb.append("RECOMMENDATION:\n");
            diagSb.append("Check for hardware conflicts or updated drivers from the hardware vendor.");
        }
        diagArea.setText(diagSb.toString());

        diagBox.getChildren().addAll(diagTitle, diagArea);
        diagTab.setContent(diagBox);

        tabPane.getTabs().addAll(driverTab, hwTab, diagTab);
        root.setCenter(tabPane);

        // Bottom Controls
        HBox bottomBox = new HBox(10);
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        bottomBox.setPadding(new Insets(12, 0, 0, 0));

        Button exportInfBtn = new Button("📦 Backup This Driver");
        boolean canExport = driverInfo.infName() != null && !driverInfo.infName().isBlank() && !"N/A".equalsIgnoreCase(driverInfo.infName());
        exportInfBtn.setDisable(!canExport);
        exportInfBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Choose Folder to Backup " + driverInfo.infName());
            java.io.File dir = chooser.showDialog(this);
            if (dir != null && driverService != null && executorService != null) {
                exportInfBtn.setDisable(true);
                exportInfBtn.setText("Exporting...");
                executorService.submit(() -> {
                    boolean ok = driverService.exportDriverPackage(driverInfo.infName(), dir.getAbsolutePath());
                    javafx.application.Platform.runLater(() -> {
                        exportInfBtn.setDisable(false);
                        exportInfBtn.setText("📦 Backup This Driver");
                        Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                        alert.setTitle(ok ? "Driver Exported" : "Export Failed");
                        alert.setHeaderText(ok ? "Package " + driverInfo.infName() + " Exported" : "Failed to Export Driver");
                        alert.setContentText(ok ? "Driver package saved to:\n" + dir.getAbsolutePath() : "Could not export INF package.");
                        alert.showAndWait();
                    });
                });
            }
        });

        Button copyIdBtn = new Button("📋 Copy Device ID");
        copyIdBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(driverInfo.deviceId());
            clipboard.setContent(content);
            copyIdBtn.setText("✓ Copied!");
        });

        Button copyAllBtn = new Button("📋 Copy Full Info");
        copyAllBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(diagSb.toString());
            clipboard.setContent(content);
            copyAllBtn.setText("✓ Report Copied!");
        });

        Button openDevMgrBtn = new Button("🛠 Open Device Manager");
        openDevMgrBtn.setOnAction(e -> {
            try {
                new ProcessBuilder("devmgmt.msc").start();
            } catch (Exception ex) {
                log.warn("Could not launch devmgmt.msc: {}", ex.getMessage());
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("accent");
        closeBtn.setOnAction(e -> close());

        bottomBox.getChildren().addAll(exportInfBtn, copyIdBtn, copyAllBtn, openDevMgrBtn, spacer, closeBtn);
        root.setBottom(bottomBox);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private GridPane createPropertyGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(170);
        col1.setPrefWidth(180);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);
        return grid;
    }

    private void addPropertyRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold;");

        TextField valField = new TextField(value != null ? value : "");
        valField.setEditable(false);
        valField.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 0;");

        grid.add(lbl, 0, row);
        grid.add(valField, 1, row);
    }

    private String resolveClassIcon(String pnpClass) {
        if (pnpClass == null) return "⚙";
        return switch (pnpClass.toLowerCase()) {
            case "display" -> "🖥";
            case "net" -> "🌐";
            case "bluetooth" -> "📶";
            case "usb", "usbdevice" -> "🔌";
            case "scsiadapter", "hdc", "diskdrive" -> "💾";
            case "processor" -> "⚡";
            case "battery" -> "🔋";
            case "media", "audioendpoint" -> "🔊";
            case "camera" -> "📷";
            case "keyboard" -> "⌨";
            case "mouse", "hidclass" -> "🖱";
            case "monitor" -> "🖥";
            case "firmware" -> "🧠";
            case "system" -> "⚙";
            default -> "🔧";
        };
    }
}
