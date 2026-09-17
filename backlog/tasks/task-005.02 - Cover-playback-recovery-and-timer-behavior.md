---
id: TASK-005.02
title: 'Cover playback, recovery and timer behavior'
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies: []
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
