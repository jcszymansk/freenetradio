---
id: TASK-039
title: Decide what a tolerated playback error should do to the stream
status: To Do
assignee:
  - '@claude'
created_date: '2026-09-18 19:18'
labels: []
dependencies:
  - TASK-005.02
type: enhancement
ordinal: 54000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A playback error with no specific cause is swallowed while the error budget holds: the player neither tells the user nor touches the stream, and only the seventh consecutive error produces a message. The upstream code carried a commented-out prepareWithList(mIndex) and a bare TODO in that branch, so the retry was intended and never finished. That comment was removed in task-005.01 when the classification moved into PlaybackErrorClassifier, which returns PlaybackErrorDecision.WithinErrorBudget for the case; this task is where the intent now lives.

The open question is whether ExoPlayer already recovers from these on its own, in which case the budget is the whole behavior and the branch is correct, or whether the stream needs re-preparing, in which case six silent errors are six silent failures for the user. Answer that before changing anything.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A decision is recorded on whether a tolerated error re-prepares the stream, and why
- [ ] #2 PlaybackErrorDecision.WithinErrorBudget either carries the chosen recovery or is documented as deliberately doing nothing
- [ ] #3 Whatever is chosen is covered by a test that needs no real stream
<!-- AC:END -->
