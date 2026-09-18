---
id: TASK-031
title: Decide whether the instrumented suite should run under Test Orchestrator
status: To Do
assignee: []
created_date: '2026-09-18 05:19'
labels: []
dependencies:
  - TASK-004.02
type: chore
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-004.02 covers 'clear app data and reconnect' with an in-process reset: every preference file the app owns is emptied by enumerating shared_prefs, the Room API cache is dropped, and CMD_CLEAR_CACHE takes the in-memory cache and the stored images, with the seeded row polled until it disappears so the asynchronous clear is known to have finished. What it cannot do is kill the process, because the service shares it with the instrumentation, so process-held state survives: the ExoPlayer media cache under the external files directory, which the player holds open, and LatestRadioStationStorage's cached station, which is TASK-027.

Three consecutive review rounds asked for a real 'pm clear' instead. The supported way to get one is androidx.test.orchestrator: 'execution ANDROIDX_TEST_ORCHESTRATOR' plus the clearPackageData runner argument, which runs every test in a fresh process against freshly cleared data.

That is a suite-wide decision, not a change one test can make. It would make every test start from a clean profile, which removes a whole class of ordering hazard, but it also starts a process per test, so a suite that currently finishes in under thirty seconds would take substantially longer, and the documented command in AGENTS.md would change. It also does not turn clear-data into something a single test can perform and then assert, so TASK-004.02's in-process reset would stay as it is either way.

Decide and record the outcome; if orchestration is adopted, update AGENTS.md and doc/testing-roadmap.md so the run policy has one copy.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A decision is recorded, with the wall-clock cost of running the current suite both ways measured rather than estimated
- [ ] #2 If adopted, the Gradle configuration, the runner argument and the AGENTS.md commands are updated together
- [ ] #3 If declined, the reason is written down where the next reviewer will find it rather than left in a review thread
<!-- AC:END -->
