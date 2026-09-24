---
id: TASK-053
title: >-
  Stop instrumented browse tests from relying on a player queue another class
  left
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 17:45'
updated_date: '2026-09-24 05:24'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService calls maybeCreateInitialPlaylist on any page-0 browse. With an empty player queue and an active station stored, that path calls getNewStations, which downloads stations/lastchange/250, and then handlePlayRequestUiThread, which starts playing whatever came back. This is the one path in the suite that could put internet audio through the speakers during a test run. The service is process-wide and outlives every test class, so "the queue is not empty" is currently an accident of ordering rather than an asserted invariant. Six classes browse the root without calling LocalStationsFixture.parkThePlayer: OpenRadioServiceBrowseTest, OpenRadioServiceSearchTest, ColdLaunchJourneyTest, FavoriteLifecycleJourneyTest, LocalStationLifecycleJourneyTest and SettingsPersistenceJourneyTest. parkThePlayer itself has three silent early returns, the last being mItems.firstOrNull() ?: return, so a class whose setup failed before seeding leaves exactly the state that arms the path for the next class, and nothing reports that it happened. Found while auditing criterion 7 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every instrumented class that browses page 0 either parks the player or asserts the queue is not empty before it does
- [ ] #2 parkThePlayer reports when it cannot park instead of returning silently
- [ ] #3 The invariant holds when a class runs alone and when it runs after any other class
<!-- AC:END -->
