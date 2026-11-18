---
schema: gc.build.final-report.v1
workflow:
  id: fnr-pi6ap
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
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/requirements.md
      hash: sha256:01596f10d6f09b3aa121371822bb9854262be78e5d02b1385094e69c2003297a
      ids: [REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-015, REQ-016, REQ-017]
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/brainstorming/design-candidate.md
      hash: sha256:1cfbc453bbe3cf1a9016fa426a05e2687bb6f1e4325a52043a71d5807613dc89
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/implementation-plan.md
      hash: sha256:acd9b11e4bb800886eee3a44518f527985709c8639569462d912eace2b0cbeb9
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/decomposition.md
      hash: sha256:9a57bcf0198f7321c49f3892ac402219c1c5125656e414dc313885d429de1c50
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/implementation-summary.md
      hash: sha256:bbbb9330f6e8005752fb615911996de6d00d1717ca5003bcce6495e8ce02f78a
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/review-report.md
      hash: sha256:b7492909769b2db567652e22f42dd6e1f8248078a6be2f2309fac10706a65ad9
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/gap-analysis-report.md
      hash: sha256:624b9faf9e45a634468da12246aae32c330f2d7140a99e03d6253729e48d3484
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/review-fix-summary.md
      hash: sha256:3d6be12b08c49422e556e50ed1f9ce8862fa5f3a8bee6f979a9e23049b0a6905
    - path: git:master..fnr/compose-shell
      hash: git:6027ae02642f67414d73327e0b43590b418102e6
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
      status: deferred
      rationale: "The restart clause conflicts with the default last-known-station autoplay in OpenRadioService.onCreate, and REQ-015 forbids changing the service. The owner decides on needs-human bead fnr-yn3gp (decision 14); the branch implements the closest reading."
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
      status: deferred
      rationale: "Roborazzi baselines exist for every listed state in dark and light and verify green; the owner's comparison with console-design reference images is carried by human-only bead fnr-g76t3."
    - id: REQ-015
      status: covered
    - id: REQ-016
      status: covered
    - id: REQ-017
      status: deferred
      rationale: "test, assembleDebug, :app:assembleDebugAndroidTest and check pass at 6027ae0, re-run by this finalize lane. :app:connectedDebugAndroidTest has never run because no lane had a device; tracked as human-only bead fnr-g76t3."
---

# Compose main screen build: final report

## Summary

Workflow `fnr-pi6ap` (`superpowers-lite-build`, source bead `fnr-bfxtd`) moved FreeNetRadio's main
screen from the View-era drawer UI to one Compose screen in the Console design. The work is on the
local integration branch `fnr/compose-shell`: 14 commits on top of `master` at `2eb80f3`, head
`6027ae0`, 170 files changed (+5965 / -6146). Requirements, design, spec, plan and plan review were
approved. The ten implementation tasks in convoy `fnr-2jf2m` all closed with `pass`. The
whole-branch review closed `done` on its second attempt: the implementation review and the gap
analysis both approved `6027ae0`, and no required finding is open.

Nothing was pushed and no PR was opened (`push=false`, `open_pr=false`). `master` has not moved.
Under the repository's rule only the owner merges an integration branch into `master` (`--no-ff`),
so this lane kept the branch as it is.

## Outcome

**Pass. The branch is ready for the owner's review but should not be merged yet.** Two owner beads
must close first:

| Bead | Label | What it needs | Requirement |
| --- | --- | --- | --- |
| `fnr-g76t3` | `human-only` | Run `:app:connectedDebugAndroidTest` on a cleared, offline emulator, and compare the Roborazzi baselines with `console-design/design-reference/png/` | REQ-017, REQ-014 |
| `fnr-yn3gp` | `needs-human` | Decide how the mini player should behave after a restart (decision 14) | REQ-008 |

The finalize lane re-ran the verification itself at `6027ae0` in a fresh detached worktree
(`console-design` at `347ed51`, JDK 17.0.20.1, `ANDROID_HOME=$HOME/Android/Sdk`):

```sh
./gradlew --offline test assembleDebug :app:assembleDebugAndroidTest check
```

