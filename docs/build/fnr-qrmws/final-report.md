---
schema: gc.build.final-report.v1
workflow:
  id: fnr-qrmws
  formula: superpowers-lite-build
methodology:
  pack: superpowers
  name: superpowers-lite-build
producer:
  formula: superpowers-lite-build
  stage: finalize
  attempt: 1
status: approved
trace:
  upstream:
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/requirements.md
      hash: sha256:586f44666567b80faf9967aa55ea723c1d7b6fbabfc83886337ff97461e62c96
      ids: [REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-015, REQ-016, REQ-017, REQ-018, REQ-019, REQ-020, REQ-021, REQ-022]
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/brainstorming/design-candidate.md
      hash: sha256:37074054bd4a36ebc79b7d5ca4287d6ac36a887d95f67aa6954c4fe888f2a66c
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/implementation-plan.md
      hash: sha256:1d45881a99c201e72e84d6013ed067a2ed2a94e9e9f0354821d04dbd428a80e0
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/decomposition.md
      hash: sha256:8fa3104173784c1cbe6ae8929cb0422d15cc8b2da649bea9ae8c08089e0daef8
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/implementation-summary.md
      hash: sha256:fedfa2eb98b5a3af1b2c92b69bc5318c428bc2bf83a0918bb1f3446e5a17232d
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/review-report.md
      hash: sha256:2f8af834279eede2d3694fcc72a55f8338c02e2ae77efcb7090400aab9b7fa85
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/gap-analysis-report.md
      hash: sha256:52efae74ccc17c30a8c27749cdc7cafadb031a8d1772c1b46d90238574b8d0da
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/review-fix-summary.md
      hash: sha256:e9d2a8aa3886590d0a219eeee2d39021d39f20d2f07403b5b0913995c0b35c64
    - path: git/fnr-settings-dialogs
      hash: git:c28ac396e644800388a993bfe2f01de01969c522
  coverage:
    - id: REQ-001
      status: covered
    - id: REQ-002
      status: covered
    - id: REQ-003
      status: covered
    - id: REQ-004
      status: covered
    - id: REQ-005
      status: covered
    - id: REQ-006
      status: covered
    - id: REQ-007
      status: covered
    - id: REQ-008
      status: covered
    - id: REQ-009
      status: covered
    - id: REQ-010
      status: covered
    - id: REQ-011
      status: covered
    - id: REQ-012
      status: covered
    - id: REQ-013
      status: covered
    - id: REQ-014
      status: covered
    - id: REQ-015
      status: covered
    - id: REQ-016
      status: covered
    - id: REQ-017
      status: covered
    - id: REQ-018
      status: covered
    - id: REQ-019
      status: covered
    - id: REQ-020
      status: covered
    - id: REQ-021
      status: covered
    - id: REQ-022
      status: deferred
      rationale: "JVM tests, Roborazzi, coverage gate, assemble and lint pass at c28ac39 (re-run by this lane with --rerun-tasks); the instrumented suite on an offline cleared emulator and the owner's baseline review (AC-9) need a device no lane may use (ANDROID_SERIAL=none). Tracked as human-only bead fnr-xuhfx, which must close before the merge."
---

# Settings in Compose, stage 5 build A: final report

## Summary

Workflow `fnr-qrmws` (`superpowers-lite-build`) is stage 5 build A. It replaces the seven
View-based settings dialogs and the battery-optimisation dialog with a Compose Settings screen.
The screen has its own `ScreenStack` of `Destination` values, hosted beside `MainScreenModel`.
Its sub-screens are Choice, Master volume, Stream buffering, User agent, Diagnostics and About.
Station export and import run through activity result launchers. The old dialogs, their layouts,
`CountriesArrayAdapter`/`BaseArrayAdapter` and the resources nothing references any more are
deleted. `BaseDialogFragment`, `UiUtils.clearDialogs`, `MainActivity.showDialog` and
`JourneyDialogs` stay in the tree for build D, as REQ-020 requires.

The work is on the local integration branch `fnr/settings-dialogs`: 22 commits on top of `master`
at `90782c3`, head `c28ac396e644800388a993bfe2f01de01969c522`, 130 files changed (+6383 / -2620).
`master` is an ancestor of the head, so the branch merges cleanly. Every commit carries a `Bead:`
trailer.

