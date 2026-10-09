---
id: TASK-103
title: Decide whether a network resume may take audio focus back from another app
status: To Do
assignee: []
created_date: '2026-10-09 08:11'
labels: []
milestone: m-1
dependencies: []
type: enhancement
ordinal: 117000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ExoPlayer keeps audio focus while paused and abandons it only in STATE_IDLE (Media3 1.2.1 AudioFocusManager.updateAudioFocus). If another app takes focus permanently while a station is paused by the mobile-network policy or waiting for the network after a failure, NetworkRecovery stays in its waiting state, and the reconnect's prepare()+play() takes focus back from that app. Whether that is right is a product question: the user wanted the station playing when the network stopped it, but has since started something else. Related to TASK-016 criterion 11 (audio focus behaviour in general). Found while reviewing TASK-101.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The decision on whether a network resume may take audio focus from an app that took it permanently is recorded on this task
- [ ] #2 Playback follows that decision for both the policy pause and a network failure
- [ ] #3 Covered by a test that needs neither a network nor a real stream
<!-- AC:END -->
