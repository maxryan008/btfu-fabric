package dev.maximus.btfu;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class BackupTask implements Runnable {
    private static final DateTimeFormatter SNAPSHOT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC);

    private final MinecraftServer server;
    private final BtfuConfig config;
    private final String reason;

    public BackupTask(MinecraftServer server, BtfuConfig config, String reason) {
        this.server = server;
        this.config = config;
        this.reason = reason;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();

        String snapshotId = SNAPSHOT_FORMAT.format(Instant.now());
        Path source = BackupManager.serverDirectory();
        Path model = this.config.modelDirectory();
        Path snapshot = this.config.snapshotsDirectory().resolve(snapshotId);

        BtfuMod.LOGGER.info("Starting BTFU backup. reason={}, source={}, model={}, snapshot={}",
                this.reason, source, model, snapshot);

        boolean savingDisabled = false;

        try {
            BackupManager.flushServer(this.server);

            if (this.config.disableSavingDuringBackup) {
                BackupManager.disableSaving(this.server);
                savingDisabled = true;
            }

            FileActions.rsyncToModel(source, model, this.config.rsyncExcludes);
            FileActions.hardlinkCopy(model, snapshot);

            long duration = System.currentTimeMillis() - start;

            SnapshotMetadata metadata = new SnapshotMetadata(
                    snapshotId,
                    Instant.now().toString(),
                    this.reason,
                    SharedConstants.getCurrentVersion().getName(),
                    source.toString(),
                    false,
                    duration
            );

            SnapshotMetadata.write(snapshot, metadata);
            BackupCullPolicy.cull(this.config);

            BtfuMod.LOGGER.info("Finished BTFU backup in {} ms: {}", duration, snapshot);
        } finally {
            if (savingDisabled) {
                BackupManager.enableSaving(this.server);
            }
        }
    }
}