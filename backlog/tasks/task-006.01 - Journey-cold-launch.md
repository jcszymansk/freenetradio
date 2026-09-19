---
id: TASK-006.01
title: 'Journey: cold launch'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 17:07'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
First-run behavior with no network and no stored state.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clear application data
- [ ] #2 Launch MainActivity
- [ ] #3 Root list loads without network
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the in-process clear-data recipe out of OpenRadioServiceBrowseTest into an internal AppDataReset helper in the androidTest service package: empty every preference file the app owns, then send CMD_CLEAR_CACHE and wait for a caller supplied completion signal. Repoint the browse test at it so there is one copy.
2. Add ColdLaunchJourneyTest under app/src/androidTest/.../mobile/journey. Its @Before resets the profile with AppDataReset plus ServiceStorages.clear, refreshes the browse tree and asserts the device is really offline, so 'loads without network' is enforced rather than documented.
3. Journey test: seed a favorite, a local station and a last played station the way earlier use would leave them, confirm the service's root offers Favorites and Locals, clear the application data, then launch MainActivity with ActivityScenario and assert the rendered rows are exactly New, Popular, Categories, Countries and Canada.
4. Second test: on the cleared profile assert the list loads with the progress bar and the no-data message both hidden and the add-station button shown, which is the root node's own UI state.
5. Assertions read the RecyclerView children by adapter position, so what is asserted is what a user sees, and the adapter's media ids for identity.
6. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled.
<!-- SECTION:PLAN:END -->
