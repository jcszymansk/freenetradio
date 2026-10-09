---
id: TASK-101
title: >-
  Resume a station the mobile-network policy paused when a usable network
  returns
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-08 07:20'
updated_date: '2026-10-09 07:26'
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
1. Add a NetworkRecovery transition for a pause forced by the mobile-network policy: AWAITING_NETWORK when play was requested, otherwise keep the current state, so a second policy pause (listener and CMD_NET_CHANGED both fire, and the first one already cleared playWhenReady) does not forget the wait.
2. Call it from OpenRadioPlayer.pauseForNetworkPolicy with playWhenReady read before pausing.
3. Update the state diagram and the service/player KDoc.
4. Cover the decision in NetworkRecoveryTest (JVM, no network or stream).
<!-- SECTION:PLAN:END -->
