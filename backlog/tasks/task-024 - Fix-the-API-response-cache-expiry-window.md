---
id: TASK-024
title: Fix the API response cache expiry window
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 19:27'
updated_date: '2026-10-08 19:56'
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
- [x] #1 The freshness window is 24 hours of wall-clock time
- [x] #2 The unit of the constant is unambiguous in its name and its use
- [x] #3 PersistentApiCacheTest asserts the 24 hour boundary instead of the millisecond one
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace SEC_IN_DAY with a millisecond constant derived from TimeUnit.DAYS, named for its unit, and use it in the freshness check.
2. Move the freshness comparison into a small pure predicate so the boundary rule is explicit.
3. Replace the 86.4 s pin in PersistentApiCacheTest with records placed either side of the 24 h boundary.
4. Update any comment or doc that described the 86.4 s window; run JVM tests and the instrumented PersistentApiCacheTest.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The rule moved into ApiCacheFreshness (pure, JVM tested to the millisecond, listed in gradle/pure-core-coverage.tsv) because PersistentApiCache is excluded from the pure-core gate. A record dated after the read now counts as stale: a wall clock moved backwards would otherwise keep it fresh until the clock caught up. The fix makes doc/project-overview.md, POLICY, and the cache_desc_text string, which already promised 24 hours, true.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
PersistentApiCache now treats a response as fresh for TimeUnit.DAYS.toMillis(1) via ApiCacheFreshness.isFresh, replacing SEC_IN_DAY (86400 compared against milliseconds). Verified with ApiCacheFreshnessTest (exact 24 h boundary on a fixed clock), PersistentApiCacheTest on an offline emulator (records a minute either side of 24 h, a one hour old record that the old code dropped), the browse, search and MediaResourcesManager instrumented suites, ./gradlew test verifyPureCoreCoverage verifyPureCoreAttribution, and a codex review that passed.
<!-- SECTION:FINAL_SUMMARY:END -->
