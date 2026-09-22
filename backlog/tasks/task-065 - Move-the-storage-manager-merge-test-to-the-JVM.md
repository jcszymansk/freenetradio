---
id: TASK-065
title: Move the storage manager merge test to the JVM
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 19:11'
updated_date: '2026-09-22 07:52'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 80000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
StorageManagerLayerImpl imports nothing from Android. It takes two storages and its own work is merge: concatenate two sets, sort by sort id, renumber from zero, and hand the result back. That is the logic behind importing a data file over an existing collection, and getting it wrong silently duplicates or reorders the user favorites.

Its only tests are the twelve in the instrumented StorageManagerLayerTest, which build FavoritesStorage and DeviceLocalsStorage from the instrumentation target context. So the class reads 0% line coverage in the JVM report, which is the measurement criterion 4 of TASK-007 depends on, and the pure-core gate records its owner as NONE. This is the same finding as TASK-061 one module over: the test is at the layer the storages need, not the layer the class needs.

The storages genuinely are instrumented subjects and the coverage they have should not be lost. What belongs on the JVM is the merge rule itself, driven through the preferences fake in common/src/test rather than through a device.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 The merge rule is covered on the JVM: overlap between the two sets, disjoint sets, an empty incoming set and an empty existing set
- [x] #2 Sort ids come out contiguous from zero and in the order the sort ids implied
- [x] #3 StorageManagerLayerImpl clears the per-class line floor in the JVM coverage report
- [x] #4 StorageManagerLayerImpl has an owning test in gradle/pure-core-coverage.tsv that verifyPureCoreAttribution confirms
- [x] #5 The storage-level behaviour the instrumented test covers is still covered somewhere
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add common/src/test/java/com/yuriy/openradio/shared/model/storage/StorageManagerLayerImplTest.kt, driving a real FavoritesStorage/DeviceLocalsStorage pair over the existing preferencesContext fake. The path is what ownerSourceFile in gradle/pure-core-coverage.gradle expects for an owner named StorageManagerLayerImplTest in :common.
2. Cover the merge rule: disjoint sets, overlap with identical content, overlap where the incoming station sorts after the stored ones, overlap where it sorts before, an empty incoming payload, an empty existing store, and input that deserializes to nothing. Both storages, and the independence of one from the other.
3. Assert the sort ids the merge itself writes by reading a station back with AbstractRadioStationsStorage.get, not through getAll, which renumbers on read and would pass even if merge stopped renumbering.
4. Build the incoming payload with a donor storage's getAllAsString rather than restating the private delimiters of AbstractRadioStationsStorage in a second source set, so the test feeds the layer exactly what FileStoreManager writes to a file.
5. Delete app/src/androidTest/.../StorageManagerLayerTest.kt. FavoritesStorage, DeviceLocalsStorage and AbstractRadioStationsStorage keep their own instrumented suites, which is where the storage level behaviour lives.
6. Flip line 57 of gradle/pure-core-coverage.tsv from NONE/- to the new owner and :common.
7. Verify: ./gradlew test, localCoverageReport, verifyPureCoreCoverage, verifyPureCoreAttribution, and :app:assembleDebugAndroidTest to prove the androidTest source set still compiles without the deleted file.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The payload each merge test feeds the layer is built by exporting a donor storage through getAllAsString, not by restating the private delimiters of AbstractRadioStationsStorage in a second source set. That is what FileStoreManager writes to a data file, and it keeps the test from drifting away from the marshalling it depends on. The instrumented AbstractRadioStationsStorageTest still owns the hand written malformed input, because the demarshaller's own rules are the storage's subject, not the layer's.

Sort ids are asserted by reading stations back one at a time through AbstractRadioStationsStorage.get. getAll renumbers from zero on the way out, so an assertion made through it reports contiguous sort ids even when the merge writes none: deleting the renumbering loop from StorageManagerLayerImpl.merge leaves every getAll based assertion green and fails only disjointSetsInterleaveBySortIdAndComeOutNumberedFromZero, which is how that test was checked.

The station builder is the existing one in the :common test source set (MediaItemCommandTestSupport.station) rather than a second copy. It already guarantees the non empty media stream that getAll requires.

verifyPureCoreAttribution still fails overall on RadioStationManagerLayerImplTest and AutoDetectParserTest. Both are pre-existing and named in doc/testing-roadmap.md; this task's owner is line [21/29] of that run.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The merge rule moved to common/src/test/java/com/yuriy/openradio/shared/model/storage/StorageManagerLayerImplTest.kt: 13 JVM tests driving a real FavoritesStorage and DeviceLocalsStorage over the in memory preferences of preferencesContext. They cover overlap between the two sets in both conflict directions, disjoint sets, an empty incoming payload, input that deserializes to nothing, an empty existing store, the local flag surviving a merge, the two collections staying independent, and the export import round trip for each. The instrumented StorageManagerLayerTest is deleted and gradle/pure-core-coverage.tsv line 57 now names the new owner.

Verified with ./gradlew test (whole JVM suite green), ./gradlew localCoverageReport (StorageManagerLayerImpl 24/24 lines and 6/6 branches, up from 0%, so it no longer appears under the 60% per-class floor in verifyPureCoreCoverage), ./gradlew verifyPureCoreAttribution ([21/29] StorageManagerLayerImplTest 1 owned class ok; the run's two remaining failures are the pre-existing RadioStationManagerLayerImplTest and AutoDetectParserTest), ./gradlew :app:assembleDebugAndroidTest, and ./gradlew :app:connectedDebugAndroidTest for the storage package on an offline emulator: 77 tests across 8 classes, all passing, with FileStoreManagerTest still driving StorageManagerLayerImpl over real preferences and a real file.
<!-- SECTION:FINAL_SUMMARY:END -->
