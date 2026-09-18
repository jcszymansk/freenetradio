---
id: TASK-027
title: Drop the cached latest station when its storage is cleared
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 19:45'
updated_date: '2026-09-18 06:14'
labels: []
dependencies: []
type: bug
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
LatestRadioStationStorage keeps the last station in an mRadioStation field and AbstractStorage.clear() only wipes the preference file, so the field survives. OpenRadioServicePresenterImpl.clear(), which the CMD_CLEAR_CACHE session command reaches through OpenRadioService.handleClearCache, calls exactly that clear(), and the registry hands out one LatestRadioStationStorage per process. After the user clears the cache, getLastRadioStation() therefore still returns the station that was supposedly removed, and the app can autoplay it on the next start. Found while writing the Phase 3 persistence tests (TASK-003); LatestRadioStationStorageTest currently pins the stale read and has to be updated together with the fix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Reading the latest station from the same instance after clear() reports the invalid instance
- [x] #2 Clearing the cache through the service leaves no station to autoplay on the next start
- [x] #3 LatestRadioStationStorageTest asserts the cleared value instead of the stale cached one
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AbstractStorage.clear is now open and LatestRadioStationStorage overrides it to drop the cached station before wiping the file.

Fixed inside TASK-004.02 rather than on its own branch. The sixth review round of that task named the cached station as app-owned state its clear-data case provably failed to reset, and the branch had already paid for the defect once: a probe station seeded through the service's own storage survived a clear, the next service start adopted it as the active station, and a later favorite command returned success instead of not supported. A clear that leaves the cache it owns behind is wrong on its own terms, so it is a one-line correctness fix rather than a test accommodation.

LatestRadioStationStorageTest.clearCurrentlyLeavesTheCachedStationReadableOnTheSameInstance pinned the old behavior and is now clearDropsTheCachedStationOnTheSameInstance, asserting the station is gone. Confirmed live by reverting the override, which fails that test.

OpenRadioServiceBrowseTest.reconnectsOnAnEmptyProfileAfterEveryStoreIsCleared also asserts the service's own storage reports no latest station after CMD_CLEAR_CACHE, but that is a guard on the service's view rather than the regression test for this defect; the storage test is.

Criteria 2 and 3 were left unchecked when this was closed, which the TASK-004.02 round 7 review caught. Both hold and the evidence is above; only the checkboxes were missing.

Criterion 2 is covered twice: OpenRadioServiceBrowseTest.reconnectsOnAnEmptyProfileAfterEveryStoreIsCleared asserts the service's own storage reports no latest station after CMD_CLEAR_CACHE, over a real MediaBrowser connection, and OpenRadioServicePresenterImplTest.clearingDropsEveryCacheAndTheLatestStation asserts the same through the presenter that the command reaches. Criterion 3 is LatestRadioStationStorageTest.clearDropsTheCachedStationOnTheSameInstance, which replaced the test that pinned the stale value and fails again if the override is removed.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LatestRadioStationStorage.clear now drops its cached station along with the stored one, so a clear is no longer invisible to the single instance the registry hands out. AbstractStorage.clear became open to allow the override. Verified by LatestRadioStationStorageTest.clearDropsTheCachedStationOnTheSameInstance, which was the test pinning the old behavior and which fails again if the override is removed; the full instrumented suite passes.
<!-- SECTION:FINAL_SUMMARY:END -->