Result: `BUILD SUCCESSFUL in 5m 3s`. All 234 actionable tasks executed (a fresh worktree, so nothing was
up to date). The JUnit XML adds up to 820 JVM tests, with 0 failures, 0 errors and 0 skipped.
`:app:verifyRoborazziDebug` and `verifyPureCoreCoverage` ran inside `check` and passed.
`:app:connectedDebugAndroidTest` was not run: this lane has no device either.

Earlier evidence at the same head: both attempt-2 review lanes ran the same command set, one with
`--rerun-tasks` (`BUILD SUCCESSFUL in 4m 30s`, 820 JVM tests, 0 failures, 0 errors, 0 skipped,
`verifyRoborazziDebug` and `verifyPureCoreCoverage` passing inside `check`). Task 10 ran
`verifyPureCoreAttribution` once at `9d376f7` and it passed. The repair commit `6027ae0` adds no
class and changes no coverage ownership.

## Artifacts

All paths are under `/home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/`:

| Artifact | File | Schema | Status |
| --- | --- | --- | --- |
| Requirements | `requirements.md` | `gc.build.requirements.v1` | approved |
| Design | `brainstorming/design-candidate.md` | - | approved |
| Plan | `implementation-plan.md` | `gc.build.plan.v1` | approved |
| Plan review | `plan-review-report.md`, `plan-review-apply-summary.md` | - | approved |
| Decomposition | `decomposition.md` | `gc.build.decomposition.v1` | approved |
| Per-task summaries | `task-fnr-*-summary.md` (10 files) | - | all tasks pass |
| Implementation summary | `implementation-summary.md` | `gc.build.implementation-summary.v1` | validates, kept as it was |
| Review report | `review-report.md` (copy of `implementation-review-report.md`) | `gc.build.review.v1` | approved, validates |
| Gap analysis | `gap-analysis-report.md` | `gc.build.review.v1` | approved |
| Review fix summary | `review-fix-summary.md` | `gc.build.review.v1` | done |
| Final report | `factory-run.md` (this file) | `gc.build.final-report.v1` | approved |

Code: branch `fnr/compose-shell` at `6027ae02642f67414d73327e0b43590b418102e6` in
`/home/jsz/Devel/jacekszymanski/FreeNetRadio`.

The implementation summary still has `status: draft`. It also marks several requirements
`deferred` because it was written before the whole-branch review. The later review reports supersede
its coverage: the review confirmed REQ-002 to REQ-012 at the code level. The coverage table below
follows the review.

## Remaining Risks

- **The instrumented suite has never run.** No lane had a device. The journeys compile but have not
  been executed against the Compose screen. Merging before `fnr-g76t3` closes risks breakage that
  only shows up on a device. This is the main blocker before merge.
- **The REQ-008 restart behaviour was undecided** (`fnr-yn3gp`). The owner decided on 2026-11-10 to
  keep the branch's reading and amended REQ-008 to match, so the two restart journeys now assert
  the requirement.
- **Visual check is the owner's** (REQ-014). Known nits, listed as M4 on `fnr-bfxtd`: initials differ
  slightly from the reference images, title and mini-player type are slightly lighter, and the mini
  player's button is 44dp where the plan says 48dp.
- **Non-blocking review findings are recorded on `fnr-bfxtd` and not fixed:**
  - M2: `MediaResourcesManager.clean()` does not cancel its scope. This predates the build.
  - M3: artwork is decoded at full size.
- **The submodule clone may have reached the network.** This lane initialised `console-design` with
  `--reference` to the local clone. Its remote is still the GitHub URL, so the clone may have made a
  read-only fetch. Nothing was pushed.
- **Leftover worktrees.** Harness-owned detached worktrees for every task bead remain under
  `gcexp/.gc/worktrees/freenetradio/`, plus `/tmp/fnr-review-hhfu7`. This lane's own
  `/tmp/fnr-finalize-pi6ap` is removed after verification. The harness owns the rest, and this lane
  did not touch them.

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
| REQ-008 | deferred |
| REQ-009 | covered |
| REQ-010 | covered |
| REQ-011 | covered |
| REQ-012 | covered |
| REQ-013 | covered |
| REQ-014 | deferred |
| REQ-015 | covered |
| REQ-016 | covered |
| REQ-017 | deferred |
