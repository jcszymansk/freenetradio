---
id: TASK-005.02
title: 'Cover playback, recovery and timer behavior'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 10:39'
labels: []
milestone: m-0
dependencies:
  - TASK-004
parent_task_id: TASK-005
type: chore
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Playback is exercised against a generated local WAV file with emulator networking disabled.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Playback of a generated local WAV file
- [ ] #2 Expanding a selected item to the expected parent playlist
- [ ] #3 Switching stations
- [ ] #4 Pause, resume, stop, previous and next
- [ ] #5 Current-item and metadata updates
- [ ] #6 Last-station persistence
- [ ] #7 Malformed and unsupported playlist handling
- [ ] #8 Network-error and HTTP 403/404 classification
- [ ] #9 The mobile-data-disabled gate
- [ ] #10 Network loss and recovery transitions
- [ ] #11 Becoming-noisy pause
- [ ] #12 Bluetooth-connect decision logic using broadcast intents
- [ ] #13 Sleep-timer start, replacement, cancellation and completion
- [ ] #14 Playback resumption with and without an existing playlist
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Seam: replace OpenRadioServicePresenter.isMobileNetwork()/getUseMobile() with a single isPlaybackBlockedByMobileNetwork(); fold the three verbatim copies of the gate in OpenRadioService into one call. Nothing else calls either half.
2. Cover the gate's truth table on the JVM in OpenRadioServicePresenterImplTest, against a test context that answers getSharedPreferences with an in-memory fake so the stored side can be false.
3. JVM SleepTimerImplTest: start, replacement, cancellation, a refused past timestamp, exactly one completion.
4. Instrumented fixtures: a generated WAV written to the app cache dir and reached over file://, and a minimal loopback HTTP server used only where raw HTTP is the subject (playlist resolution, 403/404).
5. Extend ServiceBrowser with transport control and player state/metadata observation, keeping the existing main-looper-plus-deadline discipline.
6. OpenRadioServicePlaybackTest over a real MediaBrowser: play the WAV, expand a selection to its parent playlist, switch stations, pause/resume/stop/previous/next, current item and metadata, last-station persistence, resumption with and without a playlist.
7. OpenRadioServiceRecoveryTest: unsupported and malformed playlist handling, 403/404 classification end to end, and the network-loss transition against a host that cannot resolve offline.
8. BecomingNoisyReceiverTest and BTConnectionReceiverTest drive the receivers with synthetic intents.
9. Instrumented SleepTimerModelImplTest for the storage and listener fan-out half of the timer.
10. Record every defect the coverage exposes as its own task.
11. Verify: ./gradlew test, ./gradlew :app:assembleDebugAndroidTest, ./gradlew :app:connectedDebugAndroidTest with emulator networking disabled.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The mobile-network gate became one presenter question, isPlaybackBlockedByMobileNetwork(). OpenRadioService asked isMobileNetwork() and getUseMobile() in three places and combined them identically each time, and nothing else called either half, so the two were replaced rather than added to. That is the only production change the coverage needed, and it is what makes the gate assertable: isMobileNetwork() is false for the whole run on a device with networking disabled, so the decision could not otherwise be reached.

Two paths cannot be driven end to end and are covered at the level that can express them, which is recorded here rather than left as a gap. ACTION_AUDIO_BECOMING_NOISY and BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED are both protected broadcasts: an application cannot send either, and neither can the instrumentation shell (verified: am broadcast answers Permission Denial from uid 2000). Their receivers are therefore driven directly with the intents the system would deliver. Sleep-timer completion ends in OpenRadioService.closeService, which kills the process the tests run in, so the timer is covered through its own model and never through the service's listener.

Playback is exercised against generated WAV files over file://, per the testing roadmap. A loopback HTTP server was added for the two paths HTTP defines and that cannot exist without one: a playlist url is resolved by opening it as an HttpURLConnection, and a refused stream is classified from the status code it was refused with. Both stay on 127.0.0.1, spelled out as IPv4 because InetAddress.getLoopbackAddress() answers ::1 here and an unbracketed ::1 reaches the player as a malformed port.

The tests have to hand the service back a player that holds a queue. Once a station has played, the service has an active station, and from then on every page-0 browse with an empty queue calls maybeCreateInitialPlaylist, which asks the provider for stations and replaces the queue with the answer. That would reach into unrelated test classes.

Four defects surfaced and are tracked rather than fixed: task-040 (playback resumption can never restore anything), task-041 (the initial playlist shadows a station's real parent list), task-042 (moveMediaItems is not forwarded to the wrapped player) and task-043 (an unresolvable playlist is treated as a resolved one). Two instrumented tests, aPlayRequestWithNothingLoadedRestoresNothing and aPlaylistThatCannotBeOpenedResolvesToOneEmptyUrl, pin the current behavior and have to be rewritten when 040 and 043 are fixed.

The resume half of network recovery is deliberately not covered here: it turns on OpenRadioPlayer.mStoppedByNetwork, and task-037 already owns both the decision about where that state belongs and the test for it.
<!-- SECTION:NOTES:END -->
