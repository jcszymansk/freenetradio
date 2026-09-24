---
id: TASK-086
title: Stop a favorite command that changes nothing from reporting success
status: To Do
assignee: []
created_date: '2026-09-24 12:28'
labels: []
milestone: m-1
dependencies: []
priority: low
type: bug
ordinal: 100000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
`OpenRadioService.handleFavorite` writes the favorites store through `OpenRadioServicePresenterImpl.updateRadioStationFavorite` without checking whether the station is already in the requested state. It then updates the browse tree item and the player metadata, calls `maybeNotifyRootChanged` and answers `RESULT_SUCCESS`. So a command that removes a station that is not a favorite, or adds one that already is, looks exactly like a real change: same answer, same children-changed notification. The caller cannot tell it did nothing.

This matters because the favorite command names are easy to send the wrong way round (TASK-085). A caller that picks the wrong one gets success and a notification, and only reading the store afterwards shows nothing was marked. TASK-006.06 hit exactly this. It also hides a stale client: a favorite box bound from outdated metadata sends the direction the box shows, not the one the store needs.

Whether a no-op should be an error, a separate result code, or a success without side effects is for whoever picks this up. Repeating a command is a legitimate case (a car button pressed twice, two clients racing), so plain idempotency must stay possible for callers that want it.

Related but separate: TASK-051 covers a command naming a station the service cannot resolve, in the same function. Found while reviewing TASK-071.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A caller can tell from the command result whether a favorite command changed the favorites store
- [ ] #2 A favorite command that leaves the store unchanged does not notify children changed and does not rewrite the browse tree item or player metadata
- [ ] #3 Marking and unmarking a station from the phone favorite box, the now-playing favorite box and the car custom layout buttons still work and report a change
- [ ] #4 OpenRadioServiceCommandTest covers marking a station that is already a favorite and unmarking one that is not, asserting the result, the untouched store and the absence of a notification, with no network
<!-- AC:END -->
