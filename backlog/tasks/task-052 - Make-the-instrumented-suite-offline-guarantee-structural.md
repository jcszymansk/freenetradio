---
id: TASK-052
title: Make the instrumented suite offline guarantee structural
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 17:44'
updated_date: '2026-09-23 04:43'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add OfflineTestRunner (AndroidJUnitRunner subclass, wired as testInstrumentationRunner) that registers OfflineDeviceRunnerBuilder via the runnerBuilder argument, so every test class goes through it however the run is launched.
2. OfflineDeviceRunnerBuilder: when the device reports an active network, return a runner that fails the class with the operator instruction; otherwise defer to the default builders. Non-test classes stay skipped.
3. Move the connectivity question out of JourneyProfile into one shared helper and delete the private journey check.
4. Test the builder decision on device with an injected connectivity answer (online fails loudly, offline defers, helpers skipped).
5. Replace every resolvable fixture host (stream.invalid, example.test, and any others found) with loopback or non-network values; an unreachable stream becomes a refused loopback port.
6. Delete app/src/androidTest/resources/undetected_streams.txt.
7. Run JVM tests, compile and run the instrumented suite offline, and prove a networked run fails loudly.
<!-- SECTION:PLAN:END -->
