package pro.gravit.launcher.client.gui;

import pro.gravit.launcher.LauncherNetworkAPI;
import pro.gravit.launcher.client.DirBridge;
import pro.gravit.launcher.client.UserSettings;
import pro.gravit.launcher.events.request.GetAvailabilityAuthRequestEvent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class RuntimeSettings extends UserSettings {
    public transient Path updatesDir;
    @LauncherNetworkAPI
    public String login;
    @LauncherNetworkAPI
    public byte[] encryptedPassword;
    @LauncherNetworkAPI
    public boolean autoAuth;
    @LauncherNetworkAPI
    public GetAvailabilityAuthRequestEvent.AuthAvailability lastAuth;
    @LauncherNetworkAPI
    public String updatesDirPath;
    @LauncherNetworkAPI
    public boolean fullScreen;
    @LauncherNetworkAPI
    public boolean debug;
    @LauncherNetworkAPI
    public boolean autoEnter;
    @LauncherNetworkAPI
    public UUID lastProfile;
    @LauncherNetworkAPI
    public int ram;
    @LauncherNetworkAPI
    public boolean disableJavaDownload;

    public static RuntimeSettings getDefault() {
        RuntimeSettings runtimeSettings = new RuntimeSettings();
        runtimeSettings.autoAuth = false;
        runtimeSettings.updatesDir = DirBridge.defaultUpdatesDir;
        runtimeSettings.autoEnter = false;
        runtimeSettings.fullScreen = false;
        runtimeSettings.ram = 1024;
        runtimeSettings.disableJavaDownload = false;
        return runtimeSettings;
    }

    public void apply() {
        if (updatesDirPath != null)
            updatesDir = Paths.get(updatesDirPath);
    }
}
