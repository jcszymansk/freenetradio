---
id: TASK-025
title: Stop the API response cache from accumulating duplicate rows
status: To Do
assignee: []
created_date: '2026-09-17 19:27'
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
