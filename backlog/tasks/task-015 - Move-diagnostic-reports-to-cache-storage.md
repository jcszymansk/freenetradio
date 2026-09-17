---
id: TASK-015
title: Move diagnostic reports to cache storage
status: To Do
assignee: []
created_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: bug
ordinal: 29000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Recorded as a follow-up when explicit diagnostic sharing was implemented. Reports are written to internal persistent storage and an old report can be deleted while a receiving application may still be reading it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Report files written to the application cache directory rather than internal persistent storage
- [ ] #2 Old reports cleaned up without deleting a file a receiving application may still be reading
- [ ] #3 Sharing retested end to end after the change
<!-- AC:END -->
