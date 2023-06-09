package pro.gravit.launcher.client.gui.scene;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import pro.gravit.launcher.client.ServerPinger;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.client.gui.stage.ConsoleStage;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.launcher.profiles.PlayerProfile;
import pro.gravit.launcher.request.Request;
import pro.gravit.launcher.request.auth.ExitRequest;
import pro.gravit.launcher.request.management.PingServerRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class ServerMenuScene extends AbstractScene {
    private static final String SERVER_BUTTON_FXML = "components/serverButton.fxml";
    private static final String SERVER_BUTTON_CUSTOM_FXML = "components/serverButton/%s.fxml";
    public int currentProfile;
    public Map<Integer, ServerButtonCache> serverButtonCacheMap = new LinkedHashMap<>();
    private Node layout;
    private ImageView avatar;
    private List<ClientProfile> lastProfiles;
    private ButtonBase firstBtn;
    private ButtonBase secondBtn;
    private ButtonBase thirdBtn;
    private ButtonBase fourthBtn;
    private HBox dots;

    public ServerMenuScene(JavaFXApplication application) {
        super("scenes/servermenu/servermenu.fxml", application);
    }

    @Override
    public void doInit() {
        layout = scene.getRoot();
        sceneBaseInit(layout);
        avatar = LookupHelper.lookup(layout, "#avatar");

        LookupHelper.<ButtonBase>lookup(layout, "#prev").setOnAction((e) -> actionPrev());
        LookupHelper.<ButtonBase>lookup(layout, "#next").setOnAction((e) -> actionNext());

        firstBtn = LookupHelper.lookup(layout, "#firstBtn");
        secondBtn = LookupHelper.lookup(layout, "#secondBtn");
        thirdBtn = LookupHelper.lookup(layout, "#thirdBtn");
        fourthBtn = LookupHelper.lookup(layout, "#fourthBtn");
        dots = LookupHelper.lookup(layout, "#dots");

        LookupHelper.<ButtonBase>lookup(layout, "#vk").setOnAction((e) -> application.openURL(application.guiModuleConfig.vkURL));
        LookupHelper.<ButtonBase>lookup(layout, "#yt").setOnAction((e) -> application.openURL(application.guiModuleConfig.youtubeURL));
        LookupHelper.<ButtonBase>lookup(layout, "#discord").setOnAction((e) -> application.openURL(application.guiModuleConfig.discordURL));

        LookupHelper.<HBox>lookup(layout, "#list").setOnScroll((ScrollEvent e) -> {
            if (e.getDeltaY() > 0) {
                actionPrev();
            } else {
                actionNext();
            }
        });
        LookupHelper.<ButtonBase>lookup(layout, "#settings").setOnAction((e) -> {
            showOverlay(application.gui.settingsOverlay, null);
        });
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

    private void actionPrev() {
        if (currentProfile == 1)
            currentProfile = lastProfiles.size();
        else
            currentProfile -= 1;
        resetBlock();
    }

    private void actionNext() {
        if (currentProfile == lastProfiles.size())
            currentProfile = 1;
        else
            currentProfile += 1;
        resetBlock();
    }

    @Override
    public void reset() {
        dots.getChildren().clear();
        LookupHelper.<ButtonBase>lookup(layout, "#console").setVisible(application.runtimeStateMachine.getUserPerms().isPermission(1));
        lastProfiles = application.runtimeStateMachine.getProfiles();
        LookupHelper.<Labeled>lookup(layout, "#nickname").setText(application.runtimeStateMachine.getUsername());
        try {
            int position = 1;
            for (ClientProfile profile : application.runtimeStateMachine.getProfiles()) {
                ServerButtonCache cache = new ServerButtonCache();
                UUID profileUUID = profile.getUUID();
                if(profileUUID == null) {
                    profileUUID = UUID.randomUUID();
                    LogHelper.warning("Profile %s UUID null", profileUUID);
                }
                String customFxmlName = String.format(SERVER_BUTTON_CUSTOM_FXML, profileUUID);
                URL customFxml = application.tryResource(customFxmlName);
                if (customFxml != null) {
                    cache.pane = application.getNonCachedFxmlAsync(customFxmlName, IOHelper.newInput(customFxml));
                } else {
                    cache.pane = application.getNonCachedFxmlAsync(SERVER_BUTTON_FXML);
                }
                cache.position = position;
                cache.profile = profile;
                serverButtonCacheMap.put(position, cache);
                position++;
            }
        } catch (IOException e) {
            errorHandle(e);
            return;
        }

        application.runtimeStateMachine.clearServerPingCallbacks();


        currentProfile = 1;
        serverButtonCacheMap.forEach((position, serverButtonCache) -> {
            try {
                Pane pane = serverButtonCache.pane.get();
                ClientProfile profile = serverButtonCache.profile;
                AtomicReference<ServerPinger.Result> pingerResult = new AtomicReference<>();
                LookupHelper.<Labeled>lookup(pane, "#serverTitle").setText(profile.getTitle());
                LookupHelper.<Labeled>lookup(pane, "#genreServer").setText(profile.getVersion().toString());
                profile.updateOptionalGraph();
                EventHandler<? super MouseEvent> handle = (event) -> {
                    if (!event.getButton().equals(MouseButton.PRIMARY))
                        return;
                    if (application.getCurrentScene() instanceof ServerInfoScene)
                        return;
                    currentProfile = position;
                    changeServer(profile);
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                };
                pane.setOnMouseClicked(handle);
                ClientProfile.ServerProfile defaultServer = profile.getDefaultServerProfile();
                if(defaultServer == null || defaultServer.serverAddress != null)
                {
                    application.workers.submit(() -> {
                        ServerPinger pinger = new ServerPinger(profile);
                        ServerPinger.Result result;
                        try {
                            result = pinger.ping();
                        } catch (IOException e) {
                            result = new ServerPinger.Result(0, 0, "0 / 0");
                        }
                        pingerResult.set(result);
                        changeOnline(pane, profile, result.onlinePlayers, result.maxPlayers);
                    });
                }
                else if(profile.getServers() != null)
                {
                    for(ClientProfile.ServerProfile serverProfile : profile.getServers())
                    {
                        if(serverProfile.isDefault)
                        {
                            application.runtimeStateMachine.addServerPingCallback(serverProfile.name, (report) -> {
                                changeOnline(pane, profile, report.playersOnline, report.maxPlayers);
                            });
                        }
                    }
                }
                if ((application.runtimeSettings.lastProfile == null) || (profile.getUUID() != null && profile.getUUID().equals(application.runtimeSettings.lastProfile))) {
                    currentProfile = position;
                    LogHelper.dev("Selected profile %s", profile.getTitle());
                }
                Circle c = new Circle(10);
                c.setOnMouseClicked((e) -> {
                    currentProfile = position;
                    resetBlock();
                });
                dots.getChildren().add(c);
            } catch (InterruptedException | ExecutionException e) {
                LogHelper.error(e);
            }
        });
        try {
            Request.service.request(new PingServerRequest()).thenAccept((event) -> {
                if(event.serverMap != null)
                {
                    event.serverMap.forEach((name, value) -> {
                        application.runtimeStateMachine.setServerPingReport(event.serverMap);
                    });
                }
            }).exceptionally((ex) -> {
                LogHelper.error(ex.getCause());
                return null;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        resetBlock();
        CommonHelper.newThread("SkinHead Downloader Thread", true, () -> {
            try {
                updateSkinHead();
            } catch (Throwable e) {
                LogHelper.error(e);
            }
        }).start();
    }

    public void resetBlock() {
        try {
            dots.getChildren().forEach((circle) -> {
                circle.getStyleClass().clear();
            });
            firstBtn.setGraphic(serverButtonCacheMap.get(currentProfile).pane.get());
            dots.getChildren().get(currentProfile - 1).getStyleClass().add("active");
            if (currentProfile == lastProfiles.size()) {
                secondBtn.setGraphic(serverButtonCacheMap.get(1).pane.get());
                thirdBtn.setGraphic(serverButtonCacheMap.get(2).pane.get());
                fourthBtn.setGraphic(serverButtonCacheMap.get(3).pane.get());
                dots.getChildren().get(0).getStyleClass().add("active");
                dots.getChildren().get(1).getStyleClass().add("active");
                dots.getChildren().get(2).getStyleClass().add("active");
            } else if (currentProfile + 1 == lastProfiles.size()) {
                secondBtn.setGraphic(serverButtonCacheMap.get(lastProfiles.size()).pane.get());
                thirdBtn.setGraphic(serverButtonCacheMap.get(1).pane.get());
                fourthBtn.setGraphic(serverButtonCacheMap.get(2).pane.get());
                dots.getChildren().get(lastProfiles.size() - 1).getStyleClass().add("active");
                dots.getChildren().get(0).getStyleClass().add("active");
                dots.getChildren().get(1).getStyleClass().add("active");
            } else if (currentProfile + 2 == lastProfiles.size()) {
                secondBtn.setGraphic(serverButtonCacheMap.get(lastProfiles.size() - 1).pane.get());
                thirdBtn.setGraphic(serverButtonCacheMap.get(lastProfiles.size()).pane.get());
                fourthBtn.setGraphic(serverButtonCacheMap.get(1).pane.get());
                dots.getChildren().get(lastProfiles.size() - 2).getStyleClass().add("active");
                dots.getChildren().get(lastProfiles.size() - 1).getStyleClass().add("active");
                dots.getChildren().get(0).getStyleClass().add("active");
            } else if (currentProfile + 3 == lastProfiles.size()) {
                secondBtn.setGraphic(serverButtonCacheMap.get(lastProfiles.size() - 2).pane.get());
                thirdBtn.setGraphic(serverButtonCacheMap.get(lastProfiles.size() - 1).pane.get());
                fourthBtn.setGraphic(serverButtonCacheMap.get(lastProfiles.size()).pane.get());
                dots.getChildren().get(lastProfiles.size() - 3).getStyleClass().add("active");
                dots.getChildren().get(lastProfiles.size() - 2).getStyleClass().add("active");
                dots.getChildren().get(lastProfiles.size() - 1).getStyleClass().add("active");
            } else {
                secondBtn.setGraphic(serverButtonCacheMap.get(currentProfile + 1).pane.get());
                thirdBtn.setGraphic(serverButtonCacheMap.get(currentProfile + 2).pane.get());
                fourthBtn.setGraphic(serverButtonCacheMap.get(currentProfile + 3).pane.get());
                dots.getChildren().get(currentProfile).getStyleClass().add("active");
                dots.getChildren().get(currentProfile + 1).getStyleClass().add("active");
                dots.getChildren().get(currentProfile + 2).getStyleClass().add("active");
            }
        } catch (InterruptedException | ExecutionException e) {
            LogHelper.error(e);
        }
    }

    public void changeOnline(Pane pane, ClientProfile profile, int online, int maxOnline) {
        contextHelper.runInFxThread(() -> {
            LookupHelper.<Labeled>lookup(pane, "#online").setText(String.valueOf(online));
            LookupHelper.<Labeled>lookup(pane, "#max").setText(String.valueOf(maxOnline));
            if (application.getCurrentScene() instanceof ServerInfoScene &&
                    application.runtimeStateMachine.getProfile() == profile) {
                application.gui.serverInfoScene.online.setText(String.valueOf(online));
            }
        });
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    @Override
    protected void doShow() {
        super.doShow();
        if(lastProfiles != application.runtimeStateMachine.getProfiles())
        {
            reset();
        }
    }

    private void updateSkinHead() throws IOException {
        PlayerProfile playerProfile = application.runtimeStateMachine.getPlayerProfile();
        if (playerProfile == null)
            return;
        if (playerProfile.skin == null || playerProfile.skin.url == null) {
            LogHelper.debug("Skin not found");
            return;
        }
        String url = playerProfile.skin.url;
        BufferedImage origImage = downloadSkinHead(url);
        int imageHeight = (int) avatar.getFitHeight(), imageWidth = (int) avatar.getFitWidth();
        java.awt.Image resized = origImage.getScaledInstance(imageWidth, imageHeight, java.awt.Image.SCALE_FAST);
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics2D = image.createGraphics();
        graphics2D.drawImage(resized, 0, 0, null);
        graphics2D.dispose();
        javafx.scene.shape.Rectangle clip = new Rectangle(
                avatar.getFitWidth(), avatar.getFitHeight()
        );
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        avatar.setClip(clip);
        avatar.setImage(SwingFXUtils.toFXImage(image, null));
    }

    private BufferedImage downloadSkinHead(String url) throws IOException {
        BufferedImage image = ImageIO.read(new URL(url));
        int width = image.getWidth();
        int renderScale = width / 64;
        int offset = 8 * renderScale;
        LogHelper.debug("ShinHead debug: W: %d Scale: %d Offset: %d", width, renderScale, offset);
        return image.getSubimage(offset, offset, offset, offset);
    }

    private void changeServer(ClientProfile profile) {
        application.runtimeStateMachine.setProfile(profile);
        application.runtimeSettings.lastProfile = profile.getUUID();
        try {
            if (application.gui.serverInfoScene.getScene() != null)
                application.gui.serverInfoScene.reset();
            application.setMainScene(application.gui.serverInfoScene);
        } catch (Exception e) {
            errorHandle(e);
        }
    }

    static class ServerButtonCache {
        public Future<Pane> pane;
        public int position;
        public ClientProfile profile;
        public int online = 0;
    }
}
