---
id: TASK-060
title: Cover RadioStationValidatorImpl rules directly
status: To Do
assignee: []
created_date: '2026-09-21 17:46'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 75000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RadioStationValidatorImpl has no test anywhere. Every test that touches validation either injects a RadioStationValidator fake (RecordingValidator in RadioStationManagerLayerImplTest) or drives the whole journey against the real one without asserting its rules. 13cc159 added the guard that stops an empty home page from being probed; reverted, every station added without a home page fires onWarning, which addRadioStation wires to its own onFailure, and LocalStationLifecycleJourneyTest would not see it because it asserts only that the station was stored. The guard short-circuits before the HTTP probe, so a JVM test reaches it with no network. TASK-038 changes how a warning reaches the user at all and wants this coverage in place first. Found while auditing criterion 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An empty home page is not probed and produces no warning
- [ ] #2 A non-empty but unreachable home page produces a warning and not a failure
- [ ] #3 An empty or invalid stream url is reported as a failure before any probe runs
- [ ] #4 The tests run on the JVM and reach no network
<!-- AC:END -->
