---
id: TASK-006.02
title: 'Journey: local station lifecycle'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 18:32'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The local-station path is the only fully offline content source, so it is the backbone of the offline suite.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Add a local station through the dialog
- [ ] #2 Locals appears immediately
- [ ] #3 Edit and remove the station
- [ ] #4 Restart and verify persistence
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the browse-list reading ColdLaunchJourneyTest grew (rows by adapter position, paired with the text each row's own view shows) into a BrowseListView helper in the journey package, and repoint that test at it, so the two journeys read the list the same way and there is one copy.
2. Add LocalStationLifecycleJourneyTest under app/src/androidTest/.../mobile/journey. Its @Before starts from the cold launch preconditions: offline, cleared stores, Radio Browser bound. It also grants READ_MEDIA_IMAGES through UiAutomation, so the add and edit dialogs never raise the image-picker permission request over the Activity.
3. AC1 and AC2 in one case: click the real add_station_btn, let MainActivity show the real AddStationDialog, type a name and a stream url served by LoopbackHttpFixture, and click Add. The real validator, RadioStationManagerLayerImpl and DeviceLocalsStorage run. The url is loopback because the validator probes the stream over HTTP and loopback is the one address reachable with networking disabled. Then assert the rendered root list grows the Locals row with no further user action, which is the CMD_UPDATE_TREE the dialog sends arriving as a subscription push.
4. AC3: drive the real RSSettingsDialog for the station's browsed MediaItem, assert it offers edit and remove only because the parent is the locals node, and click through to the real EditStationDialog and RemoveStationDialog. Assert the edit lands in storage and in what the service serves, and that removing the last local station takes the Locals row off the rendered root.
5. AC4: assert the station is in the on-disk preference file, not only in memory, and that a freshly launched Activity and a storage instance built from disk both still show it. A process restart is not available in process, which is TASK-031, so say what this does and does not prove.
6. Assert the offline gate found on the way: with the Locals row on screen, tapping it leaves the list where it was. TASK-049 holds the fix.
7. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data.
<!-- SECTION:PLAN:END -->
