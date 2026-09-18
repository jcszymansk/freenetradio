---
id: TASK-037
title: Clear the stopped-by-network flag once playback recovers
status: To Do
assignee:
  - '@claude'
created_date: '2026-09-18 19:17'
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
- [ ] #1 Reaching a ready playback state clears the stopped-by-network flag
- [ ] #2 A network-connected broadcast restarts playback only for a station that the network, not the user, stopped
- [ ] #3 The recovery decision is covered by a test that needs neither a network nor a real stream
<!-- AC:END -->
