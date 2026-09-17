---
id: TASK-002
title: 'Phase 2: cover the pure data and domain core'
status: Done
assignee: []
created_date: '2026-09-17 18:22'
updated_date: '2026-09-17 18:22'
labels: []
milestone: m-0
dependencies: []
type: chore
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Establishes the large, fast base of the test pyramid: parsers, cache policy, identifiers, browse state and serialization, all as local JVM tests with hand-written recording fakes.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Exit check: the selected pure-core package set reaches at least 80% line and 70% branch coverage
- [x] #2 Exit check: every listed class has normal, edge and failure-path tests
<!-- AC:END -->
