---
id: TASK-057
title: Restore the browse stack in the journey cases that leave it borrowed
status: To Do
assignee: []
created_date: '2026-09-21 17:45'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 72000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaPresenterImpl keeps its media item stack in a registry singleton that outlives the Activity, so a case that walks into a browse node and does not walk back sends the next Activity there instead of to the root. Every navigating case in the journey classes wraps the walk in a finally that calls returnToRoot except FavoriteLifecycleJourneyTest line 284 and LocalStationLifecycleJourneyTest line 315, and the failure those two exist to catch is precisely "the node opened", which is the one case that leaves the stack inside the node. JourneyNavigation.returnToRoot carries its own hazard, named in its own KDoc: if the stack ever holds a single non-root entry, MediaPresenterImpl takes the size-1 branch and sends CMD_STOP_SERVICE, which calls Process.killProcess and takes the whole run down, and the next loop iteration reads last on a cleared list. MAX_BROWSE_DEPTH bounds the loop but prevents neither. Found while auditing criterion 8 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every journey case that opens a browse node returns to the root even when it fails
- [ ] #2 returnToRoot cannot send CMD_STOP_SERVICE or read past the end of a cleared stack
- [ ] #3 A journey class leaves the presenter stack as it found it
<!-- AC:END -->
