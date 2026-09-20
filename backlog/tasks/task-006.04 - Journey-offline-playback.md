---
id: TASK-006.04
title: 'Journey: offline playback'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 05:33'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Playback proven end to end without a real stream.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Select a local WAV-backed station
- [ ] #2 Now-playing metadata and controls verified
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a NowPlayingView helper to the journey package: the current_radio_station_view bar the phone shows above the list, read the way a user reads it - whether it is up at all, the title and description text, the favorite box on it, and the tap that is the phone's only transport control (the bar's click listener sends CMD_TOGGLE_LAST_PLAYED_ITEM).
2. Add a JourneyPlayback helper that starts playback the way a tap does once past the offline gate: a real MediaResourcesManager connected to the service, then playFromMediaId with the MediaItem the adapter holds for the row and the node the presenter is standing in. MediaPresenterImpl.handleItemSelected gates playing on connectivity exactly as it gates browsing, so the tap itself cannot be used offline; this is the same shape JourneyNavigation already uses for browsable nodes.
3. Teach BrowseListView to hand back the MediaItem at a row, which is the argument the tap passes on.
4. Add OfflinePlaybackJourneyTest under app/src/androidTest/.../mobile/journey. Its @Before starts from the cold launch preconditions and parks the player, then seeds one local station whose stream url is a generated WAV reached over file://, through LocalStationsFixture so the station id is unique for the whole run and the browse tree agrees with the store.
5. AC1: open the locals list, select the station's own row, and assert the service reaches playing on that station's WAV url with no player error.
6. AC2 metadata: the now-playing bar comes up by itself, showing the station's name and the stream description the player reports, and a relaunched Activity rebuilds the same bar from the session rather than from the test.
7. AC2 controls: tapping the bar pauses playback and tapping it again resumes it, and the favorite box on the bar marks and unmarks the playing station in the store.
8. Pin the gate for a playable row the way the sibling journeys pin it for a browsable one: tapping the station's row offline starts nothing. Add the matching criterion to TASK-049.
9. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data.
<!-- SECTION:PLAN:END -->
