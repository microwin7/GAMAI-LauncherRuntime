package pro.gravit.launcher.client.gui.scene;

import com.google.gson.reflect.TypeToken;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.client.ClientLauncherProcess;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.overlay.DebugOverlay;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.launcher.request.auth.SetProfileRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JVMHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ServerInfoScene extends AbstractScene {
    private static final String STATCELL_FXML = "components/statCell.fxml";
    private static final String LOGOIMAGE_PATH = "images/servers/logos/%s.png";
    private static final String BGIMAGE_PATH = "images/servers/viewBg/%s.png";
    public Text online;
    public Map<String, ServerInfoScene.UserStat[]> statistics;
    public Image defaultBg;
    public Image defaultLogo;
    private Node layout;

    public ServerInfoScene(JavaFXApplication application) {
        super("scenes/serverinfo/serverinfo.fxml", application);
    }

    @Override
    public void doInit() {
        layout = scene.getRoot();
        sceneBaseInit(layout);

        online = LookupHelper.lookup(layout, "#headingOnline");

        defaultBg = LookupHelper.<ImageView>lookup(layout, "#serverBg").getImage();
        defaultLogo = LookupHelper.<ImageView>lookup(layout, "#serverLogo").getImage();


        LookupHelper.<ButtonBase>lookup(layout, "#goToServersList").setOnAction(event -> {
            try {
                application.setMainScene(application.gui.serverMenuScene);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        LookupHelper.<ButtonBase>lookup(layout, "#clientSettings").setOnAction((e) -> {
            try {
                if (application.runtimeStateMachine.getProfile() == null)
                    return;
                showOverlay(application.gui.optionsOverlay, (ec) -> {
                    application.gui.optionsOverlay.addProfileOptionals(application.runtimeStateMachine.getOptionalView());
                });
            } catch (Exception ex) {
                LogHelper.error(ex);
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#settings").setOnAction((e) -> showOverlay(application.gui.settingsOverlay, null));
        LookupHelper.<ButtonBase>lookup(layout, "#exit").setOnAction((e) ->
                application.messageManager.showApplyDialog(application.getTranslation("runtime.overlay.settings.exitDialog.header"),
                        application.getTranslation("runtime.overlay.settings.exitDialog.description"), () ->
                                processRequest(application.getTranslation("runtime.overlay.settings.exitDialog.processing"),
                                        new ExitRequest(), (event) -> {
                                            // Exit to main menu
                                            ContextHelper.runInFxThreadStatic(() -> {
                                                hideOverlay(0, null);
                                                application.gui.loginScene.clearPassword();
                                                application.gui.loginScene.reset();
                                                try {
                                                    application.saveSettings();
                                                    application.runtimeStateMachine.exit();
                                                    getCurrentStage().setScene(application.gui.loginScene);
                                                } catch (Exception ex) {
                                                    LogHelper.error(ex);
                                                }
                                            });
                                        }, (event) -> {

                                        }), () -> {
                        }, true));
        LookupHelper.<ButtonBase>lookup(layout, "#clientLaunch").setOnAction((e) -> launchClient());
        LookupHelper.<ButtonBase>lookup(layout, "#console").setOnAction((e) -> {
            try {
                if (application.gui.consoleStage == null)
                    application.gui.consoleStage = new ConsoleStage(application);
                if (application.gui.consoleStage.isNullScene())
                    application.gui.consoleStage.setScene(application.gui.consoleScene);
                application.gui.consoleStage.show();
            } catch (Exception ex) {
                errorHandle(ex);
            }
        });
        reset();
    }

    @Override
    public void reset() {
        LookupHelper.<ButtonBase>lookup(layout, "#console").setVisible(application.runtimeStateMachine.getUserPerms().isPermission(1));

        ((Pane) LookupHelper.<ScrollPane>lookup(layout, "#modlist").getContent()).getChildren().clear();
        LookupHelper.<VBox>lookup(layout, "#statistics").getChildren().clear();

        LookupHelper.<Text>lookup(layout, "#heading").setText(application.runtimeStateMachine.getProfile().getTitle());
        LookupHelper.<Labeled>lookup(layout, "#serverTitle").setText(application.runtimeStateMachine.getProfile().getTitle());
        LookupHelper.<Labeled>lookup(layout, "#genreServer").setText(application.runtimeStateMachine.getProfile().getVersion().toString());

        LookupHelper.<Labeled>lookup(LookupHelper.
                        <ScrollPane>lookup(layout, "#serverInfo").getContent(),
                "#servertext").setText(application.runtimeStateMachine.getProfile().getInfo());

        try {
            online.setText(LookupHelper.<Labeled>lookup(application.gui.serverMenuScene.serverButtonCacheMap.get(
                    application.gui.serverMenuScene.currentProfile).pane.get(), "#online").getText());
        } catch (InterruptedException | ExecutionException e) {
            LogHelper.error(e);
        }

        if (statistics != null)
            updateStats();

        try {
            String file = String.format("modlist/%s.json", application.runtimeStateMachine.getProfile().getUUID());
            if (application.tryResource(file) != null) {
                BufferedReader json = IOHelper.newReader(Launcher.getResourceURL(file));
                Type itemsMapType = new TypeToken<ArrayList<String[]>>() {}.getType();
                ArrayList<String[]> mods = Launcher.gsonManager.gson.fromJson(json, itemsMapType);
                Pane x = (Pane) LookupHelper.<ScrollPane>lookup(layout, "#modlist").getContent();
                double size = x.getPrefWidth() / mods.size();
                for (String[] item : mods) {
                    VBox box = new VBox();
                    box.setPrefWidth(size);
                    box.setPrefHeight(x.getPrefHeight());
                    box.setSpacing(5);
                    box.setAlignment(Pos.TOP_LEFT);
                    for (String i : item) {
                        HBox cell = new HBox();
                        cell.setSpacing(5);
                        cell.setAlignment(Pos.CENTER_LEFT);
                        SVGPath path = new SVGPath();
                        path.setContent("M0,5.2L5.2,0l5.2,5.2l-5.2,5.2L0,5.2z");
                        path.getStyleClass().add("svgPath");
                        Label label = new Label(i);
                        label.getStyleClass().add("modName");
                        cell.getChildren().addAll(path, label);
                        box.getChildren().add(cell);
                    }
                    x.getChildren().add(box);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (application.tryResource(String.format(BGIMAGE_PATH, application.runtimeStateMachine.getProfile().getUUID().toString())) != null) {
            LookupHelper.<ImageView>lookup(layout, "#serverBg").setImage(new Image(application.tryResource(String.format(BGIMAGE_PATH, application.runtimeStateMachine.getProfile().getUUID().toString())).toString()));
        } else {
            LookupHelper.<ImageView>lookup(layout, "#serverBg").setImage(defaultBg);
        }

        if (application.tryResource(String.format(LOGOIMAGE_PATH, application.runtimeStateMachine.getProfile().getUUID().toString())) != null) {
            LookupHelper.<ImageView>lookup(layout, "#serverLogo").setImage(new Image(application.tryResource(String.format(LOGOIMAGE_PATH, application.runtimeStateMachine.getProfile().getUUID().toString())).toString()));
        } else {
            LookupHelper.<ImageView>lookup(layout, "#serverLogo").setImage(defaultLogo);
        }
    }

    public void updateStats() {
        try {
            UserStat[] us = statistics.get(application.runtimeStateMachine.getProfile().getUUID().toString());

            if (us != null) {
                VBox x = LookupHelper.lookup(layout, "#statistics");
                for (UserStat item : us) {
                    Pane cell = (Pane) application.getNonCachedFxmlAsync(STATCELL_FXML).get();
                    System.out.print(item.shape);
                    System.out.print("---------");
                    System.out.print(item.text);
                    LookupHelper.<SVGPath>lookup(cell, "#statPath").setContent(item.shape);
                    LookupHelper.<Labeled>lookup(cell, "#statValue").setText(item.text);
                    x.getChildren().add(cell);
                }
            }
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    @Override
    protected void doShow() {
        super.doShow();
    }

    private boolean isEnabledDownloadJava() {
        return application.securityService.isMayBeDownloadJava() && application.guiModuleConfig.enableDownloadJava && (!application.guiModuleConfig.userDisableDownloadJava || application.runtimeSettings.disableJavaDownload);
    }

    private void launchClient() {
        ClientProfile profile = application.runtimeStateMachine.getProfile();
        if (profile == null)
            return;
        processRequest(application.getTranslation("runtime.overlay.processing.text.setprofile"), new SetProfileRequest(profile), (result) -> showOverlay(application.gui.updateOverlay, (e) -> {
            application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.java"));
            if (isEnabledDownloadJava()) {
                String jvmDirName = JVMHelper.OS_BITS == 64 ? application.guiModuleConfig.jvmWindows64Dir : application.guiModuleConfig.jvmWindows32Dir;
                Path jvmDirPath = DirBridge.dirUpdates.resolve(jvmDirName);
                application.gui.updateOverlay.sendUpdateRequest(jvmDirName, jvmDirPath, null, profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), false, (jvmHDir) -> {
                    downloadClients(profile, jvmDirPath, jvmHDir);
                });
            } else {
                downloadClients(profile, null, null);
            }
        }), null);
    }

    private void downloadClients(ClientProfile profile, Path jvmDir, HashedDir jvmHDir) {
        Path target = DirBridge.dirUpdates.resolve(profile.getAssetDir());
        LogHelper.info("Start update to %s", target.toString());
        application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.assets"));
        application.gui.updateOverlay.sendUpdateRequest(profile.getAssetDir(), target, profile.getAssetUpdateMatcher(), profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), false, (assetHDir) -> {
            Path targetClient = DirBridge.dirUpdates.resolve(profile.getDir());
            LogHelper.info("Start update to %s", targetClient.toString());
            application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.client"));
            application.gui.updateOverlay.sendUpdateRequest(profile.getDir(), targetClient, profile.getClientUpdateMatcher(), profile.isUpdateFastCheck(), application.runtimeStateMachine.getOptionalView(), true, (clientHDir) -> {
                LogHelper.info("Success update");
                application.gui.updateOverlay.initNewPhase(application.getTranslation("runtime.overlay.update.phase.launch"));
                doLaunchClient(target, assetHDir, targetClient, clientHDir, profile, application.runtimeStateMachine.getOptionalView(), jvmDir, jvmHDir);
            });
        });
    }

    private void doLaunchClient(Path assetDir, HashedDir assetHDir, Path clientDir, HashedDir clientHDir, ClientProfile profile, OptionalView view, Path jvmDir, HashedDir jvmHDir) {
        ClientLauncherProcess clientLauncherProcess = new ClientLauncherProcess(clientDir, assetDir, jvmDir != null ? jvmDir : Paths.get(System.getProperty("java.home")), clientDir.resolve("resourcepacks"), profile, application.runtimeStateMachine.getPlayerProfile(), view,
                application.runtimeStateMachine.getAccessToken(), clientHDir, assetHDir, jvmHDir);
        clientLauncherProcess.params.ram = application.runtimeSettings.ram;
        if (clientLauncherProcess.params.ram > 0) {
            clientLauncherProcess.jvmArgs.add("-Xms" + clientLauncherProcess.params.ram + 'M');
            clientLauncherProcess.jvmArgs.add("-Xmx" + clientLauncherProcess.params.ram + 'M');
        }
        clientLauncherProcess.params.fullScreen = application.runtimeSettings.fullScreen;
        clientLauncherProcess.params.autoEnter = application.runtimeSettings.autoEnter;
        contextHelper.runCallback(() -> {
            Thread writerThread = CommonHelper.newThread("Client Params Writer Thread", true, () -> {
                try {
                    clientLauncherProcess.runWriteParams(new InetSocketAddress("127.0.0.1", Launcher.getConfig().clientPort));
                    if (!application.runtimeSettings.debug) {
                        LogHelper.debug("Params writted successful. Exit...");
                        LauncherEngine.exitLauncher(0);
                    }
                } catch (Throwable e) {
                    LogHelper.error(e);
                    if (getCurrentOverlay() instanceof DebugOverlay) {
                        DebugOverlay debugOverlay = (DebugOverlay) getCurrentOverlay();
                        debugOverlay.append(String.format("Launcher fatal error(Write Params Thread): %s: %s", e.getClass().getName(), e.getMessage()));
                        if (debugOverlay.currentProcess != null && debugOverlay.currentProcess.isAlive()) {
                            debugOverlay.currentProcess.destroy();
                        }
                    }
                }
            });
            writerThread.start();
            application.gui.debugOverlay.writeParametersThread = writerThread;
            clientLauncherProcess.start(true);
            showOverlay(application.gui.debugOverlay, (e) -> application.gui.debugOverlay.onProcess(clientLauncherProcess.getProcess()));
        }).run();
    }

    @LauncherNetworkAPI
    public static class UserStat {
        @LauncherNetworkAPI
        public String shape;
        @LauncherNetworkAPI
        public String text;
    }
}
