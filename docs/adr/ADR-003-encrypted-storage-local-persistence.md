# ADR-003: Local Persistence & Backup Strategy

**Date:** 2026-08-05 (Updated: 2026-11-26)
**Status:** Accepted
**Phase:** 3 — Database & Local Persistence Layer (hardened in v2.7.0)
**Requirements:** REQ-LIB-03, REQ-OFF-03, REQ-ENG-09

---

## Context

CLIBeats persists:
1. **Track library data** — songs, playlists, play history, downloaded track metadata, persisted playback queue, and cached lyrics (large structured data, complex queries).
2. **User preferences** — theme accent, audio quality, download quality, local library scan folders, and backup preferences (small key-value pairs; CLIBeats no longer stores remote user auth tokens).

This ADR records the actual storage implementation so documentation accurately matches security boundaries.

## Decision

### Primary Storage: Room 2.6.1 with KSP

Use **Room 2.6.1** as the local relational database, compiled via KSP (not deprecated KAPT).

**Entities:** `SongEntity`, `PlaylistEntity`, `PlaylistSongCrossRef`, `HistoryEntity`, `DownloadedTrackEntity`, `QueueEntity`, `LyricsCacheEntity`
**DAOs:** Flow-based reactive queries for UI; suspend functions for writes.
**Schema:** `exportSchema = true` — JSON schema files committed to `app/schemas/` for migration verification. No destructive migrations (`fallbackToDestructiveMigration()` is forbidden).

**Rationale:**
- KSP is 2-3x faster than KAPT for incremental builds.
- `Flow<T>` DAOs avoid manual polling and drive Compose `collectAsStateWithLifecycle()`.
- `exportSchema` enables Room's automated `MigrationTestHelper` verification.
- History retention is bounded via daily pruning in `HistoryRepositoryImpl`.
- `cache_index` (`CacheIndexEntity`) was removed in Room schema v5 along with legacy `CacheManager`.

### Preferences Storage: Plaintext-in-Sandbox DataStore Preferences

CLIBeats uses **Jetpack DataStore Preferences 1.1.1** (`clibeats_prefs.preferences_pb`) inside app-private internal storage (OS sandbox protection).

- No `EncryptedSharedPreferences` or `androidx.security:security-crypto` dependency is used at runtime.
- No account credentials or API secrets are stored on disk, so Keystore-backed keysets are unnecessary.
- Android OS file-based encryption (FBE) and app sandbox isolation protect `clibeats_prefs.preferences_pb` and `clibeats.db` at rest on modern Android devices.

### Cloud Backup & Public Snapshot Controls

- `android:allowBackup="true"` remains enabled for DataStore preferences and manual restore state, while `res/xml/backup_rules.xml` (API 26–30) and `res/xml/data_extraction_rules.xml` (API 31+) explicitly exclude:
  - `clibeats_secure_prefs.xml` (legacy encrypted prefs)
  - `clibeats.db`, `clibeats.db-wal`, `clibeats.db-shm` (unencrypted local history/library database)
  - `downloads` (offline media files)
- Automatic daily library snapshots (`LibrarySnapshotManager`) write to internal storage by default and only mirror `clibeats-backup.json` to shared `Download/CLIBeats/` storage when enabled by the user in Settings (`mirrorBackupsToDownloads`).

### Repository Pattern

All DAO access is mediated via Repository interfaces defined in `domain/repository/`. ViewModels MUST NOT import DAOs directly.

**Mappers** (`SongMapper.kt`, `PlaylistMapper.kt`) handle entity ↔ domain model conversion at the data layer boundary. Domain models are pure Kotlin data classes with no Room annotations.

## Consequences

### Positive
- Clean architecture boundary: domain layer has zero dependency on Room or DataStore.
- No `EncryptedSharedPreferences` main-thread blocking or keyset corruption risk.
- Sensitive local database history and downloaded media never leak into cloud backups or device transfers.
- Users control whether plaintext JSON snapshots are mirrored to shared `Download/CLIBeats/` storage.

### Negative
- If authenticated third-party accounts are added in the future, credential storage must introduce Tink (`security-crypto` / Keystore AEAD) rather than storing tokens in DataStore Preferences.

### Neutral
- Manual `Migration` objects with `MigrationTestHelper` coverage are required for all schema upgrades.
