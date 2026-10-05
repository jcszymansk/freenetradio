---
id: TASK-008
title: Record Android Auto manual check results for the current build
status: Done
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-10-05 04:36'
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
- [x] #1 Android Auto controller classification
- [x] #2 Desktop Head Unit and real head-unit rendering and navigation
- [x] #3 Steering-wheel and media-button behavior
- [x] #4 Voice search integration
- [x] #5 Real Bluetooth disconnection
- [x] #6 Calls and navigation audio interruption
- [x] #7 Wired reconnection
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Criterion 7 was reworded from 'Wired and wireless reconnection' to wired only on 2026-09-24: the car used for the manual checks does not support wireless Android Auto, so the wireless half cannot be tested with it.

Results for the debug build of 8ab2329: all seven checks pass, run on the Desktop Head Unit and a real car over a wired connection. Observation on criterion 7: after a reconnection Android Auto always opens the last active navigation app, and there seems to be no setting to change it. This is Android Auto's own launcher behavior, not something FreeNetRadio controls, so it does not fail the check.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Recorded the manual Android Auto checks for the debug build of 8ab2329: controller classification, DHU and real head-unit rendering and navigation, steering-wheel and media buttons, voice search, Bluetooth disconnection, call and navigation audio interruption, and wired reconnection all pass. Reconnection always returns to the last navigation app, which is Android Auto's behavior.
<!-- SECTION:FINAL_SUMMARY:END -->
