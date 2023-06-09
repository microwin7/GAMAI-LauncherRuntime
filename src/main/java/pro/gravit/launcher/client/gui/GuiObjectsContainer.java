package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.client.gui.overlay.*;
import pro.gravit.launcher.client.gui.scene.ConsoleScene;
import pro.gravit.launcher.client.gui.scene.LoginScene;
import pro.gravit.launcher.client.gui.scene.ServerInfoScene;
import pro.gravit.launcher.client.gui.scene.ServerMenuScene;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;

public class GuiObjectsContainer {
    private final JavaFXApplication application;
    public ConsoleScene consoleScene;
    public ConsoleStage consoleStage;
    public DebugOverlay debugOverlay;
    public LoginScene loginScene;
    public OptionsOverlay optionsOverlay;
    public ServerMenuScene serverMenuScene;
    public ServerInfoScene serverInfoScene;
    public SettingsOverlay settingsOverlay;
    public UpdateOverlay updateOverlay;
    public ProcessingOverlay processingOverlay;

    public GuiObjectsContainer(JavaFXApplication application) {
        this.application = application;
    }

    public void init() {
        consoleScene = application.registerScene(ConsoleScene.class);
        debugOverlay = application.registerOverlay(DebugOverlay.class);
        loginScene = application.registerScene(LoginScene.class);
        optionsOverlay = application.registerOverlay(OptionsOverlay.class);
        processingOverlay = application.registerOverlay(ProcessingOverlay.class);
        serverMenuScene = application.registerScene(ServerMenuScene.class);
        serverInfoScene = application.registerScene(ServerInfoScene.class);
        settingsOverlay = application.registerOverlay(SettingsOverlay.class);
        updateOverlay = application.registerOverlay(UpdateOverlay.class);
    }
}
