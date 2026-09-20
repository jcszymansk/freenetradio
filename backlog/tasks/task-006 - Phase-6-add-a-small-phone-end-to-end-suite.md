---
id: TASK-006
title: 'Phase 6: add a small phone end-to-end suite'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 13:08'
labels: []
milestone: m-0
dependencies:
  - TASK-005
type: chore
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Proves the user-visible system is assembled correctly. Keep the suite deliberately small: parser errors, cache branches and storage boundaries belong in cheaper layers, not in UI tests.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Exit check: all six journeys pass on a clean API 34 emulator with networking disabled, without retries or test-order assumptions
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. The six journeys are already written and merged, one subtask each, so nothing is left to build. What is left is the exit check, and a status that is only flipped rather than verified proves nothing.
2. Read the criterion as three separate claims and check each: a clean API 34 emulator with networking disabled, no retries, no test-order assumptions.
3. Clean API 34 with networking off: confirm ro.build.version.sdk is 34, disable wifi and data, and run the full instrumented suite. Gradle uninstalls both APKs after each run, so every run is already a fresh install.
4. No retries: grep the build files for a retry plugin or maxTestRetries, and the instrumentation sources for @Ignore and Assume, since a skipped test passes without running.
5. No test-order assumptions: run the six journey classes alone, in reversed class order and in forward order, and compare against the full-suite run. That varies both which classes precede them and the order among them.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The six journeys landed one subtask at a time between TASK-006.01 and TASK-006.06; this task adds no code of its own. Closing it is the exit check, so the criterion was read as three claims and each was checked rather than assumed.

Clean API 34 emulator, networking disabled: ro.build.version.sdk is 34, wifi and mobile data off, and every run is a fresh install because Gradle uninstalls both APKs afterwards. JourneyProfile also asserts the device is offline itself, through the same ConnectivityManager question NetworkLayerImpl asks, so a run with networking still on fails rather than quietly testing something else.

No retries: no retry plugin and no maxTestRetries in any build file, and no @Ignore or Assume in app/src/androidTest. The suite reports 0 ignored, which is the other half - a skipped test passes without running.

No test-order assumptions: the six journey classes were run alone in reversed class order and in forward class order, 28 tests each, and as part of the full 207. That varies both which classes run before them and their order among themselves. Every journey asserts the root it starts in for this reason: the presenter is a registry singleton whose node stack outlives the Activity, so a case that walked into a node would otherwise send the next one's Activity back there.

What the suite still cannot do is give each test a new process. The service shares the instrumentation's, so state that outlives a class - the player's queue, the bound station provider, the browse tree - is handed on rather than rebuilt, and the fixtures park and reset it by hand. TASK-031 owns that.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Phase 6 is complete: six offline end-to-end journeys through the real phone UI and OpenRadioService - cold launch, local station lifecycle, favorite lifecycle, offline playback, settings persistence and service-first startup - one subtask each, all merged. The suite is 28 instrumentation tests sharing five helpers (JourneyProfile, JourneyNavigation, JourneyPlayback, JourneyDialogs, BrowseListView, NowPlayingView) and the service fixtures under shared/service.

The exit check was verified rather than assumed, in three parts. Networking disabled on an API 34 emulator (ro.build.version.sdk 34, wifi and data off, fresh install per run): the full instrumented suite passed 207 tests, 0 failures, 0 ignored. No retries: no retry plugin or maxTestRetries in any build file, no @Ignore or Assume anywhere in app/src/androidTest, and 0 ignored reported. No test-order assumptions: the six journey classes passed alone in reversed class order and alone in forward class order, 28 tests each, as well as inside the full run - varying both what precedes them and their order among themselves.

Phase 6 deliberately stops here. Parser errors, cache branches and storage boundaries stay in the cheaper layers, which is why six journeys cover the user-visible system in 28 tests. The one thing the suite cannot do is hand each test a new process, since the service shares the instrumentation's; TASK-031 carries that, and TASK-007 is the permanent gate this unblocks.
<!-- SECTION:FINAL_SUMMARY:END -->
