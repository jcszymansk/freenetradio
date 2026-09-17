---
id: TASK-005
title: 'Phase 5: cover playback and resilience'
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies:
  - TASK-004
type: chore
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The roadmap's highest operational risk, tested without relying on real radio streams. Do not invoke the service's process-killing stop path inside instrumentation: extract its decision logic for a component test and retain one manual lifecycle check.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Exit check: core playback and recovery behavior passes with networking disabled and no physical audio output required
<!-- AC:END -->
