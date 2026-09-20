---
id: TASK-049
title: Let locally served browse nodes open without a network
status: To Do
assignee:
  - '@claude'
created_date: '2026-09-19 18:32'
updated_date: '2026-09-20 05:48'
labels: []
dependencies: []
type: bug
ordinal: 64000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaPresenterImpl.handleItemSelected returns early unless NetworkLayer.checkConnectivityAndNotify reports a connection, and it is the only tap driven way into a browsable node. Favorites and local stations are built from SharedPreferences and need no network, so with networking disabled the phone shows the Locals and Favorites rows on the root and refuses to open either, raising the no-connection toast instead. A user who added a station by hand cannot reach it, edit it or remove it while offline, and the swipe-revealed settings button lives on a row in that list, so the whole edit and remove path is unreachable too.

Found while writing the local station lifecycle journey (TASK-006.02), which the testing roadmap calls the backbone of the offline suite. That journey asserts the gate as it stands and reaches the edit and remove dialogs through MediaPresenter rather than through the list, because offline there is no row to swipe. Fixing this is what lets the journey drive the real path.

The same gate is what keeps a cached provider node from opening offline after TASK-030 made the cache servable without a network, so whether it should apply there is part of the decision.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Opening the locals node offline shows the stored stations rather than the no-connection toast
- [ ] #2 Opening the favorites node offline shows the stored stations
- [ ] #3 A node that can only be answered from the network still tells the user there is no connection
- [ ] #4 The decision covers nodes the API response cache can serve offline, either by opening them or by recording why not
- [ ] #5 Covered by a test that reaches no network
- [ ] #6 TASK-006.02's journey opens the locals node by tapping its row rather than by calling addMediaItemToStack, and theLocalsRowDoesNotOpenWithoutANetwork is replaced by the opposite assertion
- [ ] #7 TASK-006.03's journey opens the favorites node by tapping its row rather than by calling addMediaItemToStack, and theFavoritesRowDoesNotOpenWithoutANetwork is replaced by the opposite assertion
- [ ] #8 The decision covers a playable row as well as a browsable one, so a station whose stream is on the device can be started offline, and TASK-006.04's theStationRowStartsNothingWithoutANetwork asserts the tap starting it instead of the refusal
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
TASK-006.04 found the gate refuses a playable row too, not only a browsable one: a station stored on the device cannot be started offline, which is criterion 8. Confirmed by experiment rather than by reading - making that journey's theStationRowStartsNothingWithoutANetwork select the station the way a tap would past the gate fails it with the station playing, and nothing else in the class changes answer.
<!-- SECTION:NOTES:END -->
