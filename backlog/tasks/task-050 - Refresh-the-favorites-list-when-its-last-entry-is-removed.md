---
id: TASK-050
title: Refresh the favorites list when its last entry is removed
status: To Do
assignee: []
created_date: '2026-09-19 20:06'
labels: []
dependencies: []
type: bug
ordinal: 65000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService.maybeNotifyRootChanged picks which node to notify from mCurrentParentId, the parent the service last answered a browse for, and it only names MEDIA_ID_FAVORITES_LIST when the store is still non-empty afterwards. Unmarking one of several favorites while standing in the favorites list therefore refreshes it and the row leaves, but unmarking the last one notifies the root instead, so the emptied list stays on screen showing the station that is no longer stored. The row is left with its box unchecked because handleFavorite rewrites the cached media item's metadata in place, which is why it looks like a state that was meant rather than a list that was not refreshed.

mCurrentParentId is service-global and shared by every connected controller, so the phone UI, Android Auto and a test browser all move it. Whether that is the right source for this decision is part of the fix.

Found while writing the favorite lifecycle journey (TASK-006.03), which asserts the stale row so the behaviour is pinned rather than described.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Unmarking the last favorite from inside the favorites list leaves a list that matches the store
- [ ] #2 Unmarking one of several favorites still refreshes the list, and the root still gains and loses the node as the store fills and empties
- [ ] #3 The node that is notified does not depend on which controller browsed last
- [ ] #4 Covered by a test that reaches no network
- [ ] #5 TASK-006.03's unmarkingAStationFromTheFavoritesNodeTakesItOffTheRoot asserts the refreshed list instead of the stale one
<!-- AC:END -->
