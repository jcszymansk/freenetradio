---
id: TASK-018
title: Fix playback not resuming after a long signal outage
status: To Do
assignee: []
created_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies:
  - TASK-017
type: bug
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Found during real-vehicle Android Auto evaluation. After ordinary brief signal loss the application recovers, but after an outage lasting several minutes playback does not always resume by itself. This is the one known blocker for general availability.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Failure reproduced deliberately, with the reproduction recorded
- [ ] #2 Recovery path fixed so playback resumes after a multi-minute outage
- [ ] #3 Regression test at the lowest layer that can express it
- [ ] #4 Re-verified in a real vehicle
<!-- AC:END -->
