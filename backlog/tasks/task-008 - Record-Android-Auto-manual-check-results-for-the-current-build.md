---
id: TASK-008
title: Record Android Auto manual check results for the current build
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-24 20:16'
labels:
  - manual
milestone: m-0
dependencies: []
type: chore
ordinal: 22000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Automated Media3 service tests cover the protocol shared by the phone and Android Auto, but not the projected UI. These checks stay manual and their result must be recorded per build for the Phase 7 gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Android Auto controller classification
- [ ] #2 Desktop Head Unit and real head-unit rendering and navigation
- [ ] #3 Steering-wheel and media-button behavior
- [ ] #4 Voice search integration
- [ ] #5 Real Bluetooth disconnection
- [ ] #6 Calls and navigation audio interruption
- [ ] #7 Wired reconnection
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Criterion 7 was reworded from 'Wired and wireless reconnection' to wired only on 2026-09-24: the car used for the manual checks does not support wireless Android Auto, so the wireless half cannot be tested with it.
<!-- SECTION:NOTES:END -->
