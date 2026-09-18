---
id: TASK-005.01
title: Add the minimum testability seams for playback
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 19:16'
labels: []
milestone: m-0
dependencies:
  - TASK-004
parent_task_id: TASK-005
type: enhancement
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Production seams needed before playback can be tested offline. Keep them minimal and reuse existing interfaces; do not introduce a mocking or DI framework.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Player error classification in OpenRadioPlayer is injectable or isolated
- [ ] #2 RadioStationValidator is injected into RadioStationManagerLayerImpl so mutation tests do not probe the internet
- [ ] #3 No mocking or dependency-injection framework added
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the playback error classification out of OpenRadioPlayer.ComponentListener into a PlaybackErrorClassifier that owns the error budget and returns a PlaybackErrorDecision; the player keeps only the side effects and the decision-to-string mapping.
2. Cover the classifier with JVM tests built from real PlaybackException instances, no network and no ExoPlayer.
3. Turn RadioStationValidator into an interface with RadioStationValidatorImpl behind it, drop its dead ModelLayer and UrlLayer parameters, and inject the validator into RadioStationManagerLayerImpl from DependencyRegistryCommon.
4. Verify: ./gradlew test, assembleDebug, :app:assembleDebugAndroidTest.
<!-- SECTION:PLAN:END -->
