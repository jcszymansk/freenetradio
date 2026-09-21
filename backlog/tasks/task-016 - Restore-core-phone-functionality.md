---
id: TASK-016
title: Restore core phone functionality
status: To Do
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-21 20:03'
labels: []
milestone: m-1
dependencies:
  - TASK-068
type: feature
ordinal: 30000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The smallest useful feature set has to be validated and repaired on real hardware before a personal release. Cloud sync, accounts, recommendations and telemetry are explicitly deferred.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Browse categories and countries
- [ ] #2 Search stations
- [ ] #3 Play, pause, stop and switch stations
- [ ] #4 Display current station and stream metadata
- [ ] #5 Add and remove favorites
- [ ] #6 Add, edit and remove local stations
- [ ] #7 Persist the last station and user settings
- [ ] #8 Handle direct streams and supported playlist formats
- [ ] #9 Recover cleanly from unavailable or malformed streams
- [ ] #10 Continue playback in the background with a correct foreground notification
- [ ] #11 Pause or resume appropriately for audio focus, output changes and Bluetooth events
<!-- AC:END -->
