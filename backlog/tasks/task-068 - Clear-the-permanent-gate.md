---
id: TASK-068
title: Clear the permanent gate
status: To Do
assignee: []
created_date: '2026-09-21 20:03'
updated_date: '2026-09-22 09:36'
labels: []
milestone: m-0
dependencies:
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
