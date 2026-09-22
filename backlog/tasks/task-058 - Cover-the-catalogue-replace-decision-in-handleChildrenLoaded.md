---
id: TASK-058
title: Cover the catalogue replace decision in handleChildrenLoaded
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 17:46'
updated_date: '2026-09-22 15:04'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 73000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
bcea65a added a replace flag to the children-loaded path so a refreshed node clears its rows instead of appending to them. MediaPresenterImpl.handleChildrenLoaded carries that decision, and nothing asserts it. MediaItemsAdapterTest looks like the regression test and is not: MediaItemsAdapter.updateData(value, replace) did not exist when the fix landed, it was extracted later in 8210ea5 to make the adapter unit-testable, and it covers a four-line helper, so reverting the presenter clause leaves it green. AppUtils.isSameCatalogue returns false for root, favorites and locals, which are the only nodes the offline journeys ever render, so the journey suite cannot see it either. This was the whole point of bcea65a on the UI side and it is the most serious of the gaps found while auditing criterion 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A JVM test drives handleChildrenLoaded twice for the same non-root node and asserts that replace true clears while replace false appends
- [x] #2 The test fails if the replace clause is removed from MediaPresenterImpl
- [x] #3 The test drives a recording adapter and needs no device
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a minimal production seam: split the list/adapter assignment out of MediaPresenterImpl.init into an internal attachList(listView, adapter) so the children-loaded path can run without an Activity, a MediaResourcesManager or a RecyclerView. The replace clause itself stays in handleChildrenLoaded.
2. Extract the abstract-adapter test double MediaItemsAdapterTest already carries into an internal TestMediaItemsAdapter shared by both common-ui test classes.
3. Add common-ui/src/test/.../presenter/MediaPresenterChildrenLoadedTest with hand-written fakes for NetworkLayer, SleepTimerModel and SourcesLayer, and real LocationStorage/FavoritesStorage over a ContextWrapper(null), following the recording-fake style of MediaItemCommandTestSupport.
4. Cover: repeat load of the same non-root node with replace true clears, with replace false appends, navigation to a different node replaces even with replace false, and an end-of-list page leaves the rows untouched. Assert adapter.parentId too.
5. Prove criterion 2 by deleting 'replace ||' from the presenter and watching the suite go red, then restore it.
6. Run ./gradlew test.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Driving the real handleChildrenLoaded needed three enablers, none of them optional:

1. mAdapter and mListView were assigned only inside MediaPresenterImpl.init, which also builds a MediaResourcesManager, registers a LocalBroadcastManager receiver and needs a FragmentActivity, two Views and a real RecyclerView. Split the two assignments into an internal attachList(listView, adapter); init calls it and nothing else moved. The replace clause stays in handleChildrenLoaded, which is what criterion 2 turns on.
2. handleChildrenLoaded ends in notifyDataSetChanged and updateActiveItem calls notifyItemChanged. Both are final on RecyclerView.Adapter, so a double cannot dodge them, and both walk RecyclerView.AdapterDataObservable, which extends android.database.Observable. AGP's mockable android.jar empties constructor bodies, so mObservers arrives null and the first notification dies inside RecyclerView with an NPE. Added android/database/Observable.java to :android-jvm-stubs, in Java because subclasses read mObservers as a field and a Kotlin property would compile to an accessor. It carries its own ObservableTest.
3. :common-ui had no stubs dependency; added testRuntimeOnly project(':android-jvm-stubs'), which :common already had.

The tests browse MediaId.MEDIA_ID_COUNTRY_STATIONS because AppUtils.isSameCatalogue only reports true for a non-root, non-favorites, non-locals node, which is the one case where replace decides anything.

Mutation evidence, each applied to the restored tree and then reverted:
- 'replace || isSameCatalogue.not()' -> 'isSameCatalogue.not()' fails refreshOfTheSameCatalogueReplacesItsRows
- 'replace || isSameCatalogue.not()' -> 'replace' fails anotherCatalogueReplacesTheRowsOfThePreviousOne
- deleting the PlayerUtils.isEndOfList guard fails endOfListPageLeavesTheLoadedRowsAlone

MediaPresenterImpl stays outside gradle/pure-core-coverage.tsv: rule 5 of the pure-core set excludes UI, and the presenter holds Views. verifyPureCoreCoverage still fails on ASXPlaylistParser at 52.6%, reproduced identically on master at 88cb479.

Open question raised with the user, not acted on: MediaPresenterImpl takes a mFavoritesStorage constructor parameter that nothing in the class reads.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added common-ui/src/test/.../presenter/MediaPresenterChildrenLoadedTest: four JVM tests that drive the real MediaPresenterImpl.handleChildrenLoaded against a recording MediaItemsAdapter, covering a same-node refresh with replace true (rows replaced), a same-node next page with replace false (rows appended), navigation to another node (rows replaced anyway), and an end-of-list page (rows untouched). Three enablers made that reachable: an internal attachList seam split out of MediaPresenterImpl.init, a new android.database.Observable stub in :android-jvm-stubs, and testRuntimeOnly project(':android-jvm-stubs') on :common-ui. Verified with ./gradlew test, assembleDebug, assembleRelease, :app:assembleDebugAndroidTest and localCoverageReport, all passing, plus three mutations of the presenter that each turned a named test red.
<!-- SECTION:FINAL_SUMMARY:END -->
