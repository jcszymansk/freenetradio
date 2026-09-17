---
id: TASK-002.02
title: Cover cache and download policy
status: Done
assignee: []
created_date: '2026-09-17 18:23'
updated_date: '2026-09-17 18:23'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-002
type: chore
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ModelLayerImpl decides between memory cache, persistent cache and network. Wrong precedence either wastes mobile data or serves stale directories.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 No connectivity returns no data and touches no cache or downloader
- [x] #2 A memory hit bypasses persistence and download
- [x] #3 A persistent hit is promoted to memory
- [x] #4 Empty and [] cache values are misses
- [x] #5 A successful download replaces both cache levels
- [x] #6 An empty download is not cached
- [x] #7 The parser receives exactly the selected response
<!-- AC:END -->
