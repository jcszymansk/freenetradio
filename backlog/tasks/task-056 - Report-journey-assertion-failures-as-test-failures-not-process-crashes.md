---
id: TASK-056
title: 'Report journey assertion failures as test failures, not process crashes'
status: To Do
assignee: []
created_date: '2026-09-21 17:45'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 71000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ActivityScenario.onActivity called off the main thread runs the block through Instrumentation.runOnMainSync with no try/catch, and the inner lambda rethrows, as the resolved androidx.test.core 1.5.0 bytecode confirms. An AssertionError raised inside the block escapes on the main looper: the app process dies while the test thread is still blocked in SyncRunnable.waitForComplete, so a single failing case is reported as a process crash and the rest of the run is aborted. Eleven blocks assert inside onActivity, in ColdLaunchJourneyTest, LocalStationLifecycleJourneyTest (five), SettingsPersistenceJourneyTest (three) and AddStationDialogTest. The run does go red, so nothing is masked, but for a gate whose job is to say what broke this is the worst available diagnostic. Found while auditing criterion 8 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An assertion that today runs inside onActivity fails the test that owns it and names the case
- [ ] #2 A single failing journey case does not abort the remaining instrumented classes
- [ ] #3 Values needed for an assertion are read on the main thread and asserted on the test thread
<!-- AC:END -->
