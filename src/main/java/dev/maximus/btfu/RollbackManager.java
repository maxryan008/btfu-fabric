package dev.maximus.btfu;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class RollbackManager {
    private RollbackManager() {
    }

    public static List<String> list(BtfuConfig config) {
        try {
            if (Files.notExists(config.snapshotsDirectory())) {
                return List.of();
            }

            return Files.list(config.snapshotsDirectory())
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to list rollback snapshots.", exception);
        }
    }

    public static String latest(BtfuConfig config) {
        List<String> snapshots = list(config);
        return snapshots.isEmpty() ? null : snapshots.getFirst();
    }

    public static void restoreAfterShutdown(BackupManager manager, String snapshotId) {
        MinecraftServer server = manager.server();
        BtfuConfig config = manager.config();

        Path snapshot = config.snapshotsDirectory().resolve(snapshotId);

        if (Files.notExists(snapshot) || !Files.isDirectory(snapshot)) {
            throw new IllegalArgumentException("Snapshot does not exist or is compressed: " + snapshotId);
        }

        if (manager.isRunningBackup()) {
            throw new IllegalStateException("Cannot start rollback while a backup is already running.");
        }

        manager.broadcastToAdmins("[BTFU] Rollback requested: " + snapshotId);

        if (!config.createPreRollbackSafetyBackup) {
            launchScriptAndStopServer(server, config, snapshotId);
            return;
        }

        manager.broadcastToAdmins("[BTFU] Creating pre-rollback safety backup...");

        boolean queued = manager.requestBackup(
                "pre rollback safety backup",
                () -> {
                    manager.broadcastToAdmins("[BTFU] Safety backup finished. Stopping server for rollback...");
                    launchScriptAndStopServer(server, config, snapshotId);
                },
                throwable -> manager.broadcastToAdmins("[BTFU] Rollback cancelled because safety backup failed: " + throwable.getMessage())
        );

        if (!queued) {
            throw new IllegalStateException("Failed to queue pre-rollback safety backup.");
        }
    }

    private static void launchScriptAndStopServer(MinecraftServer server, BtfuConfig config, String snapshotId) {
        Path script = BackupManager.serverDirectory().resolve("manual_rollback").resolve("rollback.sh");

        if (Files.notExists(script)) {
            RestoreScriptGenerator.generate(config, BackupManager.serverDirectory());
        }

        long pid = ProcessHandle.current().pid();

        try {
            new ProcessBuilder("bash", script.toString(), "restore-after-pid", snapshotId, Long.toString(pid))
                    .directory(BackupManager.serverDirectory().toFile())
                    .inheritIO()
                    .start();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to launch rollback script.", exception);
        }

        server.halt(false);
    }

    public static List<String> listFormatted(BtfuConfig config) {
        try {
            if (Files.notExists(config.snapshotsDirectory())) {
                return List.of();
            }

            return Files.list(config.snapshotsDirectory())
                    .filter(Files::isDirectory)
                    .map(path -> SnapshotDisplayFormatter.format(config, path))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to list rollback snapshots.", exception);
        }
    }
}