---
id: TASK-102
title: >-
  Stop downloading over blocked mobile data while the policy holds a station
  paused
status: Done
assignee:
  - '@claude'
created_date: '2026-10-09 08:11'
updated_date: '2026-10-10 07:17'
labels: []
milestone: m-1
dependencies: []
type: bug
ordinal: 116000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
When playback over mobile data is disabled, OpenRadioService pauses through OpenRadioPlayer.pauseForNetworkPolicy(), which only calls ExoPlayer's pause(). A paused ExoPlayer keeps loading until the LoadControl's buffer is full and keeps the connection open (Media3 1.2.1 ExoPlayerImplInternal.shouldContinueLoading consults only the LoadControl, not playWhenReady), so the policy that exists to save mobile data still spends up to a full buffer of it. A playlist fallback can also fetch over mobile. Stopping the player instead would end the loading, but Media3 drops the session notification while the player is in STATE_IDLE, so the fix has to weigh that. TASK-101 relies on a paused player still loading: NetworkRecovery keeps PAUSED_BY_NETWORK_POLICY through onPlaybackReady and onNetworkLost for that reason, and those exemptions may become unnecessary. Found while reviewing TASK-101.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 While the mobile-network policy holds a station paused, the player transfers no stream data over the mobile network
- [x] #2 The station still resumes when a network it may use connects, as TASK-101 requires
- [x] #3 Whether the playback notification survives the policy pause is decided and recorded on this task
- [x] #4 Covered by a test that needs neither a network nor a real stream
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. OpenRadioPlayer.pauseForNetworkPolicy records the policy pause as before, then pauses and stops the ExoPlayer without clearing its media items. STATE_IDLE releases the media source, which cancels the loader and closes the HTTP connection; a pause alone keeps loading until the LoadControl buffer is full, and a LoadControl gate in Media3 1.2.1 leaves the socket open and overshoots by up to 1 MiB.
2. The resume path is unchanged: prepare() and play() re-prepare the kept media items on a fresh connection, and play() raising playWhenReady ends the policy pause in NetworkRecovery.
3. A stopped player neither becomes ready nor fails, so NetworkRecovery drops the PAUSED_BY_NETWORK_POLICY exemptions in onPlaybackReady and onNetworkLost; update its diagram, KDoc and NetworkRecoveryTest.
4. Notification: Media3 removes it in STATE_IDLE and the service already leaves foreground on pause; decided to let it go during the policy pause rather than override onUpdateNotification.
5. Instrumented OpenRadioPlayer test against a paced endless WAV on LoopbackHttpFixture: after the policy pause the connection closes and no further bytes are sent; prepare()+play() opens a new connection and plays.
6. Playlist fallback over mobile is split to a follow-up task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Notification decision (AC3): the playback notification does not survive the policy pause. Media3 1.2.1 MediaNotificationManager.shouldShowNotification hides it whenever the player is in STATE_IDLE, and a stop is the only way in 1.2.1 to release the media source and close the connection: a paused player keeps loading to the LoadControl buffer, and a LoadControl gate leaves the socket open and reads up to 1 MiB more (ProgressiveMediaPeriod checks only every continueLoadingCheckIntervalBytes). The service already leaves foreground on a pause, so only the visible notification is lost; it returns when the station resumes. Keeping it would mean overriding onUpdateNotification and managing a frozen notification by hand, which was rejected. The playlist fallback over mobile data is split into TASK-104.

Validation: ./gradlew test, verifyPureCoreCoverage, assembleDebug and :app:assembleDebugAndroidTest pass. Full :app:connectedDebugAndroidTest passes offline on the emulator (233/233). OpenRadioPlayerPolicyPauseTest fails its four policy cases with the stop() removed, while the plain-pause control passes. Not verified on a real phone on blocked mobile data, which the offline emulator cannot provide. Codex review round 1: PASS, no findings.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The mobile-network policy pause now stops the player as well as pausing it, keeping its media items. A paused ExoPlayer kept loading to its buffer over the blocked connection; a stop releases the media source and closes the connection, and the existing prepare-and-play resume brings the station back on a usable network. NetworkRecovery dropped the policy-pause exemptions for ready and network-failure events, which a stopped player no longer produces. The playback notification disappears during the policy pause and returns on resume (decided and recorded). The playlist fallback over mobile data is split into TASK-104. Verified by OpenRadioPlayerPolicyPauseTest, which counts the bytes a loopback broadcast sends after the pause, NetworkRecoveryTest, and the full JVM and offline instrumented suites.
<!-- SECTION:FINAL_SUMMARY:END -->
