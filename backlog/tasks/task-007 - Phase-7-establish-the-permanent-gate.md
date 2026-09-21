---
id: TASK-007
title: 'Phase 7: establish the permanent gate'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-21 17:47'
labels: []
milestone: m-0
dependencies:
  - TASK-006
  - TASK-008
  - TASK-052
  - TASK-053
  - TASK-054
  - TASK-058
  - TASK-059
  - TASK-060
  - TASK-061
type: chore
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The main roadmap restarts only when every item here holds. Run policy: affected JVM tests on every meaningful change, the full JVM suite before commit or handoff, JVM plus offline emulator suites before restarting main roadmap work, and everything plus the DHU and real-car checklist before a personal release.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All JVM tests pass
- [ ] #2 Instrumentation tests compile and pass on the canonical emulator
- [ ] #3 All offline end-to-end journeys pass
- [ ] #4 Critical pure-core coverage is at least 80% line and 70% branch
- [ ] #5 No critical class is considered covered solely because another class happened to execute it
- [ ] #6 Every fixed bug has a regression test at the lowest appropriate layer
- [ ] #7 The suite makes no external network requests
- [ ] #8 No ignored, commented-out, assertion-free or retry-masked tests exist
- [ ] #9 Android Auto manual checks have a recorded result for the current build
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Gate run of 2026-09-20, against 823a513.

Criteria 1 to 3 hold. Full JVM suite forced with --rerun-tasks: 191 tests, 0 failures, 0 skipped (:common 171, :android-jvm-stubs 17, :common-ui 3). Instrumented suite on the API 34 emulator (ro.build.version.sdk 34) with wifi and data off and no prior app data: 207 tests, 0 failures, 0 errors, 0 skipped, including all 28 journey tests (cold launch 3, local station lifecycle 5, favorite lifecycle 5, offline playback 6, settings persistence 4, service-first startup 5).

Criteria 4 and 5 are not measurable yet. Nothing in the repository says which classes are critical pure-core, and no threshold is enforced anywhere, so both criteria are claims no one can re-check. Measured per class from the JaCoCo XML, the phase 2 core is strong (ParserLayerRadioBrowserImpl 100% line, BrowseTree 100%, MediaId 98%, ModelLayerImpl 88%), but UrlLayerRadioBrowserImpl and UrlLayerWebRadioImpl read 0% because their only test is instrumented. TASK-061 fixes that half; the set and the threshold still have to be written down somewhere a build can read them.

Criteria 6, 7 and 8 do not hold. Blockers filed as TASK-052 and TASK-053 (criterion 7), TASK-054 (criterion 8), and TASK-058 to TASK-061 (criterion 6), and this task now depends on them. TASK-055, TASK-056 and TASK-057 came out of the same audit but weaken diagnostics rather than the criteria, so they do not block the gate.

Criterion 9 needs a head unit and is carried by TASK-008, which is now a dependency rather than a loose reference.

Two record corrections the audit turned up and this task did not act on: TASK-027 names the instrumented LatestRadioStationStorageTest as its regression pin, but the defect is a one-field in-memory cache in :common that the JVM test OpenRadioServicePresenterImplTest.clearingDropsEveryCacheAndTheLatestStation covers identically and without a device. TASK-029 still claims its instrumented test asserts both provider nodes; it was narrowed to one.

Behind criterion 6 sits a process cause worth naming: nine defects were repaired inside commits whose subject reads like test work, so they got no task, no acceptance criterion and no decision about where their test belongs. Most landed covered because fix and tests arrived together. The ones that did not are exactly the sub-fixes incidental to whatever the commit was nominally about.
<!-- SECTION:NOTES:END -->
