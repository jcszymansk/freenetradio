---
schema: gc.build.final-report.v1
workflow:
  id: fnr-8i7gz
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
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/requirements.md
      hash: sha256:64f944db0c775a9c3b928fdcdaeff32adab652429d1d19a0ae7253e6e366b5c4
      ids: [REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-015, REQ-016, REQ-017, REQ-018, REQ-019, REQ-020]
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/brainstorming/design-candidate.md
      hash: sha256:7fece371060ea41fe195b912ae380cde2f5e37373e20ecb38941e61bfc2a7869
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/implementation-plan.md
      hash: sha256:b7dd18f70845643ec90014605e9d0ac13d99d1b2d014d595285874d1c597262a
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/decomposition.md
      hash: sha256:1cf6d6fa486726ef5db7c2d98c85782dd9ccd5d38e2ef5899813c9f7ae290047
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/implementation-summary.md
      hash: sha256:8b0e61475acf3a3502bcfa734249e1053b057ba293dcdf3b4114fe13026f89b9
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/review-report.md
      hash: sha256:de0cda652ebcc6f6645861cc14176f7023ecfcb376169527404c54d420de5070
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/gap-analysis-report.md
      hash: sha256:9b41681b32acecc682abeb6868821db8527d65d3f55fb9c3d8dd4df76d8686ab
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/review-fix-summary.md
      hash: sha256:028a569347dad06d34dd1450d3ab44299a823d2d76605acdced14a60d0f4d1be
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
      status: blocked
      rationale: "JVM and Roborazzi halves pass at a006c1a (re-run by this lane); the instrumented journeys compile but have never run on a device. Owner run tracked as human-only bead fnr-ugbkm."
    - id: REQ-020
      status: blocked
      rationale: "./gradlew check passes at a006c1a (re-run by this lane); ./gradlew :app:connectedDebugAndroidTest needs an offline, cleared emulator, which no lane had. Tracked as human-only bead fnr-ugbkm."
---

# Now playing build: final report

## Summary

