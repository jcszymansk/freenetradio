---
id: TASK-005.02
title: 'Cover playback, recovery and timer behavior'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 10:11'
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
