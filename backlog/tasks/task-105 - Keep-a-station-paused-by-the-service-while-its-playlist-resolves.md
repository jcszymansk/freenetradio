---
id: TASK-105
title: Keep a station paused by the service while its playlist resolves
status: To Do
assignee: []
created_date: '2026-10-11 18:06'
labels: []
milestone: m-1
dependencies: []
type: bug
ordinal: 119000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The service pauses the player itself when audio becomes noisy or the Bluetooth device disconnects (handlePauseRequest calls OpenRadioPlayer.pause directly, not through a controller), so the play request's PlaylistFallback is not reset. If that pause lands while the station's playlist is being read, the read still answers, and playResolvedStream calls prepare and play on the stream the playlist named, starting audio the user's headphones just stopped. A pause through a controller is safe, because onPlayerCommandRequest starts a new request and the late answer is ignored. Found while working on TASK-104.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A station paused by a noisy-audio or Bluetooth-disconnect event while its playlist is read does not start playing when the read answers
- [ ] #2 The stream the playlist named is kept, so playing the station again does not read the playlist again
- [ ] #3 Covered by a test that needs neither a network nor a real stream
<!-- AC:END -->
