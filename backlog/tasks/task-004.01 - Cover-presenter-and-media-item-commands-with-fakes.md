---
id: TASK-004.01
title: Cover presenter and media item commands with fakes
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-004
type: chore
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioServicePresenterImpl and the MediaItemCommand implementations compose every browse node. Testing them with fake collaborators is far cheaper than driving the same cases through a real service.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Root composition
- [ ] #2 Favorites and Locals appear only when populated
- [ ] #3 Country entry rules
- [ ] #4 Phone versus car root command registration
- [ ] #5 Categories, countries, popular, new and search nodes
- [ ] #6 Playable and browsable metadata
- [ ] #7 Empty-state behavior
- [ ] #8 Pagination and refresh
- [ ] #9 Invalid stations are omitted
<!-- AC:END -->
