---
id: TASK-073
title: Close the browse tests that a command skipping the provider would still pass
status: To Do
assignee: []
created_date: '2026-09-22 04:44'
labels:
  - test
milestone: m-0
dependencies: []
references:
  - >-
    common/src/test/java/com/yuriy/openradio/shared/model/media/item/MediaItemChartsTest.kt
  - >-
    common/src/test/java/com/yuriy/openradio/shared/model/media/item/MediaItemCommandTestSupport.kt
type: chore
ordinal: 87000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-054 and TASK-069 closed the browse tests whose whole claim was that nothing arrived. A weaker shape of the same defect is left: five empty-node tests rest entirely on the error event, and none of them asserts that the command reached the presenter at all, so a command that skipped the provider and went straight to reportNoData satisfies every one. They are anEmptyCatalogueIsDeliveredAndReportedAsAnError in MediaItemAllCategoriesTest, the empty-list test in MediaItemCountriesListTest, anEmptyPopularChartIsReportedAsAnError and anEmptyNewStationsChartIsReportedAsAnError in MediaItemChartsTest, and reachingTheEndOfTheSecondPageReportsThatNothingMoreArrived in MediaItemChildCategoriesTest, which never checks that the category was asked for a second page. Their siblings in the same files do assert the request list and say so in the failure message.

Two restored-instance branches are also unwatched. MediaItemNewStations answers a restored instance from the cache like the other six commands and has no test for it. MediaItemLocalsList deliberately reloads on a restored instance, the way MediaItemFavoritesList does, and only the favorites version is pinned by aSavedInstanceStillReloadsTheFavorites, so the locals list could silently acquire an early return without a test noticing.

Note that assertEquals(UrlLayer.FIRST_PAGE_INDEX, listener.pageNumber) proves nothing on its own: FIRST_PAGE_INDEX is zero and zero is the default argument of ResultListener.onResult, so any empty delivery satisfies it. Found while reviewing TASK-069.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each of the five empty-node tests asserts what the command asked the presenter for, not only the error it reported
- [ ] #2 The restored-instance branch of MediaItemNewStations is covered the way the other six commands are
- [ ] #3 MediaItemLocalsList has a test that fails if it starts answering a restored instance from the cache
<!-- AC:END -->
