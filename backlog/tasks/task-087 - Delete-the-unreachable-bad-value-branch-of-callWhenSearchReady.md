---
id: TASK-087
title: Delete the unreachable bad-value branch of callWhenSearchReady
status: To Do
assignee: []
created_date: '2026-10-05 05:13'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 101000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService.callWhenSearchReady answers RESULT_ERROR_BAD_VALUE when the presenter has no command for MEDIA_ID_SEARCH_FROM_SERVICE. That cannot happen: the id is a constant, OpenRadioServicePresenterImpl puts that key in its command map unconditionally in init for car and phone alike, nothing removes it, and the registry is the only source of the presenter. Both callers, onSearch and onGetSearchResult, go through the same lookup. The branch was copied from callWhenSourceReady in 3440c63, where it is reachable. No test touches it and none can, so it is dead code that reads like a handled case. Dropping the else alone would leave a nullable lookup that either hangs the future or throws, so the lookup itself has to stop being nullable, and whatever replaces it must hand back the same command instance the map holds, because search paging state lives on that instance. Judged on TASK-068 and left out of it because the gate run must not change production code after the Android Auto check of 8ab2329.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 callWhenSearchReady has no branch for a missing search command, and the type system rather than a runtime check says why
- [ ] #2 Search from the service still pages through the same command instance the presenter holds, pinned by a JVM test
<!-- AC:END -->
