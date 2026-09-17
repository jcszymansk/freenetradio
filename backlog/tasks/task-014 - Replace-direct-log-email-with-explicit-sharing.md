---
id: TASK-014
title: Replace direct log email with explicit sharing
status: Done
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: feature
ordinal: 28000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The inherited build shipped SMTP credentials and mailed logs directly. Diagnostics must now leave the device only through a chooser the user drives, with no application-owned transport.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 No embedded SMTP credentials and no Jakarta Mail dependency
- [x] #2 Report collected only after an explicit per-report confirmation
- [x] #3 Report exposed through FileProvider, never a raw file path
- [x] #4 ACTION_SEND chooser used so the user picks the application and recipient
- [x] #5 No background, automatic or always-send submission path
- [x] #6 Chooser and attachment flow verified manually on a real phone
<!-- AC:END -->
