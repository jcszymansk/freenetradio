---
id: TASK-027
title: Drop the cached latest station when its storage is cleared
status: To Do
assignee: []
created_date: '2026-09-17 19:45'
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
- [ ] #1 Reading the latest station from the same instance after clear() reports the invalid instance
- [ ] #2 Clearing the cache through the service leaves no station to autoplay on the next start
- [ ] #3 LatestRadioStationStorageTest asserts the cleared value instead of the stale cached one
<!-- AC:END -->
