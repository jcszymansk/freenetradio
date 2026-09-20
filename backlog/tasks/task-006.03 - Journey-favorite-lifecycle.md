---
id: TASK-006.03
title: 'Journey: favorite lifecycle'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 20:18'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Favorites are stored in SharedPreferences and invalidate a browse node, so the UI and storage must agree.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Seed or browse a local station
- [x] #2 Add and remove the favorite
- [x] #3 Favorites node and persisted state verified
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the navigation half that LocalStationLifecycleJourneyTest grew (reaching the registry's MediaPresenter, opening a node the offline gate refuses to open by tap, and walking back out without hitting the root's kill-the-process back press) into a JourneyNavigation helper in the journey package, and repoint that test at it, so the two journeys navigate the same way and there is one copy.
2. Teach BrowseListView the favorite control: tap a row's favorite checkbox and read whether the rendered checkbox is checked. That is the gesture this journey is about, and it has to be read off the row rather than off storage.
3. Add favoritesRow() to JourneyProfile, next to localsRow(). MediaItemRoot puts favorites first, so the expected root is favoritesRow + cleanInstallRoot + localsRow.
4. Add FavoriteLifecycleJourneyTest under app/src/androidTest/.../mobile/journey. Its @Before starts from the cold launch preconditions: offline, cleared stores, Radio Browser bound. The station the user favorites is seeded through the registry's own locals storage, which AC1 allows, because creating a station is TASK-006.02's subject and the dialog, the validator and the loopback stream server are not needed to have something to mark.
5. AC1 and AC2: open the locals list, assert the station's rendered row offers an unchecked favorite box, click it, and assert the favorite reached a favorites storage the test built and a preference file on disk. Then click it again and assert both go away. What the service does with CMD_FAVORITE_ON/OFF is OpenRadioServiceCommandTest's subject; what is asserted here is the checkbox, its listener and what the user sees.
6. AC3: the root grows the Favorites row in first place when the favorite is stored and loses it when it is not, the favorites node renders exactly the marked station with its box checked, and a relaunched Activity rebuilt from the store shows both. Persisted state is read back through a storage instance the test constructed, so nothing the writer cached can answer for it.
7. Assert the offline gate for the favorites row as well, the way TASK-006.02 does for locals: tapping it leaves the list where it was. TASK-049 criterion 2 holds the fix.
8. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Seeded the station rather than adding it through the dialog. AC1 allows either, and creating a station is TASK-006.02's subject; seeding keeps the loopback stream server, the validator probe and the image permission grant out of a journey whose subject is a check box.

The favorite gesture is a CheckBox in the layer a left swipe reveals. MobileMediaItemsAdapter enables the drag in exactly the favorites and locals nodes, which are the two this journey visits, so unlike TASK-006.02's settings button the control is genuinely reachable by a finger here; only the drag itself is skipped.

Confirmed by experiment that the journey is not vacuous: with the tapRowFavorite call removed all five cases fail.

Found a second bug from where TASK-049 sits: OpenRadioService.maybeNotifyRootChanged names the favorites node only while the store is still non-empty, so unmarking the last favorite from inside that list notifies the root and leaves the emptied list on screen. TASK-050 holds it, created in this commit.

Confirmed it is a real fix rather than a guess: with maybeNotifyRootChanged changed to notify both nodes, unmarkingAStationFromTheFavoritesNodeTakesItOffTheRoot fails with the row gone and the adapter holding nothing, and the other four still pass. Reverted.

That experiment also exposed a hole in the assertion. BrowseListView.assertRowsStay skips empty reads because an empty read is also how a list reads mid-layout, so a list that emptied passes it. The case now also asserts the row is still there in its own right, which is the assertion the experiment failed on.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Adds FavoriteLifecycleJourneyTest, the third of the six Phase 6 phone journeys, plus the two helpers it shares with the second one: JourneyNavigation, which opens a browse node and walks back out, and AppDataReset.awaitPreferenceFileContaining, which waits for a value to reach a named store on disk.

Five cases, each starting from a cleared, offline profile holding one station of the user's own, seeded into the locals store. AC1 allows seeding, and creating a station is TASK-006.02's subject; offline it is also the only kind of station there is, since every other node needs a provider.

AC1 and AC2, mark and unmark: the mark is made by clicking the favorite box on the station's row in the locals list and taken away by clicking its box on the row in the favorites list, which is where a user makes and unmakes it. Both are the real control: MobileMediaItemsAdapter binds the box only on playable rows and enables the swipe that reveals it in exactly those two nodes, so only the drag is skipped. Nothing between the click and the lists changing is done by the test. The box is read before each click, so a station that arrived already marked fails rather than passing the case it sets up, and the root is asserted to be without the favorites node first, which is what makes waiting for it mean something.

AC3, the node and persisted state: the favorites node renders exactly the marked station with its box checked; the root gains the node in first place and loses it again; the mark is read back through a FavoritesStorage the test built, because the writer answers isFavorite out of a map it fills as it goes; it is asserted to have reached FavoritesPreferences on disk rather than only the copy Android keeps in memory; and a relaunched Activity, with the service's cached root dropped, offers the node and the station inside it. A real process restart is not asserted and cannot be from here, which is TASK-031.

Verified on emulator-5554, a clean API 34 AVD, with wifi and mobile data disabled and the app's data cleared: the full instrumented suite passes at 191 tests, up from 186, and ./gradlew test --rerun-tasks passes.

Non-vacuous by mutation: with the tapRowFavorite call removed all five cases fail. Changing maybeNotifyRootChanged to notify the favorites node as well makes unmarkingAStationFromTheFavoritesNodeTakesItOffTheRoot fail with the row gone, which is how TASK-050 was confirmed rather than guessed, and that same experiment exposed a hole in BrowseListView.assertRowsStay that the case now covers with an assertion of its own.
<!-- SECTION:FINAL_SUMMARY:END -->
