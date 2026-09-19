---
id: TASK-031
title: Decide whether the instrumented suite should run under Test Orchestrator
status: To Do
assignee: []
created_date: '2026-09-18 05:19'
updated_date: '2026-09-19 17:53'
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

## Comments

<!-- COMMENTS:BEGIN -->
author: @claude
created: 2026-09-19 17:38
---
Met again while writing TASK-006.01. ColdLaunchJourneyTest clears the whole profile in @Before, but the active source is the one thing that clear cannot reach: DependencyRegistryCommon.init runs in MainAppCommon.onCreate, before any @Before, and binds the URL layer, the parser and the root command from whatever SourcePreferences held then.

Reproduced on emulator-5554 rather than argued: writing ActiveSrcIdx=1 through run-as and force-stopping the app makes the journey stop in setUp with 'This process bound a provider other than Radio Browser', root offering only [__ALL_CATEGORIES__, __COUNTRIES_LIST__, __COUNTRY_STATIONS__]. The failing run empties the preference itself, because the clear precedes the guard, so the next run binds Radio Browser and passes untouched.

So the exposure is a loud, self-healing failure rather than a wrong pass, and the journey handles it the same way the service suites do. A fresh process per test would remove the guard entirely, which is what this task decides.
---

author: @claude
created: 2026-09-19 17:53
---
Sharper evidence from TASK-006.01, replacing my earlier comment's estimate of the exposure.

Measured, not argued:
1. The binding happens before any test hook exists. ImagesProvider.onCreate calls DependencyRegistryCommon.init, and Android creates content providers before the Application and before the instrumentation (AOSP handleBindApplication runs installContentProviders, then Instrumentation.onCreate, then callApplicationOnCreate). A probe in a custom AndroidJUnitRunner confirmed the registry was already initialized at callApplicationOnCreate. So a custom runner cannot pre-empt it; I tried one and reverted it.
2. The documented Gradle command does reach a dirty profile. connectedAndroidTest installs over what is on the device and keeps its data, and only uninstalls afterwards. With ActiveSrcIdx=1 seeded, ./gradlew :app:connectedDebugAndroidTest failed all three journey cases.
3. Clearing the app's data before the run fixes it completely. Same seeded profile plus adb shell pm clear, then the same command: 3 tests pass.

AGENTS.md now carries that clear step with the reason. That is per-run hygiene and does not settle this task, which is about a fresh process per test; it does mean the documented path is no longer exposed, so the cost side of the decision is the remaining argument.
---
<!-- COMMENTS:END -->
