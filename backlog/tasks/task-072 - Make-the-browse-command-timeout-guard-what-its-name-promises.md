---
id: TASK-072
title: Make the browse command timeout guard what its name promises
status: To Do
assignee: []
created_date: '2026-09-22 04:44'
updated_date: '2026-09-22 04:44'
labels: []
milestone: m-0
dependencies: []
references:
  - >-
    common/src/main/java/com/yuriy/openradio/shared/model/media/item/MediaItemChildCategories.kt
  - >-
    common/src/main/java/com/yuriy/openradio/shared/model/media/item/MediaItemCommand.kt
  - common/src/main/java/com/yuriy/openradio/shared/service/OpenRadioService.kt
type: bug
ordinal: 86000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Every browse command wraps its work in withTimeoutOrNull(MediaItemCommand.CMD_TIMEOUT_MS) and falls back to resultListener.onResult(), which reads as a five second cap on a browse. It is neither of those things, because coroutine cancellation is cooperative and not one of those blocks ever suspends: getAllCategories, getAllCountries, getStationsInCategory, getStationsByCountry, getNewStations, getPopularStations and getSearchStations are plain blocking calls, and so are handleDataLoaded, deliverResult and onResult.

Two consequences. A provider that blocks forever, an OkHttp read with no timeout or a DNS lookup that never answers, is never interrupted, the elvis branch never runs, and the browse future OpenRadioService.callWhenSourceReady created is never completed: the phone list and an Android Auto head unit both wait forever, which is the failure TASK-029 set out to make impossible. And when the block does return just after the deadline, the job is already cancelling, so the returned value loses to the cancel cause, withTimeoutOrNull answers null and the fallback sends a second, empty onResult on top of the real one the block already delivered, leaving the client's last word an empty node. That second path affects MediaItemChildCategories, MediaItemCountryStations, MediaItemSearchFromService, MediaItemNewStations and MediaItemPopularStations.

The fallback branch has no test coverage today: the test fakes answer in microseconds, so no test has ever executed it. Found while reviewing TASK-069.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A browse command whose provider never returns still completes the browse request, rather than leaving the future unset
- [ ] #2 A browse command delivers at most one result per execution, so a slow provider cannot have its real answer overwritten by the empty fallback
- [ ] #3 Both behaviours are covered by tests that fail when the guard is removed
<!-- AC:END -->
