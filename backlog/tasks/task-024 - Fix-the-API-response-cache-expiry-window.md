---
id: TASK-024
title: Fix the API response cache expiry window
status: To Do
assignee: []
created_date: '2026-09-17 19:27'
labels: []
dependencies: []
type: bug
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PersistentApiCache treats a record as fresh while 'System.currentTimeMillis() - record.timestamp <= SEC_IN_DAY', but SEC_IN_DAY is 86400 and the difference is in milliseconds, so the cache expires after 86.4 seconds instead of the intended 24 hours. Every browse that falls through the memory cache therefore goes back to the network almost immediately, which defeats the offline and low-data behaviour the cache exists for. Found while writing the Phase 3 persistence tests (TASK-003); PersistentApiCacheTest currently pins the 86.4 second behaviour and has to be updated together with the fix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The freshness window is 24 hours of wall-clock time
- [ ] #2 The unit of the constant is unambiguous in its name and its use
- [ ] #3 PersistentApiCacheTest asserts the 24 hour boundary instead of the millisecond one
<!-- AC:END -->
