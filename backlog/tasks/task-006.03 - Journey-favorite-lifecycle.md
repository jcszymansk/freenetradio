---
id: TASK-006.03
title: 'Journey: favorite lifecycle'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 19:59'
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
- [ ] #1 Seed or browse a local station
- [ ] #2 Add and remove the favorite
- [ ] #3 Favorites node and persisted state verified
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
