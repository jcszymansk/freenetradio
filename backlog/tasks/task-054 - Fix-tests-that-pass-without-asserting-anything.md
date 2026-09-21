---
id: TASK-054
title: Fix tests that pass without asserting anything
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 17:45'
updated_date: '2026-09-21 20:37'
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
1. BrowseListView.assertRowsStay stops skipping its assertion on an empty read. Counting the comparisons it made is not enough on its own: a list that shows the expected rows and then empties banks comparisons first, and the reads after it emptied would still be skipped. rows() is what conflates the two states, answering empty both for a list the service emptied (adapter.itemCount 0) and for one whose rows are not all laid out (displayed.size != itemCount), and the adapter already separates them. So an empty read with an empty adapter fails on the spot, one with a filled adapter is still skipped as mid-layout, and a window of nothing but skipped reads fails at the end, leading with the diagnosis rather than with the caller's reason, which in that case is not known to be true. A private adapterItemCount with a NO_ADAPTER sentinel keeps 'no browse list at all' from reading as 'empty list'.
2. Update the FavoriteLifecycleJourneyTest comment that documented the hole assertRowsStay used to have. The awaitRowFavorite check under it stays, for the claim it makes in its own right: a BrowseRow carries the media id and the title, so a rebind that restored the row's old favorite state would leave the comparison above equal.
3. MediaItemCarRootTest.carEntriesAreBrowsableFolders asserts listener.items.size is 3 before the loop. The count rather than assertMediaIds, because pinning the ids would restate carRootKeepsTheTopLevelToThreeEntries's whole claim, where this loop needs only to have walked all three entries.
4. The five empty-result media item tests assert the presenter counter or request list that separates an empty catalogue from a command that never reached the presenter: categoryRequests, countryRequests, searchRequests, favoritesRequests, deviceLocalsRequests. The country test asserts countryRequests only; countriesRequests is the warm-up call and is already owned by theCountryIsWarmedUpBeforeItsFirstPageIsRequested, while countryRequests is the request that produces the result under test. Messages state what each assertion pins rather than naming the timeout, because the recording presenter writes on entry: these catch a command that never asked, not one that asked and then hung.
5. Delete MediaIDHelperTest.testStartsWithAndEquals.
6. Check every criterion by mutation rather than by inspection: break the thing each assertion claims to watch, confirm the test fails, restore, and confirm no production source is modified. Then run the full JVM suite, compile the instrumentation tests, and run the instrumented suite on a device with networking disabled.
<!-- SECTION:PLAN:END -->
