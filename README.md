# BTFU Fabric

Continuous incremental rsync-based backups for Fabric 1.21.1 servers using hardlinked snapshots.

BTFU Fabric is a modern Fabric remake of the original BTFU backup mod, designed for:

* fast incremental backups
* extremely low disk usage
* simple snapshot restores
* automated rollback workflows
* emergency offline recovery

The system uses:

* `rsync` for incremental syncing
* `cp -al` hardlinked snapshots
* optional snapshot compression
* automatic rollback script generation

---

# Features

* Incremental rsync backups
* Hardlinked snapshots (low storage usage)
* Automatic scheduled backups
* Manual backups
* Snapshot rollback system
* Snapshot locking
* Snapshot metadata
* Optional snapshot compression
* Emergency offline restore scripts
* Automatic rollback safety backups
* Admin backup notifications
* Linux-first architecture

---

# Requirements

## Linux

Required packages:

```bash
sudo apt install rsync coreutils tar
```

## Java

Java 21 required.

## Minecraft

* Minecraft 1.21.1
* Fabric Loader
* Fabric API

---

# Installation

Place the mod jar into:

```text
mods/
```

Start the server once.

The mod generates:

```text
config/btfu.json
```

---

# Configuration

Example config:

```json
{
  "enabled": true,
  "backupDirectory": "/home/user/backups/btfu",
  "intervalSeconds": 300,
  "maxBackups": 128,

  "failServerStartWithoutBackupDirectory": true,
  "failIfRunningInsideBackupDirectory": true,
  "disableSavingDuringBackup": true,

  "runInitialBackupAfterStartup": false,
  "initialBackupDelaySeconds": 60,

  "broadcastBackupStart": true,
  "broadcastBackupFinish": true,
  "broadcastBackupFailures": true,
  "broadcastToOpsOnly": true,

  "generateManualRollbackScripts": true,
  "createPreRollbackSafetyBackup": true,

  "compressOldSnapshots": false,
  "compressSnapshotsOlderThanDays": 7,
  "compressionCommand": "tar",
  "compressionExtension": ".tar.gz",

  "rsyncExcludes": [
    "backups/**",
    "logs/latest.log",
    "crash-reports/**",
    "session.lock",
    "manual_rollback/**"
  ]
}
```

---

# Important Configuration Notes

## backupDirectory

Must be outside the server folder.

Correct:

```text
/home/user/backups/btfu
```

Incorrect:

```text
server/backups
```

The mod prevents recursive backup setups automatically.

---

# Backup Structure

Example:

```text
backupDirectory/
├── _model/
├── snapshots/
│   ├── 2026-05-24_14-17-26/
│   ├── 2026-05-24_14-20-40/
│   └── 2026-05-24_14-25-12/
├── locks/
└── compressed/
```

---

# How Snapshots Work

Snapshots are NOT full copies.

The mod uses:

```bash
cp -al
```

which creates hardlinks.

This means:

* snapshots appear full-sized
* unchanged files are shared
* only changed files consume extra disk space

Example:

```text
Snapshot 1: 4.5 GB
Snapshot 2: 200 MB additional usage
Snapshot 3: 150 MB additional usage
```

---

# Commands

## Manual Backup

```text
/btfu backup now
```

Creates an immediate backup.

---

## Backup Status

```text
/btfu status
```

Shows:

* backup running state
* next scheduled backup
* last backup result

---

## List Snapshots

```text
/btfu list
```

Example:

```text
[BTFU] 2026-05-24_14-25-12 - reason: pre rollback safety backup
[BTFU] 2026-05-24_14-20-40 - reason: manual command
[BTFU] 2026-05-24_14-17-26 [LOCKED] - reason: manual command
```

---

# Rollback Commands

## List Rollback Snapshots

```text
/btfu rollback list
```

---

## Restore Latest Snapshot

```text
/btfu rollback latest
```

---

## Restore Specific Snapshot

```text
/btfu rollback restore <snapshot>
```

Example:

```text
/btfu rollback restore 2026-05-24_14-17-26
```

---

# Rollback Process

Rollback is fully automated.

The sequence is:

```text
1. Create safety backup
2. Wait for backup completion
3. Launch external restore script
4. Safely stop Minecraft server
5. Wait for process shutdown
6. Restore selected snapshot
```

This prevents corruption from restoring while the server is still running.

---

# Snapshot Locking

Locked snapshots cannot be:

* culled
* compressed

## Lock Snapshot

```text
/btfu snapshot lock <snapshot>
```

## Unlock Snapshot

```text
/btfu snapshot unlock <snapshot>
```

Locked snapshots show:

```text
[LOCKED]
```

in snapshot lists.

---

# Snapshot Metadata

Every snapshot contains:

```text
snapshot.json
```

Example:

```json
{
  "snapshotId": "2026-05-24_14-17-26",
  "createdAtUtc": "2026-05-24T14:17:26.452719818Z",
  "reason": "manual command",
  "minecraftVersion": "1.21.1",
  "serverDirectory": "/server",
  "compressed": false,
  "backupDurationMs": 89
}
```

---

# Snapshot Compression

Optional automatic compression of older snapshots.

Enable:

```json
"compressOldSnapshots": true
```

Older snapshots become:

```text
2026-05-24_14-17-26.tar.gz
```

Compressed snapshots are currently:

* archival only
* not directly rollbackable from commands

They must be extracted manually before restore.

---

# Emergency Offline Recovery

The mod automatically generates:

```text
manual_rollback/
├── rollback.sh
└── rollback.bat
```

These scripts allow recovery WITHOUT starting Minecraft.

Useful when:

* the server is corrupted
* mods crash on startup
* world loading fails
* rollback commands cannot be accessed

---

# Linux Emergency Recovery

Go to:

```bash
cd manual_rollback
```

## List Snapshots

```bash
./rollback.sh list
```

## Restore Latest Snapshot

```bash
./rollback.sh latest
```

## Restore Specific Snapshot

```bash
./rollback.sh restore 2026-05-24_14-17-26
```

---

# Windows Emergency Recovery

## List Snapshots

```bat
rollback.bat list
```

## Restore Latest Snapshot

```bat
rollback.bat latest
```

## Restore Specific Snapshot

```bat
rollback.bat restore 2026-05-24_14-17-26
```

---

# Safety Notes

## Never Restore While Minecraft Is Running

Always use:

* `/btfu rollback restore`
* or the generated rollback scripts

Never manually overwrite files while the server is active.

---

# Recommended Setup

Recommended:

* backup directory on another drive
* nightly offsite sync
* lock important milestones
* keep automatic safety backups enabled

Example:

```text
/mnt/storage/btfu-backups
```

---

# Known Limitations

Current limitations:

* compressed snapshots are not auto-restorable
* no partial chunk rollback yet
* no GUI restore browser yet
* no Windows-native hardlink optimization yet
* live chunk rollback not implemented

---

# Planned Features

Planned:

* client admin GUI
* chunk-level rollback
* region diff viewer
* restore previews
* snapshot tagging
* rollback scheduling
* offsite sync support
* web dashboard
* restore verification
* live chunk unload/reload rollback system

---

# License

All Rights Reserved.

This software may not be redistributed, modified, republished, or used in derivative works without explicit permission from the copyright holder.
