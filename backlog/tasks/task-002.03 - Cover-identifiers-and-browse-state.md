---
id: TASK-002.03
title: Cover identifiers and browse state
status: Done
assignee: []
created_date: '2026-09-17 18:23'
updated_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-002
type: chore
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaId and BrowseTree define the browse hierarchy shared by the phone UI and Android Auto; a malformed identifier or a stale tree node is visible in the car.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 MediaId construction, normalization, search IDs, country IDs, sortable and refreshable classification
- [x] #2 BrowseTree replacement, append, item lookup, station lookup, parent-list lookup and invalidation
- [x] #3 Indexable command page reset and advancement
- [x] #4 Catalogue-change decisions
<!-- AC:END -->
