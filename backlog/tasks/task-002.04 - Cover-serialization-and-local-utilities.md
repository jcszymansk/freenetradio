---
id: TASK-002.04
title: Cover serialization and local utilities
status: Done
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-002
type: chore
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Favorites, local stations and equalizer state are persisted as serialized values, so a round-trip defect silently destroys user data.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Complete RadioStation round trips
- [x] #2 Complete equalizer-state round trips and malformed data
- [x] #3 Map import and export with malformed and missing entries
- [x] #4 Playlist dispatch for M3U, M3U8, PLS, ASX and XSPF using in-memory streams
- [x] #5 In-memory API cache operations
- [x] #6 URL construction, query encoding and pagination
- [x] #7 Filter rules, including empty-stream behavior
<!-- AC:END -->
