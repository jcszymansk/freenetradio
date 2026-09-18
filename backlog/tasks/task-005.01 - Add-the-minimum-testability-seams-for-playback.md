---
id: TASK-005.01
title: Add the minimum testability seams for playback
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 19:20'
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
- [x] #1 Player error classification in OpenRadioPlayer is injectable or isolated
- [x] #2 RadioStationValidator is injected into RadioStationManagerLayerImpl so mutation tests do not probe the internet
- [x] #3 No mocking or dependency-injection framework added
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the playback error classification out of OpenRadioPlayer.ComponentListener into a PlaybackErrorClassifier that owns the error budget and returns a PlaybackErrorDecision; the player keeps only the side effects and the decision-to-string mapping.
2. Cover the classifier with JVM tests built from real PlaybackException instances, no network and no ExoPlayer.
3. Turn RadioStationValidator into an interface with RadioStationValidatorImpl behind it, drop its dead ModelLayer and UrlLayer parameters, and inject the validator into RadioStationManagerLayerImpl from DependencyRegistryCommon.
4. Verify: ./gradlew test, assembleDebug, :app:assembleDebugAndroidTest.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Seam 1 is isolation, not constructor injection: PlaybackErrorClassifier is built by OpenRadioPlayer itself. The player cannot be constructed without a real ExoPlayer, so passing the classifier in from OpenRadioService would move a player detail into the service without making anything more testable. The AC allows either.

The extraction also removed a duplicate branch. onPlayerError and toDisplayString each re-tested the error code and the cause; classification now happens once and the player maps the decision to a string resource.

The upstream retry TODO in the tolerated-error branch is gone from the code and now lives in task-039, per the AGENTS.md rule that an unacted intention becomes a task rather than a comment.

RadioStationValidator lost its ModelLayer and UrlLayer constructor parameters, which nothing read, and RadioStationManagerLayerImpl lost the two it only forwarded.

Verification: ./gradlew test (all JVM suites pass, 10 new PlaybackErrorClassifierTest cases and 4 new RadioStationManagerLayerImplTest cases), ./gradlew assembleDebug, ./gradlew :app:assembleDebugAndroidTest. No dependency was added: git diff over *.gradle is empty.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Playback error classification moved out of OpenRadioPlayer.ComponentListener into PlaybackErrorClassifier, which owns the error budget and returns a PlaybackErrorDecision; the player keeps the side effects and maps the decision to a string resource. RadioStationValidator became an interface with RadioStationValidatorImpl behind it, injected into RadioStationManagerLayerImpl from DependencyRegistryCommon, so adding a station no longer forces a real HTTP probe. No mocking or DI framework was added and no dependency changed.

Verified with ./gradlew test (PlaybackErrorClassifierTest 10 cases built from real PlaybackException instances, RadioStationManagerLayerImplTest 4 cases driven by a recording validator, whole JVM suite green), ./gradlew assembleDebug and ./gradlew :app:assembleDebugAndroidTest.
<!-- SECTION:FINAL_SUMMARY:END -->
