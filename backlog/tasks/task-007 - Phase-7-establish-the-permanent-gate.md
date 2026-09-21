---
id: TASK-007
title: 'Phase 7: establish the permanent gate'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-21 19:59'
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
  - TASK-063
  - TASK-064
  - TASK-065
  - TASK-066
  - TASK-067
type: chore
ordinal: 21000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The main roadmap restarts only when every item here holds. Run policy: affected JVM tests on every meaningful change, the full JVM suite before commit or handoff, JVM plus offline emulator suites before restarting main roadmap work, and everything plus the DHU and real-car checklist before a personal release.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All JVM tests pass
- [x] #2 Instrumentation tests compile and pass on the canonical emulator
- [x] #3 All offline end-to-end journeys pass
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

Pure-core set defined and written down on 2026-09-21. The rule lives in doc/testing-roadmap.md under Phase 7; the set itself lives in gradle/pure-core-coverage.tsv, one row per class with the JVM test that owns it, so each fact has one home. verifyPureCoreCoverage and verifyPureCoreAttribution read that file.

Membership is five tests: production source in :common or :common-ui, decides something, behaviour follows from inputs rather than a platform service, honestly reachable on the JVM (a class built on ContextWrapper(null) does not count, since returnDefaultValues answers for it), and not UI. Dependencies do not join transitively: ModelLayerImpl is in, the DownloaderLayer it calls is not, because the downloader behaviour is OkHttps. Thresholds are 80% line and 70% branch across the set, a 60% line floor per class, and no listed class missing from the report.

First run over 49 classes: 83.7% line, 77.2% branch, failing on four classes with no owning test (StorageManagerLayerImpl, both UrlLayer implementations, SortUtils) and seven below the floor (those four plus RadioStationToAdd 33.3%, ASXPlaylistParser 52.6%, RadioStationManagerLayerImpl 55.6%). The failure is the gate working. TASK-061 clears the two UrlLayer rows; StorageManagerLayerImpl is the same finding one module over, its twelve tests being instrumented although the class imports nothing from Android; SortUtils has no test anywhere and one production caller.

Attribution is checked by re-running each owner test class alone, 26 isolated Gradle invocations, about two minutes. JaCoCo merges every session into one set of probes, so a merged report cannot attribute a line to a test and the exec file keeps session ids but not per-session probes; an isolated re-run is the only honest mechanism available. It fails on exactly the three classes already below the floor, at identical percentages, which says those three take all their coverage from their owner with no incidental top-up.

Neither task is wired into check, deliberately, because verifyPureCoreCoverage fails today.

Two things the first run exposed. The list can be gamed: TASK-062 split getConnectionUrl out and carried 30 uncovered lines of mirror lookup into the unlisted DnsMirrorUrlResolver, lifting the aggregate three points. That move is correct under rule 3, but the shape is not, and it is recorded as a known limit in the roadmap. And the owner of JsonUtils is EqualizerSerializationTest, measured rather than guessed (36/49 lines alone, against 10 and 8 for the other candidates), which satisfies the mechanical rule but not criterion 5: JsonUtils is covered precisely because serializers executed it. Left as it stands pending a decision, since setting it to NONE makes the gate demand a JsonUtilsTest that does not exist.

Criteria 1 to 3 are checked on the 2026-09-21 run, re-verified after TASK-062 merged: 196 JVM tests and 207 instrumented tests including all 28 journeys, 0 failures and 0 skipped in both. They are point-in-time by nature and get re-run in one pass when the remaining blockers clear, because the gate asks that every criterion hold at the same moment, not at some moment each.

Criterion 4 is left unchecked although the aggregate passes at 83.7% line and 77.2% branch. Read literally it holds; read as what it is for, it does not, because the same task enforces the per-class floor and the owner column and fails on both. Checking it while ./gradlew verifyPureCoreCoverage exits non-zero would put a green box next to a red command.

JsonUtils owner set to NONE. The mechanical rule qualified EqualizerSerializationTest, which covers 36 of its 49 lines alone, but that is the shape criterion 5 exists to reject: JsonUtils is covered because serializers executed it, not because anything tests it. The gate now reports five classes with no owning test rather than four, and JsonUtils is the one of the five that is above the per-class floor, at 81.6% line. That is the owner column earning its place, since no percentage would have flagged it.

Three of the five unowned classes have no task yet: JsonUtils, SortUtils (no test anywhere, one production caller, station reordering) and StorageManagerLayerImpl (twelve instrumented tests although the class imports nothing from Android). TASK-061 carries the other two.

What stays on this task once every blocker has landed, recorded so it is not rediscovered late.

1. Re-audit criteria 7 and 8 against the tree as it will then be. This is the large one. Fourteen blockers will add and rewrite tests, and those tests are themselves unaudited; criterion 8 says no ignored, commented-out, assertion-free or retry-masked test exists, which has to be true of the new ones too. The audit that produced TASK-052 to TASK-054 read 80 test files and was a snapshot. Budget it as work in its own right, not as a final read-through.

2. Re-run every suite in one pass. Criteria 1 to 3 are checked against the 2026-09-21 run. The gate asks that all nine hold at the same moment, not at some moment each, so the checks get cleared and redone together with verifyPureCoreCoverage and verifyPureCoreAttribution.

3. Wire verifyPureCoreCoverage into check once it passes. It is deliberately unwired while it fails, and leaving it unwired after it passes would waste it.

4. The gameability limit stays recorded rather than fixed: a hand-maintained list rewards moving untested code out of the set, as TASK-062 demonstrated by accident. Closing it needs a package-scoped rule asking whether an unlisted class in these packages is big enough to deserve a row. Decide then whether it is worth building.

Residue that is now filed: ASXPlaylistParser and the ENTRYREF network hazard as TASK-066, the children-changed item count as TASK-067, and the two below-floor classes as a fourth criterion on TASK-059.

Residue still unfiled and deliberately so: callWhenSearchReady RESULT_ERROR_BAD_VALUE is unreachable and probably wants deleting rather than testing; RadioStationValidatorImpl and ImagesPersistenceLayerImpl reach the network without consulting the connectivity gate, which TASK-052 neutralises for the suite but not for production; TASK-027 designates the wrong regression test and TASK-029 final summary claims two provider nodes where it asserts one; and the test-file hygiene items, two missing Apache headers, two missing trailing newlines, dead blank lines in ModelLayerImplTest, the unreachable NowPlayingView.assertStaysDown and BrowseListView.inRow clicking every matching row without a break.

Owner judgement calls left open in gradle/pure-core-coverage.tsv: MediaItemCommandImpl, arbitrary among twelve qualifying command tests, and Country, a three-line data class owned by ParserLayerMappingTest.
<!-- SECTION:NOTES:END -->
