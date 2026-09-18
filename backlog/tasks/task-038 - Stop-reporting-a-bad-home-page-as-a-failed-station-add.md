---
id: TASK-038
title: Stop reporting a bad home page as a failed station add
status: To Do
assignee:
  - '@claude'
created_date: '2026-09-18 19:17'
labels: []
dependencies: []
type: bug
ordinal: 53000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RadioStationValidator answers a candidate station through three callbacks: onSuccess, onWarning for a defect that does not block the add, and onFailure. RadioStationManagerLayerImpl.addRadioStation wires both onWarning and onFailure to its own onFailure. A station whose stream is fine but whose home page is unreachable therefore produces onFailure("Radio Station's home page is invalid") and then, from the same validation run, onSuccess("Radio Station added successfully"). The dialog shows the user an error for a station that was added.

Found while injecting the validator in task-005.01. The manager exposes only onSuccess and onFailure to its callers, so fixing this means deciding whether a warning reaches the user at all and through what.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A station with a valid stream and an unreachable home page is added and reported once
- [ ] #2 A station with an invalid stream is still reported as a failure and is not added
- [ ] #3 The behavior is covered by a test that injects a RadioStationValidator and reaches no network
<!-- AC:END -->
