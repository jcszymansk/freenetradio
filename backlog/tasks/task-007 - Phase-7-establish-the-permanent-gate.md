---
id: TASK-007
title: 'Phase 7: establish the permanent gate'
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies:
  - TASK-006
type: chore
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The main roadmap restarts only when every item here holds. Run policy: affected JVM tests on every meaningful change, the full JVM suite before commit or handoff, JVM plus offline emulator suites before restarting main roadmap work, and everything plus the DHU and real-car checklist before a personal release.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All JVM tests pass
- [ ] #2 Instrumentation tests compile and pass on the canonical emulator
- [ ] #3 All offline end-to-end journeys pass
- [ ] #4 Critical pure-core coverage is at least 80% line and 70% branch
- [ ] #5 No critical class is considered covered solely because another class happened to execute it
- [ ] #6 Every fixed bug has a regression test at the lowest appropriate layer
- [ ] #7 The suite makes no external network requests
- [ ] #8 No ignored, commented-out, assertion-free or retry-masked tests exist
- [ ] #9 Android Auto manual checks have a recorded result for the current build
<!-- AC:END -->
