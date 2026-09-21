---
id: TASK-054
title: Fix tests that pass without asserting anything
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 17:45'
updated_date: '2026-09-21 20:46'
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
- [x] #1 assertRowsStay fails when the list reads empty instead of skipping its assertion
- [x] #2 carEntriesAreBrowsableFolders asserts how many items it expects
- [x] #3 The five media item tests that accept an empty result also assert the presenter was asked
- [x] #4 testStartsWithAndEquals is removed
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Every criterion was checked by mutation rather than by inspection: break the thing the assertion claims to watch, confirm the test fails, restore, and confirm git diff leaves production source untouched.

CMD_TIMEOUT_MS 5000 -> 0, so every command times out: all five empty-result tests FAILED, each on its own new assertion, which is the state the task described as indistinguishable from an empty catalogue.
MediaItemRootCar's 'if (favorites.isNotEmpty())' -> 'if (false)': carEntriesAreBrowsableFolders FAILED with expected:<3> but was:<2>, where before it would have walked two items and passed.
assertRowsStay's read forced empty with the adapter left alone: all three call sites FAILED on the end-of-window branch.
The list forced to read emptied one second into the five second window, after roughly twenty comparisons had already been banked: all three call sites FAILED on the emptied branch. This is the case a comparison counter alone would have let through, and it is why the fix reads the adapter rather than counting.

Criterion 4 confirmed by the absence of testStartsWithAndEquals from the JaCoCo test report and from a repository-wide grep.

Suites on the final tree: ./gradlew test --rerun-tasks, 195 tests (:common 175, :android-jvm-stubs 17, :common-ui 3), 0 failures, 0 errors, 0 skipped. ./gradlew :app:connectedDebugAndroidTest on the API 34 emulator with wifi and data disabled, 207 tests, 0 failures, 0 errors, 0 skipped, including all 28 journey tests, so all three assertRowsStay call sites ran under the stricter helper.

The review of this work found three assertion-free shapes the task's own list did not name. They are TASK-069, TASK-070 and TASK-071, and TASK-068 now depends on them, because all three are criterion 8 failures. One review finding was checked and rejected: ColdLaunchJourneyTest was reported as passing if the list callback never ran, but it calls awaitRows() first, which throws before those assertions are reached.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Four shapes that went green without evaluating their claim now fail when the thing they watch breaks.

BrowseListView.assertRowsStay skipped its assertEquals on every empty read, so a window in which nothing read compared nothing. Counting comparisons is not enough on its own, because a list that shows the expected rows and then empties banks comparisons first; rows() is what conflates a list the service emptied with one still being laid out, and the adapter separates them, so an empty read with an empty adapter now fails on the spot and one with a filled adapter is still skipped as mid-layout.

carEntriesAreBrowsableFolders asserts its item count before the loop that carries its assertions. The five empty-result media item tests assert the presenter request the recording presenter already counted, which is what separates an empty catalogue from a command that never got there. testStartsWithAndEquals, which asserted that Kotlin startsWith and == are reflexive, is deleted.

No production source was modified. Verified by mutation, four of them, each confirming the intended test now fails and previously would not have; then ./gradlew test --rerun-tasks (195 tests) and ./gradlew :app:connectedDebugAndroidTest (207 tests) both clean, the latter on an API 34 emulator with networking disabled.
<!-- SECTION:FINAL_SUMMARY:END -->
