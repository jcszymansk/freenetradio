---
id: TASK-070
title: Pin the entries the phone root test walks
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 20:32'
updated_date: '2026-09-24 17:39'
labels:
  - test
milestone: m-0
dependencies: []
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemRootTest.everyRootEntryIsBrowsableAndCarriesAnIcon is the shape TASK-054 fixed in MediaItemCarRootTest, in the same directory, and was not in that task's list. listener.awaitResult() pins nothing, and two loops over listener.items carry every assertion about browsability, media type, icon and title. A regression that dropped entries from the phone root leaves both loops satisfied by iterating over what is left: the second loop filters out the country entry, so it can run zero times and still pass. The single { } for the country entry is the only thing keeping a wholly empty result from passing, which makes the test fail for the wrong reason rather than assert the root it is named for.

The phone root is built by MediaItemRoot and its expected entries are already spelled out by the other cases in the file. Found while reviewing TASK-054.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 everyRootEntryIsBrowsableAndCarriesAnIcon asserts which entries it walked before walking them
- [x] #2 The test fails when the root comes back short, not only when it comes back empty
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Pin the full phone root with assertMediaIds (favorites, new, popular, categories, countries, country, locals) right after awaitResult, before the loops walk listener.items.
2. Keep the loops as the per-entry checks of browsability, type, icon and title.
3. Prove it by mutation: drop entries from MediaItemRoot, confirm the new test fails and the old one passed.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Mutation check on MediaItemRoot, one entry dropped at a time: without the local-stations entry the old test passed and the new one fails; without new-stations or favorites the new one fails too, each on the assertMediaIds list. Full JVM suite (./gradlew test) passes.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
everyRootEntryIsBrowsableAndCarriesAnIcon now pins the full ordered phone root (favorites, new, popular, categories, countries, country, locals) with assertMediaIds before the loops walk it, so a short root fails on the list instead of shrinking the loops. Verified by dropping entries from MediaItemRoot: the old test missed a missing local-stations entry, the new one fails for all three removals. ./gradlew test green.
<!-- SECTION:FINAL_SUMMARY:END -->
