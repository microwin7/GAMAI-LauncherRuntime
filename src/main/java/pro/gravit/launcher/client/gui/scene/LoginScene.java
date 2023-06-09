package pro.gravit.launcher.client.gui.scene;

import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherEngine;
import pro.gravit.launcher.client.events.ClientExitPhase;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractScene;
import pro.gravit.launcher.events.request.AuthRequestEvent;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.request.RequestException;
import pro.gravit.launcher.request.auth.AuthRequest;
import pro.gravit.launcher.request.auth.GetAvailabilityAuthRequest;
import pro.gravit.launcher.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.request.auth.password.AuthECPassword;
import pro.gravit.launcher.request.auth.password.AuthTOTPPassword;
import pro.gravit.launcher.request.update.LauncherRequest;
import pro.gravit.launcher.request.update.ProfilesRequest;
import pro.gravit.utils.helper.CommonHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class LoginScene extends AbstractScene {
    public boolean isLoginStarted;
    private List<GetAvailabilityAuthRequestEvent.AuthAvailability> auth;
    private TextField loginField;
    private TextField passwordField;
    private CheckBox savePasswordCheckBox;
    private CheckBox autoenter;
    private ComboBox<GetAvailabilityAuthRequestEvent.AuthAvailability> authList;

    public LoginScene(JavaFXApplication application) {
        super("scenes/login/login.fxml", application);
    }

    @Override
    public void doInit() {
        Node layout = scene.getRoot();
        sceneBaseInit(layout);
        loginField = LookupHelper.lookup(layout, "#login");
        if (application.runtimeSettings.login != null)
            loginField.setText(application.runtimeSettings.login);
        passwordField = LookupHelper.lookup(layout, "#password");
        savePasswordCheckBox = LookupHelper.lookup(layout, "#savePassword");
        if (application.runtimeSettings.encryptedPassword != null) {
            passwordField.getStyleClass().add("hasSaved");
            passwordField.setPromptText(application.getTranslation("runtime.scenes.login.login.password.saved"));
            LookupHelper.<CheckBox>lookup(layout, "#savePassword").setSelected(true);
        }
        autoenter = LookupHelper.lookup(layout, "#autoenter");
        autoenter.setSelected(application.runtimeSettings.autoAuth);
        autoenter.setOnAction((event) -> application.runtimeSettings.autoAuth = autoenter.isSelected());
        if (application.guiModuleConfig.createAccountURL != null)
            LookupHelper.<Hyperlink>lookup(layout, "#createAccount").setOnAction((e) ->
                    application.openURL(application.guiModuleConfig.createAccountURL));
        if (application.guiModuleConfig.forgotPassURL != null)
            LookupHelper.<Hyperlink>lookup(layout, "#forgotPass").setOnAction((e) ->
                    application.openURL(application.guiModuleConfig.forgotPassURL));
        authList = LookupHelper.lookup(layout, "#combologin");
        authList.setConverter(new AuthConverter());
        LookupHelper.<ButtonBase>lookup(layout, "#goAuth").setOnAction((e) -> contextHelper.runCallback(this::loginWithGui).run());

        LookupHelper.<Pane>lookup(layout, "#about").setVisible(false);
        LookupHelper.<ButtonBase>lookup(layout, "#aboutAct").setOnAction(event -> {
            LookupHelper.<Pane>lookup(layout, "#authPane").setEffect(new GaussianBlur(10));
            LookupHelper.<Pane>lookup(layout, "#about").setVisible(true);
        });
        LookupHelper.<ButtonBase>lookup(layout, "#closeAbout").setOnAction(event -> {
            LookupHelper.<Pane>lookup(layout, "#authPane").setEffect(null);
            LookupHelper.<Pane>lookup(layout, "#about").setVisible(false);
        });

        // Verify Launcher
        {
            LauncherRequest launcherRequest = new LauncherRequest();
            processRequest(application.getTranslation("runtime.overlay.processing.text.launcher"), launcherRequest, (result) -> {
                if (result.needUpdate) {
                    try {
                        LogHelper.debug("Start update processing");
                        application.securityService.update(result);
                        LogHelper.debug("Exit with Platform.exit");
                        Platform.exit();
                        return;
                    } catch (Throwable e) {
                        contextHelper.runInFxThread(() -> {
                            getCurrentOverlay().errorHandle(e);
                        });
                        try {
                            Thread.sleep(1500);
                            LauncherEngine.modulesManager.invokeEvent(new ClientExitPhase(0));
                            Platform.exit();
                        } catch (Throwable ex) {
                            LauncherEngine.exitLauncher(0);
                        }
                    }
                }
                LogHelper.dev("Launcher update processed");
                GetAvailabilityAuthRequest getAvailabilityAuthRequest = new GetAvailabilityAuthRequest();
                processRequest(application.getTranslation("runtime.overlay.processing.text.authAvailability"), getAvailabilityAuthRequest, (auth) -> contextHelper.runInFxThread(() -> {
                    this.auth = auth.list;
                    GetAvailabilityAuthRequestEvent.AuthAvailability lastAuth = null;
                    for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth.list) {
                        if (authAvailability.equals(application.runtimeSettings.lastAuth))
                            lastAuth = authAvailability;
                        authList.getItems().add(authAvailability);
                    }
                    if (lastAuth != null) authList.getSelectionModel().select(lastAuth);
                    else authList.getSelectionModel().selectFirst();

                    hideOverlay(0, (event) -> {
                        if (application.runtimeSettings.encryptedPassword != null && application.runtimeSettings.autoAuth)
                            contextHelper.runCallback(this::loginWithGui).run();
                    });
                }), null);
            }, (event) -> LauncherEngine.exitLauncher(0));
        }
    }

    @Override
    public void reset() {
        LookupHelper.<Pane>lookup(scene.getRoot(), "#authPane").setEffect(null);
        LookupHelper.<Pane>lookup(scene.getRoot(), "#about").setVisible(false);
        passwordField.getStyleClass().removeAll("hasSaved");
        passwordField.setPromptText(application.getTranslation("runtime.scenes.login.login.password"));
        passwordField.setText("");
        loginField.setText("");
    }

    @Override
    public void errorHandle(Throwable e) {
        LogHelper.error(e);
    }

    private void loginWithGui() {
        String login = loginField.getText();
        byte[] encryptedPassword;
        if (passwordField.getText().isEmpty() && passwordField.getPromptText().equals(
                application.getTranslation("runtime.scenes.login.login.password.saved"))) {
            encryptedPassword = application.runtimeSettings.encryptedPassword;
        } else {
            String password = passwordField.getText();
            try {
                encryptedPassword = encryptPassword(password);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        GetAvailabilityAuthRequestEvent.AuthAvailability authId = authList.getSelectionModel().getSelectedItem();
        boolean savePassword = savePasswordCheckBox.isSelected();
        login(login, encryptedPassword, authId, null, savePassword);
    }

    private byte[] encryptPassword(String password) throws Exception {
        return SecurityHelper.encrypt(launcherConfig.passwordEncryptKey, password);
    }

    private void login(String login, byte[] password, GetAvailabilityAuthRequestEvent.AuthAvailability authId, String totp, boolean savePassword) {
        isLoginStarted = true;
        LogHelper.dev("Auth with %s password ***** authId %s", login, authId);
        AuthRequest authRequest;
        if(totp == null)
        {
            authRequest = new AuthRequest(login, password, authId.name);
        }
        else
        {
            Auth2FAPassword auth2FAPassword = new Auth2FAPassword();
            auth2FAPassword.firstPassword = new AuthECPassword(password);
            auth2FAPassword.secondPassword = new AuthTOTPPassword();
            ((AuthTOTPPassword) auth2FAPassword.secondPassword).totp = totp;
            authRequest = new AuthRequest(login, auth2FAPassword, authId.name, true, AuthRequest.ConnectTypes.CLIENT);
        }
        processRequest(application.getTranslation("runtime.overlay.processing.text.auth"), authRequest, (result) -> {
            application.runtimeStateMachine.setAuthResult(result);
            if (savePassword) {
                application.runtimeSettings.login = login;
                application.runtimeSettings.encryptedPassword = password;
                application.runtimeSettings.lastAuth = authId;
            }

            CommonHelper.newThread("Get user stats", true, () -> {
                try {
                    if (application.guiModuleConfig.apiUrl == null)
                        throw new NullPointerException("Regenerate the config \"JavaRuntime.json\"");
                    URL url = new URL(String.format(application.guiModuleConfig.apiUrl, result.playerProfile.username));

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setConnectTimeout(10000);

                    InputStreamReader reader;
                    int statusCode = connection.getResponseCode();

                    if (200 <= statusCode && statusCode < 300)
                        reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
                    else
                        reader = new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8);
                    try {
                        Type itemsMapType = new TypeToken<Map<String, ServerInfoScene.UserStat[]>>() {
                        }.getType();
                        application.gui.serverInfoScene.statistics = Launcher.gsonManager.gson.fromJson(reader, itemsMapType);
                        if (application.getCurrentScene() instanceof ServerInfoScene)
                            application.gui.serverInfoScene.updateStats();
                    } catch (Exception e) {
                        application.gui.serverInfoScene.statistics = null;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            onGetProfiles();

        }, (error) -> {
            LogHelper.info("Handle error: ", error.getClass().getName());
            Throwable exception = error.getCause();
            if(exception instanceof RequestException)
            {
                if(totp != null) {
                    application.messageManager.createNotification(application.getTranslation("runtime.scenes.login.dialog2fa.header"), exception.getMessage());
                    return;
                }
                String message = exception.getMessage();
                if(message.equals(AuthRequestEvent.TWO_FACTOR_NEED_ERROR_MESSAGE))
                {
                    this.hideOverlay(0, null); //Force hide overlay
                    application.messageManager.showTextDialog(application.getTranslation("runtime.scenes.login.dialog2fa.header"), (result) -> {
                        login(login, password, authId, result, savePassword);
                    }, null, true);
                }
            }
        }, null);
    }

    public void onGetProfiles() {
        processRequest(application.getTranslation("runtime.overlay.processing.text.profiles"), new ProfilesRequest(), (profiles) -> {
            application.runtimeStateMachine.setProfilesResult(profiles);
            contextHelper.runInFxThread(() -> {
                hideOverlay(0, null);
                application.securityService.startRequest();
                if (application.gui.optionsOverlay != null) {
                    try {
                        application.gui.optionsOverlay.loadAll();
                    } catch (Throwable ex) {
                        LogHelper.error(ex);
                    }
                }
                if (application.getCurrentScene() instanceof LoginScene) {
                    ((LoginScene) application.getCurrentScene()).isLoginStarted = false;
                }
                application.setMainScene(application.gui.serverMenuScene);
            });
        }, null);
    }

    public void clearPassword() {
        application.runtimeSettings.encryptedPassword = null;
        application.runtimeSettings.login = null;
    }

    private class AuthConverter extends StringConverter<GetAvailabilityAuthRequestEvent.AuthAvailability> {

        @Override
        public String toString(GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability) {
            return authAvailability.displayName;
        }

        @Override
        public GetAvailabilityAuthRequestEvent.AuthAvailability fromString(String s) {
            for (GetAvailabilityAuthRequestEvent.AuthAvailability authAvailability : auth)
                if (authAvailability.displayName.equals(s))
                    return authAvailability;
            return null;
        }
    }
}
