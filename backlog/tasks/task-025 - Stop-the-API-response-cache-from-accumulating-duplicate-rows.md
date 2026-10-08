---
id: TASK-025
title: Stop the API response cache from accumulating duplicate rows
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 19:27'
updated_date: '2026-10-08 19:03'
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
- [ ] #1 Writing the same key twice leaves one row for that key
- [ ] #2 A read after a second write returns the value that was written last
- [ ] #3 Existing databases carrying duplicate rows are migrated or discarded without crashing
- [ ] #4 PersistentApiCacheTest asserts replacement instead of accumulation
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Key the apicache table on name (primary key), so the DAO's REPLACE insert overwrites the record for a key instead of appending a row.
2. Bump PersistentApiDb to version 2; the existing destructive fallback discards version-1 files, duplicates included. A 24-hour response cache loses nothing worth migrating.
3. Expose a non-shared database builder so an instrumented test can open a hand-built version-1 file carrying duplicate rows and prove the upgrade discards it without crashing.
4. Replace the pinning test in PersistentApiCacheTest with replacement assertions: one row per key, last write wins, a fresh write supersedes a stale record.
<!-- SECTION:PLAN:END -->
