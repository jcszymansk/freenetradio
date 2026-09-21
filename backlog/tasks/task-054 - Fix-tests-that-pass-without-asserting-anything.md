---
id: TASK-054
title: Fix tests that pass without asserting anything
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 17:45'
updated_date: '2026-09-21 20:11'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 69000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Four shapes go green without evaluating their claim, which is what criterion 8 of TASK-007 rules out. BrowseListView.assertRowsStay skips its assertEquals whenever rows() reads empty, and rows() reads empty both when the list is genuinely empty and when not every row is laid out; at FavoriteLifecycleJourneyTest line 295 it is the only assertion carrying the test, and LocalStationLifecycleJourneyTest line 324 is partly rescued by a separate check. MediaItemCarRootTest.carEntriesAreBrowsableFolders puts every assertion inside a loop over listener.items with nothing pinning the count, so zero items is a pass. Five media item tests assert exactly the state a timed-out command delivers, namely empty items, page 0 and no error, while AWAIT_SECONDS of 10 outlasts CMD_TIMEOUT_MS of 5000: MediaItemChildCategoriesTest, MediaItemCountryStationsTest, MediaItemSearchTest, MediaItemFavoritesListTest and MediaItemLocalsListTest. A hung command or one that never reaches the presenter is indistinguishable from an empty catalogue, and the recording presenter already exposes the request counter. MediaIDHelperTest.testStartsWithAndEquals compares MEDIA_ID_COUNTRIES_LIST with itself and asserts that Kotlin startsWith and == are reflexive. Found while auditing criterion 8 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 assertRowsStay fails when the list reads empty instead of skipping its assertion
- [ ] #2 carEntriesAreBrowsableFolders asserts how many items it expects
- [ ] #3 The five media item tests that accept an empty result also assert the presenter was asked
- [ ] #4 testStartsWithAndEquals is removed
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. BrowseListView.assertRowsStay counts the reads it actually compared and fails at the end when none of them was, naming what the list held instead. An empty read stays skipped, because rows() cannot tell an emptied list from one mid-layout, but a window of nothing but empty reads is now a failure rather than a pass.
2. Update the FavoriteLifecycleJourneyTest comment that documents the hole assertRowsStay used to have, keeping the extra awaitRowFavorite check for the claim it makes in its own right.
3. MediaItemCarRootTest.carEntriesAreBrowsableFolders pins the three entries the loop is meant to walk with assertMediaIds before looping over them.
4. The five empty-result media item tests assert the presenter counter or request list that separates an empty catalogue from a command that timed out or never reached the presenter: categoryRequests, countryRequests plus countriesRequests, searchRequests, favoritesRequests, deviceLocalsRequests.
5. Delete MediaIDHelperTest.testStartsWithAndEquals.
6. Run the full JVM suite; compile the instrumentation tests; run the instrumented journey suites if a device is reachable.
<!-- SECTION:PLAN:END -->
