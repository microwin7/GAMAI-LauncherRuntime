package pro.gravit.launcher.client;

import pro.gravit.launcher.LauncherInject;

public class GuiModuleConfig {
    @LauncherInject(value = "modules.javaruntime.createaccounturl")
    public String createAccountURL;
    @LauncherInject(value = "modules.javaruntime.forgotpassurl")
    public String forgotPassURL;
    @LauncherInject(value = "modules.javaruntime.vkurl")
    public String vkURL;
    @LauncherInject(value = "modules.javaruntime.youtubeurl")
    public String youtubeURL;
    @LauncherInject(value = "modules.javaruntime.discordurl")
    public String discordURL;
    @LauncherInject(value = "modules.javaruntime.hastebinserver")
    public String hastebinServer;
    @LauncherInject(value = "modules.javaruntime.apiurl")
    public String apiUrl;
    @LauncherInject(value = "modules.javaruntime.enabledownloadjava")
    public boolean enableDownloadJava;
    @LauncherInject(value = "modules.javaruntime.userdisabledownloadjava")
    public boolean userDisableDownloadJava;
    @LauncherInject(value = "modules.javaruntime.jvmwindows64dir")
    public String jvmWindows64Dir;
    @LauncherInject(value = "modules.javaruntime.jvmwindows32dir")
    public String jvmWindows32Dir;

    public static Object getDefault() {
        GuiModuleConfig config = new GuiModuleConfig();
        config.createAccountURL = "https://gravit.pro/createAccount.php";
        config.forgotPassURL = "https://gravit.pro/fogotPass.php";
        config.vkURL = "https://example.com";
        config.youtubeURL = "https://example.com";
        config.discordURL = "https://example.com";
        config.hastebinServer = "https://hastebin.com";
        config.apiUrl = "https://gamai.ru/minecraft-auth/launcher_statistics21.php?username=%s";
        config.enableDownloadJava = false;
        config.userDisableDownloadJava = true;
        config.jvmWindows64Dir = "java-windows-x64";
        config.jvmWindows32Dir = "java-windows-x32";
        return config;
    }
}
