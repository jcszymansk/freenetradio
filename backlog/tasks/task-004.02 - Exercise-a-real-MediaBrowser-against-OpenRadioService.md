---
id: TASK-004.02
title: Exercise a real MediaBrowser against OpenRadioService
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-004
type: chore
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Expands the existing MediaResourcesManagerTest pattern. Android's guidance specifically calls for service startup before any Activity exists, and for force-stop and clear-data scenarios.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Connect before any Activity exists
- [ ] #2 Fetch the library root and root children
- [ ] #3 Seed favorites and locals, then verify their browse nodes
- [ ] #4 Subscribe to root and child nodes
- [ ] #5 Add, edit and remove a local station and verify immediate subscription refresh
- [ ] #6 Toggle favorite state and verify storage plus browse refresh
- [ ] #7 Verify sort-update commands and invalid command arguments
- [ ] #8 Verify unknown custom commands return not supported
- [ ] #9 Search using a seeded local cache fixture
- [ ] #10 Clear app data and reconnect successfully
<!-- AC:END -->
