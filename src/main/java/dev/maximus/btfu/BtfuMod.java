package dev.maximus.btfu;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BtfuMod implements ModInitializer {
    public static final String MOD_ID = "btfu";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static BackupManager backupManager;

    @Override
    public void onInitialize() {
        BtfuCommands.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            BtfuConfig config = BtfuConfig.loadOrCreate();

            backupManager = new BackupManager(server, config);
            backupManager.validateBeforeServerStart();
            backupManager.start();

            if (config.generateManualRollbackScripts) {
                RestoreScriptGenerator.generate(config, BackupManager.serverDirectory());
            }

            LOGGER.info("BTFU backup manager started.");
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (backupManager != null) {
                backupManager.tick();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (backupManager != null) {
                backupManager.shutdown();
                backupManager = null;
            }
        });
    }

    public static BackupManager backupManager() {
        return backupManager;
    }
}