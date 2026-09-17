---
id: TASK-004.01
title: Cover presenter and media item commands with fakes
status: In Progress
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-17 20:45'
labels: []
milestone: m-0
dependencies:
  - TASK-003
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add an android-jvm-stubs module wired as testRuntimeOnly so JVM tests compile against the platform API but run against working Uri, Bundle and MimeTypeMap implementations; retire TestUri.
2. Add shared command test support: a Context double that answers resource lookups, a recording OpenRadioServicePresenter, a recording ResultListener and playback state listener that can be awaited, and station, category and country builders.
3. Cover root composition for phone, car root and car browse, including favorites and locals gating, source gating and country entry rules.
4. Cover favorites and locals lists: invalid stations omitted, favorite and local flags, empty results.
5. Cover categories, child categories, countries, country stations, popular, new and both search nodes: delegation, pagination and refresh, empty-state behavior, saved-instance behavior.
6. Cover OpenRadioServicePresenterImpl: phone versus car command registration, url and model layer delegation, country cache memoization, favorite toggling, clear and close.
7. Run the JVM suite and the local coverage report; record the testing convention for the stub module in AGENTS.md.
<!-- SECTION:PLAN:END -->