Workflow `fnr-8i7gz` (`superpowers-lite-build`, source bead `fnr-qdkv7`, "Add a Now playing screen
opened from the mini player") adds a Compose Now playing screen and a Station details sub-screen,
both opened from the mini player. Playback state moves out of `MainScreenModel` into a new shared
`NowPlayingTracker`. The overflow menu adds copy and share of the stream URL, the sleep timer and the
equalizer. The only service-side change is a set of additive media item extras (`KEY_HOME_PAGE`,
`KEY_STREAM_URL`, `KEY_COUNTRY_NAME`).

The work is on the local integration branch `fnr/now-playing`: 9 commits on top of `master` at
`612caf5`, head `a006c1a1db182f9355b28f4cb9bba436f09e52f9`, 72 files changed (+3102 / -410).
Requirements, design, spec, plan and plan review were approved. The owner approved the design and
spec gates. All nine tasks in implementation convoy `fnr-unupc` closed with an approving task review.
The whole-branch review closed `done` on attempt 1: the implementation review (`fnr-8y0d1`) and the
gap analysis (`fnr-4vxm2`) both approved `a006c1a`, and no required finding is open.

Nothing was pushed and no PR was opened (`push=false`, `open_pr=false`). `master` has not moved.
Under the repository's rule only the owner merges an integration branch into `master` with `--no-ff`,
so this lane left the branch as it is.

## Outcome

**Pass. The branch is ready for the owner's review, but it should not be merged until `fnr-ugbkm`
closes.**

| Bead | Label | What it needs | Requirement |
| --- | --- | --- | --- |
| `fnr-ugbkm` | `human-only` | Run `:app:connectedDebugAndroidTest` on a cleared, offline emulator. Compare the 20 `now_playing_*` Roborazzi baselines with `console-design/design-reference/png/` | REQ-019, REQ-020, REQ-017 |
| `fnr-vrntr` | `needs-human` | Name the down chevron for the player screen in console-design `DESIGN.md`. Done in the console-design repository, does not block the merge | REQ-017 |

This lane re-ran the verification itself at `a006c1a` in the harness worktree
`gcexp/.gc/worktrees/freenetradio/fnr-1jekp` (clean, `console-design` at `347ed51`, JDK 17.0.21,
`ANDROID_HOME=$HOME/Android/Sdk`). It forced every task to execute:

```sh
./gradlew --offline --rerun-tasks test assembleDebug :app:assembleDebugAndroidTest check
```

Result: `BUILD SUCCESSFUL in 3m 26s`, 234 of 234 actionable tasks executed. The JUnit XML adds up to
929 JVM tests (`:common` 698, `:common-ui` 141, `:app` 70, `:android-jvm-stubs` 20), with 0 failures,
0 errors and 0 skipped. `:app:verifyRoborazziDebug` and `verifyPureCoreCoverage` ran inside `check`
and passed. `:app:connectedDebugAndroidTest` was not run. `ANDROID_SERIAL` is unset and
`adb devices` lists no device.

Earlier evidence at the same head: both review lanes ran `./gradlew --offline check` on `a006c1a` and
got `BUILD SUCCESSFUL`. Task 9 ran `verifyPureCoreAttribution` at `a006c1a` (57/57 owner classes,
4m8s). This lane did not repeat that run.

## Artifacts

All paths are under `/home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/`:

| Artifact | File | Schema | Status |
| --- | --- | --- | --- |
| Requirements | `requirements.md` | `gc.build.requirements.v1` | approved |
| Design | `brainstorming/design-candidate.md`, gates `brainstorming/approval-summary.md`, `brainstorming/spec-approval-summary.md` | - | approved by the owner |
| Plan | `implementation-plan.md` | `gc.build.plan.v1` | approved |
| Plan review | `plan-review-report.md`, `plan-review-apply-summary.md` | - | approved |
| Decomposition | `decomposition.md` | `gc.build.decomposition.v1` | approved |
| Per-task summaries | `task-fnr-*-summary.md` (9 files) | - | all tasks pass |
| Implementation summary | `implementation-summary.md` | `gc.build.implementation-summary.v1` | validates, kept as it was |
| Review report | `review-report.md` (copy of `implementation-review-report.md`) | `gc.build.review.v1` | approved |
| Gap analysis | `gap-analysis-report.md` | `gc.build.review.v1` | approved |
| Review fix summary | `review-fix-summary.md` | `gc.build.review.v1` | approved, no-op |
| Final report | `factory-run.md` (this file) | `gc.build.final-report.v1` | approved |

Code: branch `fnr/now-playing` at `a006c1a1db182f9355b28f4cb9bba436f09e52f9` in
`/home/jsz/Devel/jacekszymanski/FreeNetRadio`.

The implementation summary marks REQ-019 `covered` and only REQ-020 `blocked`. The later review fix
summary also marks REQ-019 `blocked`, because its device half has not run. The coverage table below
follows the review.

## Remaining Risks

- **The instrumented suite has never run against this branch.** No lane had a device. Task 8's seven
  `NowPlayingJourneyTest` cases and its fixes to the two `OfflinePlaybackJourneyTest` cases that
  Task 6 broke compile, but nobody has executed them. Merging before `fnr-ugbkm` closes risks
  breakage that only a device shows. This is the main blocker before merge.
- **Visual conformance (REQ-017) has only been checked by eye.** No automated image diff against
  the reference PNGs exists. The agreed differences are D1, D2 and D6. The owner reviews the
  baselines as part of `fnr-ugbkm`. The chevron wording is `fnr-vrntr`.
- **`KEY_COUNTRY_NAME` goes beyond the letter of REQ-018.** The change is additive (plan decision 3).
  Nothing else in the service, browse tree or Android Auto changed.
- **Decision 9 (REQ-015's "app data cleared" row) is still open.** Nothing was built for it beyond
  closing the player screen when no station is shown.
- **Optional review findings are not fixed:** the unused `NowPlayingView.tapDetailsBack()` helper
  (the Station details back arrow is only tested at model level), the stale `common/src/test` class
  count in `doc/project-overview.md`, and `NowPlayingTracker.onPlaybackStateChanged` dropping the
  player's id until the first titled metadata. Task 4 also left two optional findings open: a gap in
  tracker-wiring coverage and an untested no-title branch.
- **The lasting documents are not on the branch yet.** This lane committed nothing. The workflow's
  later `commit-docs` step copies the requirements, design and this report into
  `docs/build/fnr-8i7gz/` on `fnr/now-playing`, which moves the branch head past `a006c1a`.
- **Leftover worktrees.** Harness-owned detached worktrees for every task bead remain under
  `gcexp/.gc/worktrees/freenetradio/`. This lane only read from `fnr-1jekp` and ran the build there.
  It removed nothing.

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
| REQ-019 | blocked |
| REQ-020 | blocked |