The owner approved the requirements, design and spec gates. The plan and the plan review were
approved. All 15 tasks in implementation convoy `fnr-awbgr` closed with `gc.outcome=pass`, and the
convoy autoclosed. The whole-branch review closed `done` on attempt 2: the implementation review
and the gap analysis both approve `c28ac39`, and no required finding is open. Attempt 1's only
required finding, GAP-1 (the instrumented suite has never run), was handed to the owner as
`fnr-xuhfx`. Both attempt-2 lanes checked that handoff.

Nothing was pushed and no PR was opened (`push=false`, `open_pr=false`). Under the repository's
rule only the owner merges an integration branch into `master` with `--no-ff`, so this lane left
the branch as it is.

## Outcome

**Pass. The branch is ready for the owner's review. Do not merge it until `fnr-xuhfx` closes.**

| Bead | Labels | What it needs | Requirement |
| --- | --- | --- | --- |
| `fnr-xuhfx` | `human-only`, `needs-human`, `review-followup` | (1) Run `:app:connectedDebugAndroidTest` on an offline, cleared emulator. (2) Check the 36 new or changed Roborazzi baselines by eye in dark and light: 34 added `settings_*`, 2 modified `main_overflow_open_*`. (3) Confirm two plan decisions: REQ-014 (the service's existing `clear_completed` toast is the only "cache cleared" message) and REQ-001 (`SettingsModel.clearCache` empties the screen stack) | REQ-022 (AC-9), REQ-014, REQ-001 |
| `fnr-x3yhm` | `review-followup` | Review the task 13 `LocationService` change: the country list is now built from `COUNTRY_CODE_TO_NAME`, which drops four duplicate codes that crashed the default-country list. The owner accepted it as a scope exception on 2026-11-28. Does not block the merge | - |

This lane re-ran the verification itself at `c28ac39` in the harness worktree
`gcexp/.gc/worktrees/freenetradio/fnr-cq0dz`. The worktree was clean, `console-design` was at
`347ed51`, the JDK was 17.0.21 and `ANDROID_HOME=$HOME/Android/Sdk`. A first run without
`--rerun-tasks` passed, but 229 of its 234 tasks were up to date, so it proves little. This lane
then forced every task to execute:

```sh
./gradlew --no-daemon --offline --rerun-tasks test assembleDebug :app:assembleDebugAndroidTest check
```

Result: `BUILD SUCCESSFUL in 4m 8s`, 234 of 234 actionable tasks executed, exit 0. The JUnit XML
adds up to 1096 JVM tests (`:common` 706, `:common-ui` 260, `:app` 108, `:android-jvm-stubs` 22),
with 0 failures, 0 errors and 0 skipped. `check` also ran `:app:verifyRoborazziDebug`,
`verifyPureCoreCoverage` and lint for every module, and all of them passed.

`:app:connectedDebugAndroidTest` was not run. `ANDROID_SERIAL=none`, so no lane may use a device.
An emulator (`emulator-5556`) is attached, and this lane did not touch it.
`verifyPureCoreAttribution` was not re-run by this lane either.

Earlier evidence at the same head: the gap-analysis lane, attempt 1, ran
`./gradlew --no-daemon test assembleDebug :app:assembleDebugAndroidTest check` fresh (234 tasks,
BUILD SUCCESSFUL). The implementation review, attempt 2, ran `./gradlew --no-daemon test check`
(BUILD SUCCESSFUL).

## Artifacts

All paths are under `/home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/`:

| Artifact | File | Schema | Status |
| --- | --- | --- | --- |
| Requirements | `requirements.md` | `gc.build.requirements.v1` | approved |
| Design | `brainstorming/design-candidate.md`, gates `brainstorming/approval-summary.md`, `brainstorming/spec-approval-summary.md` | - | approved by the owner |
| Plan | `implementation-plan.md` | `gc.build.plan.v1` | approved |
| Plan review | `plan-review-report.md`, `plan-review-apply-summary.md` | - | approved |
| Decomposition | `decomposition.md` | `gc.build.decomposition.v1` | approved |
| Implementation summary | `implementation-summary.md` | `gc.build.implementation-summary.v1` | validates, kept unchanged |
| Review report | `review-report.md` (identical to `implementation-review-report.md`) | `gc.build.review.v1` | approved |
| Gap analysis | `gap-analysis-report.md` | `gc.build.review.v1` | approved |
| Review fix summary | `review-fix-summary.md` | `gc.build.review.v1` | approved, no-op |
| Final report | `factory-run.md` (this file) | `gc.build.final-report.v1` | approved |

Code: branch `fnr/settings-dialogs` at `c28ac396e644800388a993bfe2f01de01969c522` in
`/home/jsz/Devel/jacekszymanski/FreeNetRadio`.

The implementation summary predates the review and marks REQ-022 `covered`. The review fix
summary marks REQ-022 `deferred`, because its device half has not run. The coverage table below
follows the review. The implementation summary's front matter names `methodology.pack:
superpowers-lite`, while this step's contract says `superpowers`. The summary validates, and this
lane's instructions say to keep a valid summary, so it was left as it is.

## Remaining Risks

- **The instrumented suite has never run against this branch.** These classes are new or
  rewritten and have only been compiled: `SettingsNavigationJourneyTest`,
  `StationTransferJourneyTest`, `SettingsPersistenceJourneyTest`, `CountryChangeJourneyTest`,
  `DiagnosticReportJourneyTest`, `SettingsAdaptersTest` and `FileStoreManagerTest`.
  `BrowseStacksRule`, `LocalStationLifecycleJourneyTest` and `OpenRadioServiceBrowseTest` were
  changed as well. Merging before `fnr-xuhfx` closes risks failures that only show up on a device. This
  is the main blocker before the merge.
- **Visual conformance has not had the owner's check.** Nothing compares the baselines with the
  reference PNGs automatically. The owner reviews them under `fnr-xuhfx` item 2. The "28" in
  `code-review-context.md` is wrong: the count is 36.
- **Two plan decisions are waiting for the owner** (REQ-014 toast, REQ-001 clear empties the
  stack): `fnr-xuhfx` item 3.
- **Scope exception:** the `LocationService` country-list change (task 13), tracked by
  `fnr-x3yhm`.
- **Minor review findings are not fixed:**
  - `disabledLook` dims controls without disabling them. The real fix is an `enabled` parameter
    in console-design.
  - `SettingsModel` refreshes on `Dispatchers.Main`, so a row can show a stale value for one
    frame after a pop.
  - The Default country row shows `USE LOCATION` in capitals. This is unchanged behaviour.
  - `parseBuffer` accepts any Unicode digit.
- **The leftover dialog-support code is intended.** `BaseDialogFragment`, `UiUtils.clearDialogs`,
  `MainActivity.showDialog` and `JourneyDialogs` stay for builds B, C and D (`fnr-hkpbe`,
  `fnr-uo578`, `fnr-baige`; REQ-021). The deletion is not incomplete.
- **The lasting documents are not on the branch yet.** This lane committed nothing. If the
  workflow's later `commit-docs` step runs, it moves the branch head past `c28ac39`.
- **Leftover worktrees.** The harness's detached worktrees for all 15 task beads remain under
  `gcexp/.gc/worktrees/freenetradio/`. This lane only built in `fnr-cq0dz` and removed nothing.

## Coverage

| ID | Status |
| --- | --- |
| REQ-001 | covered |
| REQ-002 | covered |
| REQ-003 | covered |
| REQ-004 | covered |
| REQ-005 | covered |
| REQ-006 | covered |
| REQ-007 | covered |
| REQ-008 | covered |
| REQ-009 | covered |
| REQ-010 | covered |
| REQ-011 | covered |
| REQ-012 | covered |
| REQ-013 | covered |
| REQ-014 | covered |
| REQ-015 | covered |
| REQ-016 | covered |
| REQ-017 | covered |
| REQ-018 | covered |
| REQ-019 | covered |
| REQ-020 | covered |
| REQ-021 | covered |
| REQ-022 | deferred |
