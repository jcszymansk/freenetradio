---
id: TASK-052
title: Make the instrumented suite offline guarantee structural
status: To Do
assignee: []
created_date: '2026-09-21 17:44'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 67000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The suite is safe today because the operator runs adb shell svc wifi disable before it, not because of how the tests are built. Only JourneyProfile checks, through a private assertTheDeviceIsOffline called from the six journeys. The five OpenRadioService test classes, MediaResourcesManagerTest, AddStationDialogTest and PlaylistResolutionTest have no such guard, and those are the classes that would fetch from Radio Browser on a networked device. Three fixtures also name hosts a resolver would look up: stream.invalid in OpenRadioServiceRecoveryTest (the reserved TLD is not short-circuited by bionic or glibc, so the query does leave the device), https://example.test/ ids in StorageTestStations, and app/src/androidTest/resources/undetected_streams.txt, 1238 live stream URLs left behind when AutoDetectParserAndroidTest was deleted in 8b9cf8f and referenced by nothing. Criterion 7 of TASK-007 cannot be claimed while the promise rests on run policy rather than on the tests. Found while auditing that criterion.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every instrumented test class that reaches the service or the network asserts the device is offline before its cases run
- [ ] #2 A run started with networking enabled fails loudly instead of quietly fetching
- [ ] #3 No test fixture names a host that a resolver would look up
- [ ] #4 app/src/androidTest/resources/undetected_streams.txt is removed
<!-- AC:END -->
