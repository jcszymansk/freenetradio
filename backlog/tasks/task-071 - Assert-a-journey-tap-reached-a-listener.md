---
id: TASK-071
title: Assert a journey tap reached a listener
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 20:32'
updated_date: '2026-09-24 09:09'
labels:
  - test
milestone: m-0
dependencies: []
ordinal: 85000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
BrowseListView.clickInRow reads View.performClick() through inRow, so its type argument is Boolean and the assertNotNull that follows is satisfied by false. performClick returns false when no listener is attached, and MobileMediaItemsAdapter attaches the row listener through two safe calls, so an adapter that stopped attaching it makes every tap a no-op that the helper reports as delivered. The three offline gate cases turn on this: FavoriteLifecycleJourneyTest.theFavoritesRowDoesNotOpenWithoutANetwork, LocalStationLifecycleJourneyTest.theLocalsRowDoesNotOpenWithoutANetwork and the equivalent in OfflinePlaybackJourneyTest all tap a row and then assert the list did not change. A tap that never reached a listener produces exactly that, so the gate under test is never exercised and the case reports it working.

The message the assertion carries, about no rendered row carrying the media id, is accurate for what it does check, so what is missing is the second half rather than a wrong claim.

NowPlayingView.assertStaysDown has no callers anywhere in app, common or common-ui. It asserts unconditionally, so it is not part of the same defect, but it is dead and belongs with this cleanup. Found while reviewing TASK-054.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A tap that no listener consumed fails the journey that made it, separately from a row that was never rendered
- [ ] #2 NowPlayingView.assertStaysDown is deleted
<!-- AC:END -->
