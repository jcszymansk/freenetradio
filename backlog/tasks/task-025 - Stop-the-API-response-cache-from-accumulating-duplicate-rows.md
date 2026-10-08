---
id: TASK-025
title: Stop the API response cache from accumulating duplicate rows
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 19:27'
updated_date: '2026-10-08 19:14'
labels: []
dependencies: []
type: bug
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PersistentApiEntry keys the apicache table on an auto-generated id and nothing constrains the 'name' column, so PersistentApiCache.put inserts a new row every time instead of replacing the record for that key. The DAO then reads with 'SELECT * FROM apicache WHERE name = :key LIMIT 1', which serves the oldest row, so a refreshed response is never seen and the table grows without bound. Found while writing the Phase 3 persistence tests (TASK-003); PersistentApiCacheTest currently pins the duplicate-row behaviour and has to be updated together with the fix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Writing the same key twice leaves one row for that key
- [x] #2 A read after a second write returns the value that was written last
- [x] #3 Existing databases carrying duplicate rows are migrated or discarded without crashing
- [x] #4 PersistentApiCacheTest asserts replacement instead of accumulation
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Key the apicache table on name (primary key), so the DAO's REPLACE insert overwrites the record for a key instead of appending a row.
2. Bump PersistentApiDb to version 2; the existing destructive fallback discards version-1 files, duplicates included. A 24-hour response cache loses nothing worth migrating.
3. Expose a non-shared database builder so an instrumented test can open a hand-built version-1 file carrying duplicate rows and prove the upgrade discards it without crashing.
4. Replace the pinning test in PersistentApiCacheTest with replacement assertions: one row per key, last write wins, a fresh write supersedes a stale record.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Room 2.6.1 RoomOpenHelper (disassembled): with no migration path and fallbackToDestructiveMigration, onUpgrade drops and recreates tables without reading the old identity hash; onDowngrade takes the same path, so a downgrade also discards rather than crashes. Validation: ./gradlew test verifyPureCoreCoverage PASS; ./gradlew :app:connectedDebugAndroidTest PASS (226 tests, offline emulator, cleared app data). Codex review round 1: PASS, no findings.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Keyed the apicache table on the request (PersistentApiEntry.name is the primary key), so the DAO's REPLACE insert overwrites a request's record instead of appending one, and the read returns the latest write. PersistentApiDb moved to version 2; the existing destructive fallback discards version 1 files with their duplicate rows, since a 24-hour cache holds nothing worth migrating. PersistentApiCacheTest now asserts one row per key, last write wins, a write supersedes a stale record and leaves other keys alone. The new PersistentApiDbUpgradeTest seeds a version 1 file with Room's exact version 1 DDL and identity hash plus duplicate rows and opens it through PersistentApiDb.buildDatabase (now public for that purpose). Verified with the full JVM tests, the pure-core coverage gate and the full instrumented suite (226 tests).
<!-- SECTION:FINAL_SUMMARY:END -->
