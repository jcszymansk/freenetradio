---
id: TASK-024
title: Fix the API response cache expiry window
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 19:27'
updated_date: '2026-10-08 19:49'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace SEC_IN_DAY with a millisecond constant derived from TimeUnit.DAYS, named for its unit, and use it in the freshness check.
2. Move the freshness comparison into a small pure predicate so the boundary rule is explicit.
3. Replace the 86.4 s pin in PersistentApiCacheTest with records placed either side of the 24 h boundary.
4. Update any comment or doc that described the 86.4 s window; run JVM tests and the instrumented PersistentApiCacheTest.
<!-- SECTION:PLAN:END -->
