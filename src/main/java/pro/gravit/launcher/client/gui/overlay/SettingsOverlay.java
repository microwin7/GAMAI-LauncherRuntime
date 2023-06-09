package pro.gravit.launcher.client.gui.overlay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;

import oshi.SystemInfo;

import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

public class SettingsOverlay extends AbstractOverlay {
    private Pane componentList;
    private Label ramLabel;

    public SettingsOverlay(JavaFXApplication application) {
        super("overlay/settings/settings.fxml", application);
    }

    @Override
    protected void doInit() {
        Node layout = pane;
        componentList = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#settingslist").getContent();
        LookupHelper.<ButtonBase>lookup(layout, "#apply").setOnAction((e) -> {
            try {
                if (currentStage != null) {
                    currentStage.getScene().hideOverlay(0, null);
                }
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#close").setOnAction(
                (e) -> Platform.exit());
        LookupHelper.<ButtonBase>lookup(layout, "#hide").setOnAction((e) -> {
            if (this.currentStage != null) this.currentStage.hide();
        });

        Slider ramSlider = LookupHelper.lookup(layout, "#ramSlider");
        ramLabel = LookupHelper.lookup(layout, "#ramLabel");
        updateRamLabel();
        try {
            SystemInfo systemInfo = new SystemInfo();
            ramSlider.setMax(systemInfo.getHardware().getMemory().getTotal() >> 20);
        } catch (Throwable e) {
            LogHelper.error(e);
            ramSlider.setMax(2048);
        }

        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setMinorTickCount(3);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(1024);
        ramSlider.setValue(application.runtimeSettings.ram);
        ramSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            application.runtimeSettings.ram = newValue.intValue();
            updateRamLabel();
        });
        ramSlider.setLabelFormatter(new StringConverter<Double>() {
            @Override
            @LauncherNetworkAPI
            public String toString(Double object) {
                if (object == 0) return "AUTO";
                return Math.round(object / 1024 * 1d) / 1d + "GB";
            }

            @Override
            @LauncherNetworkAPI
            public Double fromString(String string) {
                string = string.replace("GB", "");
                return Double.parseDouble(string) * 1024;
            }
        });

        Hyperlink updateDirLink = LookupHelper.lookup(layout, "#patch");
        updateDirLink.setText(DirBridge.dirUpdates.toAbsolutePath().toString());
        updateDirLink.setOnAction((e) -> {
            application.openURL(DirBridge.dirUpdates.toAbsolutePath().toString());
        });
        LookupHelper.<ButtonBase>lookup(layout, "#changeDir").setOnAction((e) -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Сменить директорию загрузок");
            directoryChooser.setInitialDirectory(DirBridge.dir.toFile());
            File choose = directoryChooser.showDialog(application.getMainStage().stage);
            if (choose == null)
                return;
            Path newDir = choose.toPath().toAbsolutePath();
            try {
                DirBridge.move(newDir);
            } catch (IOException ex) {
                LogHelper.error(ex);
            }
            application.runtimeSettings.updatesDirPath = newDir.toString();
            application.runtimeSettings.updatesDir = newDir;
            updateDirLink.setText(application.runtimeSettings.updatesDirPath);
        });
        LookupHelper.<ButtonBase>lookup(layout, "#deleteDir").setOnAction((e) ->
                application.messageManager.showApplyDialog(application.getTranslation("runtime.overlay.settings.deletedir.header"),
                        application.getTranslation("runtime.overlay.settings.deletedir.description"),
                        () -> {
                            LogHelper.debug("Delete dir: %s", DirBridge.dirUpdates);
                            try {
                                IOHelper.deleteDir(DirBridge.dirUpdates, false);
                            } catch (IOException ex) {
                                LogHelper.error(ex);
                                application.messageManager.createNotification(application.getTranslation("runtime.overlay.settings.deletedir.fail.header"),
                                        application.getTranslation("runtime.overlay.settings.deletedir.fail.description"));
                            }
                        }, () -> {
                        }, true));
        add("Debug", application.runtimeSettings.debug, (value) -> application.runtimeSettings.debug = value);
        add("AutoEnter", application.runtimeSettings.autoEnter, (value) -> application.runtimeSettings.autoEnter = value);
        add("Fullscreen", application.runtimeSettings.fullScreen, (value) -> application.runtimeSettings.fullScreen = value);
        if(application.securityService.isMayBeDownloadJava() && application.guiModuleConfig.enableDownloadJava && application.guiModuleConfig.userDisableDownloadJava)
        {
            add("DisableJavaDownload", application.runtimeSettings.disableJavaDownload, (value) -> application.runtimeSettings.disableJavaDownload = value);
        }
    }

    @Override
    public void reset() {}

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    public void add(String languageName, boolean value, Consumer<Boolean> onChanged) {
        String nameKey = String.format("runtime.overlay.settings.properties.%s.name", languageName.toLowerCase());
        String descriptionKey = String.format("runtime.overlay.settings.properties.%s.description", languageName.toLowerCase());
        add(application.getTranslation(nameKey, languageName), application.getTranslation(descriptionKey, languageName), value, onChanged);
    }

    public void add(String name, String description, boolean value, Consumer<Boolean> onChanged) {
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(value);
        checkBox.setText(name);
        Label desc = new Label();
        desc.setText(description);
        componentList.getChildren().add(checkBox);
        componentList.getChildren().add(desc);

        checkBox.setOnAction((e) -> onChanged.accept(checkBox.isSelected()));
        checkBox.getStyleClass().add("optCheckbox");
        desc.getStyleClass().add("optDescription");
    }
    public void updateRamLabel()
    {
        ramLabel.setText(application.runtimeSettings.ram == 0 ? "Auto" : Integer.toString(application.runtimeSettings.ram).concat(" MiB"));
    }
}
