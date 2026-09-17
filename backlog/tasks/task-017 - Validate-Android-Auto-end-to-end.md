---
id: TASK-017
title: Validate Android Auto end to end
status: In Progress
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: chore
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The real phone and vehicle are the primary acceptance environment; the Desktop Head Unit may supplement but not replace them. Record the phone model, Android version, Android Auto version, connection type and head unit for each evaluation cycle.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Application discovery by Android Auto
- [ ] #2 Root and child browse nodes
- [ ] #3 Countries, categories, favorites and search
- [ ] #4 Starting a station from the car display
- [ ] #5 Play, pause, previous, next and steering-wheel controls where available
- [ ] #6 Metadata and artwork updates
- [ ] #7 Reconnection after unplugging or leaving the vehicle
- [ ] #8 Wired and wireless operation where the equipment supports it
- [ ] #9 Network loss, mobile-data restrictions and recovery
- [ ] #10 Audio interruption by calls, navigation and other media applications
- [ ] #11 Bluetooth disconnect and reconnect behavior
- [ ] #12 Background playback and service shutdown
- [ ] #13 Exit check: routine listening in the real vehicle without ADB intervention after installation
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Several hours of real-car use completed, including weak coverage and recovery after ordinary signal loss. One reproducible failure remains, tracked separately: playback does not always resume after a signal outage lasting several minutes.
<!-- SECTION:NOTES:END -->
