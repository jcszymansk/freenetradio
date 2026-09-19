---
id: TASK-042
title: Forward moveMediaItems to the player it wraps
status: To Do
assignee: []
created_date: '2026-09-19 10:26'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 57000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioPlayer.moveMediaItems reorders its own shadow copy of the queue and never calls mPlayer.moveMediaItems, unlike every other queue mutation on the class, which forwards first and mirrors second. A controller that reorders a range of items therefore changes what the wrapper reports and nothing about what plays, and the two views of the queue disagree from then on.

Found by inspection while covering playback for TASK-005.02. Nothing in the application reorders a queue today, which is why it has not surfaced; a media3 controller can send the command regardless.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 moveMediaItems forwards the move to the wrapped player
- [ ] #2 The wrapper's queue and the player's queue agree after a range move
- [ ] #3 Covered by a test
<!-- AC:END -->
