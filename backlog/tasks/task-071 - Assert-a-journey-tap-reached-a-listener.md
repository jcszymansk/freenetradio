---
id: TASK-071
title: Assert a journey tap reached a listener
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 20:32'
updated_date: '2026-09-24 09:20'
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
- [x] #1 A tap that no listener consumed fails the journey that made it, separately from a row that was never rendered
- [x] #2 NowPlayingView.assertStaysDown is deleted
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Delete NowPlayingView.assertStaysDown and its SETTLE_SECONDS constant.
2. In BrowseListView.clickInRow, keep the no-row assertion and add one that performClick returned true; for the row foreground and settings button also assert the adapter holds its MediaItemsAdapter.Listener, since OnItemTapListener and OnSettingsListener forward through listener?.
3. Apply the performClick check to NowPlayingView.tap and tapFavorite; the bar favorite listener is attached only under getCurrentMediaItem()?.let.
4. Verify by removing each listener in production and running the three offline gate cases, then run the full instrumented suite.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Audit of every androidTest performClick site: all row, bar and dialog targets use OnClickListener (the RS settings buttons through android:onClick). NetworkDialog use_mobile_network_check_box and GeneralSettingsDialog user_agent_check_view carry only OnCheckedChangeListener, so performClick returns false there by design; SettingsPersistenceJourneyTest is left unchanged. Dialog button taps in LocalStationLifecycleJourneyTest are followed by waits for a positive outcome, so a dead tap there already fails and they are left unchanged.
Sabotage runs (reverted): removing the foreground setOnClickListener in MobileMediaItemsAdapter failed all three offline gate cases with 'carries no click listener'; removing mAdapter?.listener assignment in MediaPresenterImpl failed all three with 'the adapter holds none'.
Full :app:connectedDebugAndroidTest: 216 tests, BUILD SUCCESSFUL, networking disabled, app data cleared.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
BrowseListView.clickInRow now fails when performClick finds no click listener, separately from the no-rendered-row failure, and for row and settings taps also when the adapter holds no MediaItemsAdapter.Listener. NowPlayingView.tap and tapFavorite get the same performClick check. NowPlayingView.assertStaysDown is deleted. Verified by removing the row's setOnClickListener, then the adapter listener assignment: both made all three offline gate cases fail with the new messages. The full connectedDebugAndroidTest run passed (216 tests), and the codex review passed.
<!-- SECTION:FINAL_SUMMARY:END -->
