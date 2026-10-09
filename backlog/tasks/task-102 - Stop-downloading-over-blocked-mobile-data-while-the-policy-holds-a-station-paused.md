---
id: TASK-102
title: >-
  Stop downloading over blocked mobile data while the policy holds a station
  paused
status: To Do
assignee: []
created_date: '2026-10-09 08:11'
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
- [ ] #1 While the mobile-network policy holds a station paused, the player transfers no stream data over the mobile network
- [ ] #2 The station still resumes when a network it may use connects, as TASK-101 requires
- [ ] #3 Whether the playback notification survives the policy pause is decided and recorded on this task
- [ ] #4 Covered by a test that needs neither a network nor a real stream
<!-- AC:END -->
