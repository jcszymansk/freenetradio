---
id: TASK-004
title: 'Phase 4: cover browse commands and the Media3 service contract'
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 13:37'
labels: []
milestone: m-0
dependencies:
  - TASK-003
  - TASK-032
type: chore
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Protects the surface shared by the phone UI and Android Auto. Both clients browse the same tree through OpenRadioService, so a defect here is visible in the car.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Exit check: all offline browse nodes and supported custom commands are exercised through a real Media3 connection, including cold service startup without an Activity
<!-- AC:END -->
