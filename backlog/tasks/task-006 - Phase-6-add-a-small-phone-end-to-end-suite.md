---
id: TASK-006
title: 'Phase 6: add a small phone end-to-end suite'
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies:
  - TASK-005
type: chore
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Proves the user-visible system is assembled correctly. Keep the suite deliberately small: parser errors, cache branches and storage boundaries belong in cheaper layers, not in UI tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Exit check: all six journeys pass on a clean API 34 emulator with networking disabled, without retries or test-order assumptions
<!-- AC:END -->
