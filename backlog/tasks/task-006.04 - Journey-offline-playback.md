---
id: TASK-006.04
title: 'Journey: offline playback'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 06:03'
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
- [x] #1 Select a local WAV-backed station
- [x] #2 Now-playing metadata and controls verified
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The station's stream is a generated WAV reached over file://, per the testing roadmap, seeded through LocalStationsFixture rather than through the add dialog: creating a station is TASK-006.02's subject, and the fixture already owns the id discipline this suite needs, since the browse tree keeps entries keyed by a station id that no browse invalidates.

The fixture now builds a station's name from its id as well. What the now-playing bar shows outlives the Activity that put it there and the test that put it there, so a repeated name would let a stale bar pass for the one a case is waiting for. Every case asserts the bar is not already showing its own station before it selects it, which is what makes waiting for it mean anything.

Playback cannot be started by tapping the row. The connectivity gate in handleItemSelected refuses a playable row for the same missing connection that makes it refuse a browsable one, so JourneyPlayback makes the call the tap would have made, through MediaResourcesManager.playFromMediaId, the presenter's own class, on the main looper because that is where the presenter calls it from and because Media3 rejects the controller read it starts with from anywhere else. TASK-049 criterion 8 now carries the playable half of the gate.

Confirmed both halves by experiment rather than by reading. With the selection removed, all five playing cases fail and the gate case still passes. With the gate case selecting the station the way a tap would past the gate, it fails with the station playing and nothing else in the class changes answer, so the assertion will flip when TASK-049 lands.

Found TASK-051 on the way: this is the first test in the suite to leave a station active before OpenRadioServiceCommandTest, and handleFavorite falls back to the active station for any media id it cannot resolve, writing the favorites store before it decides whether it can answer at all. favoriteCommandRejectsAStationOutsideTheBrowseTree had passed only because nothing had ever played in the process. On the user's decision the behaviour is pinned rather than fixed: that case now plays a station itself and asserts the mark the service really makes, so it no longer depends on what ran before it, and TASK-051 carries the fix and the assertion that changes with it.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Adds OfflinePlaybackJourneyTest, the fourth of the six Phase 6 phone journeys, plus the two helpers it needed: NowPlayingView, which reads the bar the phone shows above the browse list, and JourneyPlayback, which starts playback the way selecting a row does.

Six cases, each starting from a cleared, offline profile with a stopped player and one station of the user's own whose stream is a generated WAV reached over file://. Nothing below the UI is stubbed: the real OpenRadioPlayer opens the file through the same data source it builds for a stream, and the emulator has no audio output.

AC1, selecting a station: the station is selected on its own rendered row in the locals list, with the item the adapter bound that row from and the node the presenter reports standing in, so neither argument is invented by the test. The player is then asserted to be ready, on that station, with that station's own url open and no error, so a service playlist or some other item cannot answer for it.

AC2, metadata: the bar comes up by itself on the metadata the session pushes, showing the station's name and the line the player writes about the stream, and it is asserted not to be showing that station beforehand. A second Activity, handed nothing by the test, rebuilds the same bar from the session it connects to, so what the bar shows belongs to the session rather than to the screen that started playback.

AC2, controls: the bar is the phone's only transport control, and tapping it pauses the station and tapping it again resumes it, with the station held across both. The favorite box on the bar marks and unmarks the station that is playing, read back through a storage instance that has cached no answers.

The gate is pinned as well: tapping the station's row offline starts nothing, because handleItemSelected refuses a playable row exactly as it refuses a browsable one. TASK-049 carries that as criterion 8.

Verified on emulator-5554, a clean API 34 AVD with wifi and mobile data disabled and no application data on it, twice in a row: the full instrumented suite passes at 197 tests, up from 191, and ./gradlew test --rerun-tasks passes.

Non-vacuous by mutation: with the selection removed all five playing cases fail; with the gate case selecting the station the way a tap would past the gate, it fails with the station playing and nothing else changes answer.

One defect surfaced and is tracked as TASK-051, with its current behaviour pinned in OpenRadioServiceCommandTest on the user's decision.
<!-- SECTION:FINAL_SUMMARY:END -->
