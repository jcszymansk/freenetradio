---
id: TASK-037
title: Clear the stopped-by-network flag once playback recovers
status: Done
assignee:
  - '@claude'
created_date: '2026-09-18 19:17'
updated_date: '2026-10-05 20:27'
labels: []
dependencies: []
type: bug
ordinal: 52000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioPlayer sets mStoppedByNetwork to true when a playback error carries ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, and nothing ever sets it back to false. OpenRadioService.callPlayFromNetworkConnected consults it to decide whether a network-connected broadcast should restart playback, so after the first network drop in the process every later reconnect restarts playback, including reconnects that arrive while the user has deliberately paused or stopped a station that is playing fine.

Found while isolating the playback error classification in task-005.01. The flag is the only piece of network-recovery state the player keeps, and it is a plain field on a class that cannot be built without a real ExoPlayer, so covering it means deciding where the state belongs first.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Reaching a ready playback state clears the stopped-by-network flag
- [x] #2 A network-connected broadcast restarts playback only for a station that the network, not the user, stopped
- [x] #3 The recovery decision is covered by a test that needs neither a network nor a real stream
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Move the stopped-by-network state out of OpenRadioPlayer into a pure NetworkRecovery enum whose transitions return the next state (network lost while play requested -> AWAITING_NETWORK; ready, stop request, or loss while paused -> IDLE).
2. Drive it from OpenRadioPlayer: STATE_READY, NetworkLost errors (with playWhenReady), and every pause/stop/setPlayWhenReady(false)/reset reaching the player.
3. Keep the mobile-network-blocked pause from clearing it (pauseForNetworkPolicy), so a station the network stopped still resumes on a network it may use.
4. Service asks resumesOnReconnect() before restarting playback on a network-connected event.
5. JVM test NetworkRecoveryTest; list the class in gradle/pure-core-coverage.tsv.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Media3 1.2.1 paths checked from bytecode: controller and legacy (Android Auto) pause reach Player.pause(), stop reaches Player.stop(), play from IDLE-with-error calls prepare() then play(); ExoPlayer keeps playWhenReady across a fatal error and across stop(). The player listener and the connectivity receiver both run on the main thread.
A pause forced by the mobile-network policy (pauseForNetworkPolicy) deliberately leaves the state alone, which keeps the earlier behaviour: a station the network stopped still resumes when a usable network returns. It does not newly arm a resume for a healthy station the policy paused.
Evidence level: the transitions are proven by NetworkRecoveryTest; the wiring in OpenRadioPlayer (an ExoPlayer wrapper, excluded from the pure-core gate) was checked by reading it and by the round-1 codex review, not on a device.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Replaced OpenRadioPlayer's never-cleared mStoppedByNetwork with NetworkRecovery, a pure enum whose transitions return the next state: a network loss while play is requested waits for the network; STATE_READY, any pause/stop/setPlayWhenReady(false)/reset, or a loss while paused clears it. OpenRadioService restarts playback on a network-connected event only when resumesOnReconnect() is true; the mobile-network-blocked pause goes through pauseForNetworkPolicy and leaves the state alone. Verified with NetworkRecoveryTest (11 JVM cases), ./gradlew test, verifyPureCoreCoverage, verifyPureCoreAttribution and assembleDebug; codex review round 1 PASS. Not run on a device.
<!-- SECTION:FINAL_SUMMARY:END -->
