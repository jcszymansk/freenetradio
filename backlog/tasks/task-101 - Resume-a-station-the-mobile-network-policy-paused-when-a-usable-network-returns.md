---
id: TASK-101
title: >-
  Resume a station the mobile-network policy paused when a usable network
  returns
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-08 07:20'
updated_date: '2026-10-09 07:37'
labels: []
dependencies: []
type: bug
ordinal: 115000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
With playback over mobile data disabled, OpenRadioService pauses through OpenRadioPlayer.pauseForNetworkPolicy() whenever the connection turns out to be mobile, both from the connectivity listener and from CMD_NET_CHANGED. Since task-037 that pause leaves NetworkRecovery untouched, which keeps the earlier behaviour but only half of the intent: a station already waiting for the network (AWAITING_NETWORK after a network error) resumes when Wi-Fi returns, while a station that was playing fine on Wi-Fi, lost Wi-Fi to blocked mobile data and was paused by the policy stays paused when Wi-Fi comes back. The network, not the user, stopped it in both cases. The policy may also pause a station the user had already paused, and that one must stay paused.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A station that was playing when the mobile-network policy paused it resumes when a network it may use connects
- [ ] #2 A station the user had paused before the policy pause stays paused when a usable network connects
- [ ] #3 The decision is covered by a test that needs neither a network nor a real stream
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add NetworkRecovery state PAUSED_BY_NETWORK_POLICY, entered by onPolicyPause when a loaded stream is meant to play (playWhenReady while BUFFERING or READY; playWhenReady alone survives stop and failures). A paused player keeps loading, so onPlaybackReady and onNetworkLost keep the state; onPlayRequested (playWhenReady false->true) and onStopRequested end it. A policy pause while play is not requested keeps the current state, so a repeated policy pause does not forget the wait.
2. Call it from OpenRadioPlayer.pauseForNetworkPolicy and hook onPlayWhenReadyChanged.
3. In OpenRadioService run the resume decision on the same main-thread queue as the deferred policy pause, and also resume when CMD_NET_CHANGED finds playback no longer blocked (the user allowed mobile data).
4. Cover the decision in NetworkRecoveryTest (JVM, no network or stream).
<!-- SECTION:PLAN:END -->
