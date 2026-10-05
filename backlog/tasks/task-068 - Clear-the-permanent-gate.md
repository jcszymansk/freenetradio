---
id: TASK-068
title: Clear the permanent gate
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 20:03'
updated_date: '2026-10-05 07:07'
labels: []
milestone: m-0
dependencies:
  - TASK-007
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
  - TASK-069
  - TASK-070
  - TASK-071
  - TASK-073
  - TASK-076
type: chore
ordinal: 21500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The main roadmap restarts only when all nine criteria below hold at the same moment. TASK-007 built the gate: the membership rule in doc/testing-roadmap.md, the set and its owners in gradle/pure-core-coverage.tsv, verifyPureCoreCoverage and verifyPureCoreAttribution, and an audit that established where every criterion actually stood. This task is the other half, running it, and it is split off because it cannot start until every blocker has landed.

What the audit found, and what each blocker clears, is recorded on TASK-007. Criteria 1 to 3 held on 2026-09-21 and are expected to hold again; they are listed unchecked here because the gate asks for one simultaneous pass, not nine separate moments.

Four pieces of work belong to this task rather than to any blocker:

Re-audit criteria 7 and 8 against the tree as it will then be. This is the large one and should not be budgeted as a final read-through. Thirteen blockers will add and rewrite tests, and criterion 8 has to hold for tests that do not exist yet. The audit behind TASK-052 to TASK-054 read 80 test files.

Re-run every suite in one pass, together with verifyPureCoreCoverage and verifyPureCoreAttribution.

Wire verifyPureCoreCoverage into check once it passes. It is unwired only because it fails today.

Decide whether to close the gate hole recorded in the roadmap: a hand-maintained class list rewards moving untested code out of the set, as TASK-062 demonstrated by accident. Closing it needs a package-scoped rule asking whether an unlisted class in these packages is big enough to deserve a row.

Residue deliberately left unfiled, to be judged here: callWhenSearchReady RESULT_ERROR_BAD_VALUE is unreachable and probably wants deleting rather than testing; RadioStationValidatorImpl and ImagesPersistenceLayerImpl reach the network without consulting the connectivity gate, which TASK-052 neutralises for the suite but not for production; TASK-027 designates the wrong regression test and TASK-029 final summary claims two provider nodes where it asserts one; the test-file hygiene items; and the owner judgement calls still open for MediaItemCommandImpl and Country.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All JVM tests pass
- [x] #2 Instrumentation tests compile and pass on the canonical emulator
- [x] #3 All offline end-to-end journeys pass
- [x] #4 Critical pure-core coverage is at least 80% line and 70% branch
- [x] #5 No critical class is considered covered solely because another class happened to execute it
- [x] #6 Every fixed bug has a regression test at the lowest appropriate layer
- [x] #7 The suite makes no external network requests
- [x] #8 No ignored, commented-out, assertion-free or retry-masked tests exist
- [x] #9 Android Auto manual checks have a recorded result for the current build
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Run JVM, coverage and attribution, and the offline instrumented suite, to see where the tree stands before changing anything.
2. Re-audit criteria 7 and 8 over every test file, and criterion 6 over every production fix since the fork, by reading rather than sampling.
3. Fix what blocks criteria 6 and 8 in test sources only. Production code stays as it was for the Android Auto check of 8ab2329, so criterion 9 keeps referring to the current build.
4. Close the measurement holes in the gate: fold synthetic classes into their outer class, fail on unlisted classes with decisions in watched packages unless excluded with a rule, drop Country, add what the new check finds.
5. Wire verifyPureCoreCoverage into check.
6. File the residue that needs production changes or is hygiene, and correct the records of TASK-027, TASK-029, TASK-055 and TASK-056.
7. Update doc/testing-roadmap.md where the gate's description has changed.
8. Re-run every suite in one pass with both gate tasks, then check the criteria.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Gate run of 2026-10-05 against fc3213f, all criteria in one pass.

Before changing anything the tree already passed every suite and both gate tasks: 216 instrumented tests, 96.5% line and 89.7% branch over 50 classes, 33 owners attributed. The work was in what those numbers could not see.

Criteria 7 and 8 were re-audited by reading every test file in full, 49 JVM and 47 instrumented, not by sampling. No test is ignored, commented out or retried, and no fixture names a host; criterion 7 held as found. Criterion 8 did not: tests that ran assertions but could not fail for the reason they stated. Six restored-instance command tests counted presenter requests before the command's IO coroutine could run, so a command that answered from cache and fetched anyway passed; deleting the early return in all six commands proved it. The command support now owns the scope a command launches in and joins it before counting, which also made assertNoError exact rather than a 250 ms guess (TASK-055 criterion 4) and routes a throw on an IO thread to the test that caused it. InMemoryPreferencesTest and three other JVM helpers handed AbstractStorage a context held only through its WeakReference, so after a collection every write was a no-op and every default assertion passed regardless. On the device, the unsortable-category test seeded one station, which getAll renumbers to 0 whatever happened, and the local id test never compared the first id with the initial value its name promises. Smaller ones fixed alongside: category counts never asserted, a nested playlist test that did not check the fetch, a journey row lookup that acted on every matching row, vacuous assertions over stores nothing had seeded, and three cross-class leaks (the in-memory API cache, parallel storage instances, an image permission prompt).

Criterion 6 was re-audited over every production commit since the fork, 35 fixed defects traced to a test that fails on revert. One had none: XSPFPlaylistParser stopped reading external DTDs in 6a83887 and reverting it failed nothing. XSPFPlaylistParserTest now fails on the revert. TASK-080's limit is pinned only by an instrumented loopback test; that placement is TASK-089, since moving it needs a production seam.

