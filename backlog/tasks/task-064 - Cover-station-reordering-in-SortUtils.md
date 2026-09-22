---
id: TASK-064
title: Cover station reordering in SortUtils
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 19:11'
updated_date: '2026-09-22 08:49'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 79000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SortUtils has no test anywhere: 19 lines and 10 branches at 0% coverage, reached from one production caller, OpenRadioServicePresenterImpl.updateSortIds. It is what renumbers favorites and local stations when the user drags a row, so a defect reorders or collides the list the user arranged by hand, and the only thing that would notice is the user.

The renumbering rule in the private resortIds is not obvious and should be established rather than assumed. Walking the set, the dragged station takes the requested sortId, and every other station takes a running counter that is incremented twice when it already holds the requested sortId and once otherwise. Whether the double increment is deliberate room-making or an off-by-one is exactly what a test has to settle; if it turns out to be wrong, the fix belongs in its own task with this one pinning the behaviour first.

It takes the concrete FavoritesStorage and DeviceLocalsStorage, so a JVM test needs the preferences fake that common/src/test already has in InMemoryPreferences, as OpenRadioServicePresenterImplTest.preferencesContext does.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Reordering within favorites renumbers only favorites, and within locals only locals
- [x] #2 A category media id that is neither favorites nor locals changes nothing
- [x] #3 The dragged station ends up with the requested sort id
- [x] #4 The renumbering rule for the remaining stations is pinned, including the case where one already holds the requested sort id
- [x] #5 An empty category and a media id that matches no station are covered
- [x] #6 The tests run on the JVM against a preferences fake and reach no device
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read SortUtils.resortIds and everything it depends on: AbstractRadioStationsStorage.getAll renumbers the set to 0..n-1 before resortIds ever sees it, and returns a TreeSet ordered by sortId, so the walk order is fixed.
2. Add common/src/test/java/com/yuriy/openradio/shared/utils/SortUtilsTest.kt driven by the existing preferencesContext() fake and the station() helper, with real FavoritesStorage and DeviceLocalsStorage over it.
3. Read the result back with getAllFromString(getAllAsString()), not getAll(), because getAll renumbers on the way out and would hide the sort ids the rule actually wrote.
4. Pin: an upward drag, a downward drag, the station that already holds the requested sort id, a media id that matches nothing, an empty category, a foreign category, and the fact that a sparse set is compacted first.
5. Record whatever the double increment turns out to do; if it is a defect, open a follow up task rather than changing SortUtils here.
6. Run ./gradlew :common:testDebugUnitTest and the full ./gradlew test.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Established the rule in two halves. AbstractRadioStationsStorage.getAll sorts the stored stations by sort id and renumbers them 0..n-1 before resortIds is handed them, so the set the rule walks is always contiguous, ascending and numbered from zero whatever the preference file held. Walking it, the dragged station takes the requested sort id and every other station takes a running counter, incremented twice when the station being looked at currently holds the requested sort id and once otherwise.

The double increment is a defect, not room making. It reserves the requested slot only when the station holding it is met after the counter has already reached that value, which is to say only on a drag towards the top of the list. Dragging down (a to 2 of a b c d) gives b=0, a=2, c=2, d=3; dropping a row back on its own position (b to 1) gives a=0, b=1, c=1, d=2. Both leave two stations sharing one sort id, which the next getAll renumbers apart by station name, so the row lands one place from where the user dropped it. TASK-075 carries the fix and SortUtilsTest names it next to the two expectations that will have to change.

Assertions read what was written, through getAllFromString over getAllAsString, because getAll renumbers on the way out and would report a tidy 0..n-1 over a gap or a collision, leaving every interesting assertion vacuous. FavoritesStorage.get would do for favorites, but DeviceLocalsStorage overrides get to go through getAll, so one helper covers both. The categories that must not change are seeded with sort ids no read would ever produce (4, 9, 7), so a renumbering of the untouched collection shows up as a change rather than passing by coincidence.

Verified on a clean build: ./gradlew clean, then ./gradlew test PASS (SortUtilsTest 10 tests, 0 failures, 0 skipped; whole JVM suite green) and ./gradlew :common:createDebugUnitTestCoverageReport PASS, reporting SortUtils at 19/19 lines, 10/10 branches, 80/80 instructions, 2/2 methods, against the 0% the task recorded. Mutation check: replacing item.sortId == sortId with counter == sortId fails exactly the two collision tests and produces the contiguous order in both, which is the evidence behind TASK-075 as much as it is a check that the suite discriminates; SortUtils.kt was restored unchanged.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added common/src/test/java/com/yuriy/openradio/shared/utils/SortUtilsTest.kt, ten JVM tests driving the production FavoritesStorage and DeviceLocalsStorage over the existing preferencesContext in memory fake, taking SortUtils from 0% to 19/19 lines and 10/10 branches. No production code changed.

The renumbering rule is established rather than assumed: getAll compacts the collection to 0..n-1 before resortIds sees it, then the dragged station takes the requested sort id and the rest take a running counter that skips one number at the station currently holding that id. The skip lands in the right place only on a drag towards the top of the list; dragging down and dropping a row back where it was picked up each leave two stations sharing one sort id. The tests state that behaviour as it is and name TASK-075, which carries the fix.

Verified on a clean build with ./gradlew test (SortUtilsTest 10 tests, 0 failures, 0 skipped, whole JVM suite green) and ./gradlew :common:createDebugUnitTestCoverageReport. Replacing the condition with counter == sortId fails exactly the two collision tests and yields the contiguous order in both.
<!-- SECTION:FINAL_SUMMARY:END -->
