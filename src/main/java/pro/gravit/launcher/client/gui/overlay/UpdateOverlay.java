package pro.gravit.launcher.client.gui.overlay;

import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import pro.gravit.launcher.AsyncDownloader;
import pro.gravit.launcher.client.gui.JavaFXApplication;
import pro.gravit.launcher.client.gui.helper.LookupHelper;
import pro.gravit.launcher.client.gui.raw.AbstractOverlay;
import pro.gravit.launcher.client.gui.raw.ContextHelper;
import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.hasher.HashedFile;
import pro.gravit.launcher.profiles.optional.OptionalView;
import pro.gravit.launcher.profiles.optional.actions.OptionalAction;
import pro.gravit.launcher.profiles.optional.actions.OptionalActionFile;
import pro.gravit.launcher.request.update.UpdateRequest;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class UpdateOverlay extends AbstractOverlay {
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private final AtomicLong lastUpdateTime = new AtomicLong(0);
    private final AtomicLong lastDownloaded = new AtomicLong(0);
    private ProgressBar progressBar;
    private Circle[] phases;
    private Label speed;
    private Label volume;
    private TextArea logOutput;
    private Text currentStatus;
    private long totalSize;
    private int currentPhase = 0;
    private double phaseOffset;
    private double progressRatio = 1.0;
    private double phaseRatio;

    public UpdateOverlay(JavaFXApplication application) {
        super("overlay/update/update.fxml", application);
    }

    @Override
    protected void doInit() {
        progressBar = LookupHelper.lookup(pane, "#progress");
        phases = new Circle[5];
        for (int i = 1; i <= 5; ++i) {
            Circle circle = LookupHelper.lookup(pane, String.format("#phase%d", i));
            phases[i - 1] = circle;
            phaseOffset = (circle.getRadius() * 2.0) / progressBar.getPrefWidth();
            progressRatio -= phaseOffset;
        }
        phaseRatio = progressRatio / 4.0;
        speed = LookupHelper.lookup(pane, "#speed");
        volume = LookupHelper.lookup(pane, "#volume");
        logOutput = LookupHelper.lookup(pane, "#outputUpdate");
        currentStatus = LookupHelper.lookup(pane, "#headingUpdate");
        logOutput.setText("");
        LookupHelper.<ButtonBase>lookup(pane, "#close").setOnAction(
                (e) -> Platform.exit());
        LookupHelper.<ButtonBase>lookup(pane, "#hide").setOnAction((e) -> {
            if (this.currentStage != null) this.currentStage.hide();
        });
        RotateTransition rotate = new RotateTransition();
        rotate.setAxis(Rotate.Z_AXIS);
        rotate.setByAngle(360);
        rotate.setDuration(Duration.millis(500));
        rotate.setCycleCount(1000);
        rotate.setNode(LookupHelper.lookup(pane, "#secondPath"));
        rotate.play();
    }

    private void deleteExtraDir(Path subDir, HashedDir subHDir, boolean deleteDir) throws IOException {
        for (Map.Entry<String, HashedEntry> mapEntry : subHDir.map().entrySet()) {
            String name = mapEntry.getKey();
            Path path = subDir.resolve(name);

            // Delete list and dirs based on type
            HashedEntry entry = mapEntry.getValue();
            HashedEntry.Type entryType = entry.getType();
            switch (entryType) {
                case FILE:
                    Files.delete(path);
                    break;
                case DIR:
                    deleteExtraDir(path, (HashedDir) entry, deleteDir || entry.flag);
                    break;
                default:
                    throw new AssertionError("Unsupported hashed entry type: " + entryType.name());
            }
        }

        // Delete!
        if (deleteDir) {
            Files.delete(subDir);
        }
    }
    private static class PathRemapperData {
        public String key;
        public String value;

        public PathRemapperData(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
    @SuppressWarnings("rawtypes")
    public void sendUpdateRequest(String dirName, Path dir, FileNameMatcher matcher, boolean digest, OptionalView view, boolean optionalsEnabled, Consumer<HashedDir> onSuccess) {
        UpdateRequest request = new UpdateRequest(dirName);
        try {
            application.service.request(request).thenAccept(updateRequestEvent -> {
                LogHelper.dev("Start updating %s", dirName);
                totalDownloaded.set(0);
                lastUpdateTime.set(System.currentTimeMillis());
                lastDownloaded.set(0);
                totalSize = 0;
                if (optionalsEnabled) {
                    for(OptionalAction action : view.getDisabledActions())
                    {
                        if(action instanceof OptionalActionFile)
                        {
                            ((OptionalActionFile) action).disableInHashedDir(updateRequestEvent.hdir);
                        }
                    }
                }
                try {
                    LinkedList<PathRemapperData> pathRemapper = new LinkedList<>();
                    if(optionalsEnabled)
                    {
                        Set<OptionalActionFile> fileActions = view.getActionsByClass(OptionalActionFile.class);
                        for(OptionalActionFile file : fileActions)
                        {
                            file.injectToHashedDir(updateRequestEvent.hdir);
                            file.files.forEach((k,v) -> {
                                if(v == null || v.isEmpty()) return;
                                pathRemapper.add(new PathRemapperData(v, k)); //reverse (!)
                                LogHelper.dev("Remap prepare %s to %s", v, k);
                            });
                        }
                    }
                    pathRemapper.sort(Comparator.comparingInt(c -> -c.key.length())); // Support deep remap
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Hashing %s", dirName)));
                    if (!IOHelper.exists(dir))
                        Files.createDirectories(dir);
                    HashedDir hashedDir = new HashedDir(dir, matcher, false /* TODO */, digest);
                    HashedDir.Diff diff = updateRequestEvent.hdir.diff(hashedDir, matcher);
                    final List<AsyncDownloader.SizedFile> adds = new ArrayList<>();
                    diff.mismatch.walk(IOHelper.CROSS_SEPARATOR, (path, name, entry) -> {
                        String urlPath = path;
                        switch (entry.getType()) {
                            case FILE:
                                HashedFile file = (HashedFile) entry;
                                totalSize += file.size;
                                adds.add(new AsyncDownloader.SizedFile(path, file.size));
                                for(PathRemapperData remapEntry : pathRemapper)
                                {
                                    if(path.startsWith(remapEntry.key))
                                    {
                                        urlPath = path.replace(remapEntry.key, remapEntry.value);
                                        LogHelper.dev("Remap found: injected url path: %s | calculated original url path: %s", path, urlPath);
                                    }
                                }
                                adds.add(new AsyncDownloader.SizedFile(urlPath, path, file.size));
                                break;
                            case DIR:
                                Files.createDirectories(dir.resolve(path));
                                break;
                        }
                        return HashedDir.WalkAction.CONTINUE;
                    });
                    LogHelper.info("Diff %d %d", diff.mismatch.size(), diff.extra.size());
                    ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Downloading %s...", dirName)));
                    AsyncDownloader asyncDownloader = new AsyncDownloader((d) -> {
                        long old = totalDownloaded.getAndAdd(d);
                        updateProgress(old, old + d);
                    });
                    List<List<AsyncDownloader.SizedFile>> tasks = asyncDownloader.sortFiles(adds, 4);
                    CompletableFuture[] futures = asyncDownloader.runDownloadList(tasks, updateRequestEvent.url, dir, application.workers);
                    CompletableFuture.allOf(futures).thenAccept((e) -> {
                        ContextHelper.runInFxThreadStatic(() -> addLog(String.format("Delete Extra files %s", dirName)));
                        try {
                            deleteExtraDir(dir, diff.extra, diff.extra.flag);
                            onSuccess.accept(updateRequestEvent.hdir);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }).exceptionally((e) -> {
                        ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
                        return null;
                    });

                } catch (IOException e) {
                    ContextHelper.runInFxThreadStatic(() -> errorHandle(e));
                }
            }).exceptionally((error) -> {
                ContextHelper.runInFxThreadStatic(() -> errorHandle(error.getCause()));
                // hide(2500, scene, onError);
                return null;
            });
        } catch (IOException e) {
            errorHandle(e);
        }
    }

    private void addLog(String string) {
        LogHelper.dev("Update event %s", string);
        logOutput.appendText(string.concat("\n"));
    }

    public void initNewPhase(String name) {
        currentStatus.setText(name);
        phases[currentPhase].getStyleClass().add("phaseActive");
        DoubleProperty property = progressBar.progressProperty();
        property.set(phaseOffset + (phaseOffset + phaseRatio) * currentPhase);
        LogHelper.debug("NewPhase %f", progressBar.progressProperty().doubleValue());
        currentPhase++;
    }

    private void updateProgress(long oldValue, long newValue) {
        double add = (double) (newValue - oldValue) / (double) totalSize; // 0.0 - 1.0
        DoubleProperty property = progressBar.progressProperty();
        property.set(property.get() + add * phaseRatio);
        long lastTime = lastUpdateTime.get();
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTime >= 130) {
            String format = String.format("%.1f MB / %.1f MB", (double) newValue / (1024.0 * 1024.0), (double) totalSize / (1024.0 * 1024.0));
            double bytesSpeed = (double) (newValue - lastDownloaded.get()) / (double) (currentTime - lastTime) * 1000.0;
            String speedFormat = String.format("%.2f MiB/s", bytesSpeed * 8 / (1000.0 * 1000.0));
            ContextHelper.runInFxThreadStatic(() -> {
                volume.setText(format);
                speed.setText(speedFormat);
            });
            lastUpdateTime.set(currentTime);
            lastDownloaded.set(newValue);
        }

    }

    @Override
    public void reset() {
        progressBar.progressProperty().setValue(0);
        logOutput.clear();
        volume.setText("");
        speed.setText("");
        for (Circle circle : phases) {
            circle.getStyleClass().removeAll("phaseActive");
            circle.getStyleClass().removeAll("phaseError");
        }
        currentPhase = 0;
    }

    @Override
    public void errorHandle(Throwable e) {
        addLog(String.format("Exception %s: %s", e.getClass(), e.getMessage() == null ? "" : e.getMessage()));
        phases[currentPhase].getStyleClass().add("phaseError");
        LogHelper.error(e);
    }
}