The gate itself had two holes, both closed. JaCoCo reports lambdas and launch blocks as Outer$... classes and the gate dropped them, so about 360 lines inside listed classes were never measured; they are now folded into their outer class. The hand-maintained list rewarded moving untested code into an unlisted class, as TASK-062 showed; every package holding a listed class is now watched, and a class there with a branch or 20 lines fails the gate until it is listed or excluded with the rule that keeps it out. 29 exclusions are recorded, each naming its rule. The check found four classes that belonged: RadioStationKt, owned by RadioStationTest, and PlaybackState, PlayerUtils and MediaItemHelper, which had no test at all and now have their own. Country left the set under rule 2. MediaItemCommandImpl is owned by a MediaItemCommandImplTest written for its three-way decision rather than one command test picked among twelve. verifyPureCoreCoverage is wired into a root check task; ./gradlew check still fails on lint errors that predate the roadmap, filed as TASK-093.

The full suite turned up a flaky journey: LocalStationLifecycleJourneyTest failed once in the full run and once in three solo runs. Logcat showed a production race that master has too. A locals refresh that arrives after the user walked back renders over the root. The journey now waits for the refresh to render before walking back, 10 of 10 solo runs clean, and the race is TASK-095.

Production code is unchanged since 8ab2329, the build TASK-008 recorded Android Auto results for: git diff 8ab2329 HEAD over src/main, the module build files, constants.gradle and version.properties is empty. Every change here is in tests, the gate script, docs and the tracker. That is why the residue needing production changes was filed rather than fixed.

Residue judged: callWhenSearchReady's bad-value branch is unreachable and becomes TASK-087; the validator and artwork paths that skip the connectivity gate become TASK-088; the mirror resolver test that would reach DNS if the prefix check regressed becomes TASK-090; the test-file hygiene items, now including 21 missing Apache headers, become TASK-091. The TASK-027 and TASK-029 records are corrected in notes on those tasks. New weak assertions found by the audit joined TASK-055 and the incomplete onActivity inventory joined TASK-056. Defects found while writing tests became TASK-092 (categories with equal station counts merge in a TreeSet) and TASK-094 (description fallback treats null and empty differently), and the end-of-list identity comparison was added to TASK-078.

Final pass at fc3213f: ./gradlew test --rerun-tasks, 399 tests per variant (:common 369, :common-ui 7, :android-jvm-stubs 23), 0 failures, 0 skipped. verifyPureCoreCoverage, 53 listed classes, 96.1% line (1727/1797), 85.7% branch (816/952), thinnest CoroutineTimerTask at 80.0%. verifyPureCoreAttribution, all 37 owners carry their classes alone. Instrumented on the API 34 emulator with wifi and data off, no active network and the app cleared: 217 tests, 0 failures, 0 skipped, including all 28 journey tests.

Review round 1: two MediaItemHelperTest cases only invoked the call they named, which criterion 8 rules out; they now assert the call raises nothing. A scan of every @Test body across the four test source sets for a missing assertion finds nothing else. AGENTS.md now states the thresholds and the watched-package boundary of the unlisted-class check, and the roadmap describes both row kinds of the class list.

Review round 2: the command test listener and presenter fake counted with ++ on plain or volatile fields, which loses an increment when two IO coroutines report at once, the very duplicate the single-result assertions exist to catch. Their counters are now AtomicInteger and their request records synchronized lists.

Review round 3: folding every Outer$... class into the top-level class also swallowed nested classes declared in the source, so a decision-bearing nested class under an excluded outer, such as HTTPDownloaderImpl$BytesDownloader, escaped the unlisted-class check. The gate now folds only compiler-generated classes, each into its nearest declared enclosing class, and rejects a row that names a generated class. The rule was checked against all 257 $ classes in both reports. MediaStream$Variant joined the set, owned by RadioStationTest, and nine nested listener and callback classes are excluded under rule 3. Gate after a fresh run: 54 listed, 38 excluded, 96.1% line (1722/1792), 85.7% branch (816/952); attribution passes for all 37 owners.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The gate is clear: all nine criteria held in one pass at fc3213f. 399 JVM tests per variant and 217 instrumented tests, including all 28 journeys, with 0 failures and 0 skipped, on an API 34 emulator with networking off and the app cleared. verifyPureCoreCoverage reads 96.1% line and 85.7% branch over 53 classes, and verifyPureCoreAttribution confirms all 37 owners alone. Android Auto results recorded for 8ab2329 still describe the current build, because no production or build-config file has changed since.

Reaching that took more than re-running. The audit read all 96 test files and found tests that asserted but could not fail for their stated reason: race-decided cache counters in six command tests, storages whose context a GC could drop, single-station sort checks that renumbering always satisfies. Each now fails on the defect it names, proved by simulating that defect. One fixed defect, the XSPF external DTD, had no regression test and now has one. The gate also measured less than it claimed. Coroutine and lambda bodies were dropped from the report, and moving code out of the list went unseen. Both holes are closed: synthetic classes fold into their outer class, and watched packages must list or exclude every class with a decision, which brought four classes into the set with tests of their own. The gate now runs under ./gradlew check, which still fails on lint (TASK-093).

Criterion 6 is checked on one judgement: TASK-080's read limit has its regression test on a device rather than the JVM, because a JVM test needs a production seam this run could not add. That move is TASK-089. Everything else that needs production code is filed (TASK-087, 088, 090, 092, 094, 095), and the test hygiene is TASK-091.

Review round 3 separated declared nested classes from compiler-generated ones, so the set now holds 54 classes at 96.1% line and 85.7% branch.
<!-- SECTION:FINAL_SUMMARY:END -->
