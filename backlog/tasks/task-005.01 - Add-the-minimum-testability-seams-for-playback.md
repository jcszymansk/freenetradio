---
id: TASK-005.01
title: Add the minimum testability seams for playback
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-005
type: enhancement
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Production seams needed before playback can be tested offline. Keep them minimal and reuse existing interfaces; do not introduce a mocking or DI framework.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Player error classification in OpenRadioPlayer is injectable or isolated
- [ ] #2 RadioStationValidator is injected into RadioStationManagerLayerImpl so mutation tests do not probe the internet
- [ ] #3 No mocking or dependency-injection framework added
<!-- AC:END -->
