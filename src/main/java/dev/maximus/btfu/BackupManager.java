package dev.maximus.btfu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BackupManager {
    private final MinecraftServer server;
    private final BtfuConfig config;
    private final ExecutorService executor;
    private final AtomicBoolean runningBackup = new AtomicBoolean(false);

    private long ticksUntilNextBackup;
    private volatile String lastBackupStatus = "No backup has run yet.";
    private volatile long lastBackupDurationMs = -1L;

    public BackupManager(MinecraftServer server, BtfuConfig config) {
        this.server = Objects.requireNonNull(server);
        this.config = Objects.requireNonNull(config);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BTFU Backup Thread");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void validateBeforeServerStart() {
        if (!this.config.enabled) {
            BtfuMod.LOGGER.warn("BTFU is disabled.");
            return;
        }

        if (this.config.backupDirectory == null || this.config.backupDirectory.isBlank()) {
            if (this.config.failServerStartWithoutBackupDirectory) {
                throw new IllegalStateException("BTFU backupDirectory is blank. Set it in config/btfu.json.");
            }

            BtfuMod.LOGGER.error("BTFU backupDirectory is blank.");
            return;
        }

        Path serverDirectory = serverDirectory();
        Path backupDirectory = this.config.resolveBackupDirectory();

        if (this.config.failIfRunningInsideBackupDirectory && serverDirectory.startsWith(backupDirectory)) {
            throw new IllegalStateException("Server directory cannot be inside the BTFU backup directory.");
        }

        try {
            Files.createDirectories(this.config.modelDirectory());
            Files.createDirectories(this.config.snapshotsDirectory());
            Files.createDirectories(this.config.locksDirectory());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create BTFU directories.", exception);
        }
    }

    public void start() {
        this.ticksUntilNextBackup = this.config.runInitialBackupAfterStartup
                ? secondsToTicks(this.config.initialBackupDelaySeconds)
                : secondsToTicks(this.config.intervalSeconds);
    }

    public void tick() {
        if (!this.config.enabled || this.runningBackup.get()) {
            return;
        }

        this.ticksUntilNextBackup--;

        if (this.ticksUntilNextBackup <= 0) {
            this.ticksUntilNextBackup = secondsToTicks(this.config.intervalSeconds);
            this.requestBackup("scheduled");
        }
    }

    public boolean requestBackup(String reason) {
        return this.requestBackup(reason, null, null);
    }

    public boolean requestBackup(String reason, Runnable onSuccess, Consumer<Throwable> onFailure) {
        if (!this.runningBackup.compareAndSet(false, true)) {
            BtfuMod.LOGGER.warn("Skipped backup because another backup is already running.");
            return false;
        }

        if (this.config.broadcastBackupStart) {
            this.broadcastToAdmins("[BTFU] Starting backup...");
        }

        this.executor.submit(() -> {
            long start = System.currentTimeMillis();

            try {
                new BackupTask(this.server, this.config, reason).run();

                this.lastBackupDurationMs = System.currentTimeMillis() - start;
                this.lastBackupStatus = "Last backup succeeded in " + this.lastBackupDurationMs + " ms.";

                if (this.config.compressOldSnapshots) {
                    SnapshotCompressor.compressOldSnapshots(this.config);
                }

                if (this.config.broadcastBackupFinish) {
                    this.broadcastToAdmins("[BTFU] Backup completed in " + this.lastBackupDurationMs + " ms.");
                }

                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Throwable throwable) {
                this.lastBackupStatus = "Last backup failed: " + throwable.getMessage();
                BtfuMod.LOGGER.error("BTFU backup failed.", throwable);

                if (this.config.broadcastBackupFailures) {
                    this.broadcastToAdmins("[BTFU] Backup failed: " + throwable.getMessage());
                }

                if (onFailure != null) {
                    onFailure.accept(throwable);
                }
            } finally {
                this.runningBackup.set(false);
            }
        });

        return true;
    }

    public boolean isRunningBackup() {
        return this.runningBackup.get();
    }

    public String statusText() {
        return "[BTFU] running=" + this.runningBackup.get()
                + ", nextBackupTicks=" + this.ticksUntilNextBackup
                + ", status=" + this.lastBackupStatus;
    }

    public List<String> listSnapshots() {
        try {
            if (Files.notExists(this.config.snapshotsDirectory())) {
                return List.of("No snapshots exist.");
            }

            return Files.list(this.config.snapshotsDirectory())
                    .filter(path -> Files.isDirectory(path) || path.getFileName().toString().endsWith(this.config.compressionExtension))
                    .map(path -> SnapshotDisplayFormatter.format(this.config, path))
                    .sorted(Comparator.reverseOrder())
                    .limit(50)
                    .toList();
        } catch (Exception exception) {
            return List.of("Failed to list snapshots: " + exception.getMessage());
        }
    }

    public BtfuConfig config() {
        return this.config;
    }

    public MinecraftServer server() {
        return this.server;
    }

    public void shutdown() {
        this.executor.shutdownNow();
    }

    public void broadcastToAdmins(String message) {
        this.server.execute(() -> {
            Component component = Component.literal(message);

            if (!this.config.broadcastToOpsOnly) {
                this.server.getPlayerList().broadcastSystemMessage(component, false);
                return;
            }

            for (var player : this.server.getPlayerList().getPlayers()) {
                if (this.server.getPlayerList().isOp(player.getGameProfile())) {
                    player.sendSystemMessage(component);
                }
            }
        });
    }

    public static Path serverDirectory() {
        return Path.of(".").toAbsolutePath().normalize();
    }

    static void disableSaving(MinecraftServer server) {
        server.executeBlocking(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                level.noSave = true;
            }
        });
    }

    static void enableSaving(MinecraftServer server) {
        server.executeBlocking(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                level.noSave = false;
            }
        });
    }

    static void flushServer(MinecraftServer server) {
        server.executeBlocking(() -> server.saveAllChunks(false, true, true));
    }

    private static long secondsToTicks(int seconds) {
        return seconds * 20L;
    }
}