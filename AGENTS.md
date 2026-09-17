# AGENTS.md

This file provides guidance to coding agents working in this repository. It is the harness agnostic
source; `CLAUDE.md` only points here.

## Project

FreeNetRadio is a Kotlin Android internet-radio client for phones/tablets and Android Auto, revived from the
archived OpenRadio source (see `doc/source-provenance.md`). It streams stations listed by Radio Browser and
WebRadioDB through a Media3 `MediaLibraryService`.

Read these before non-trivial work; do not duplicate their content here:

- `doc/project-overview.md` — architecture in depth (module map, browse flow, providers, storage, DI wiring)
- `doc/roadmap.md` — product targets and the decisions that constrain what may be added
- `doc/testing-roadmap.md` — the phased test plan currently gating the main roadmap; keep its checkboxes current

## Build and test

The Android SDK must be reachable; `local.properties` is git-ignored and usually absent, so export the SDK path:

```sh
export ANDROID_HOME=$HOME/Android/Sdk
```

```sh
./gradlew test                                  # all JVM unit tests (:android-jvm-stubs, :common, :common-ui)
./gradlew :common:testDebugUnitTest --tests "*MediaIdTest*"   # one JVM test class or method
./gradlew assembleDebug                         # debug APK, signed with Android's debug key
./gradlew :app:assembleDebugAndroidTest         # compile instrumentation tests without a device
./gradlew localCoverageReport                   # JaCoCo for :common and :common-ui unit tests
./gradlew instrumentedCoverageReport            # JaCoCo for :app instrumentation tests
```

Instrumented tests must run with emulator networking disabled:

```sh
adb shell svc wifi disable && adb shell svc data disable
./gradlew :app:connectedDebugAndroidTest
```

Release builds are unsigned — no release signing key exists yet, and `sign.properties` must never be committed.
`./gradlew signReleaseBundle` increments `VERSION_CODE` in the tracked `version.properties` as a side effect.

Toolchain versions live in `constants.gradle`, not in the module build files. `minSdkVersion` is 17, which is why
OkHttp is pinned to 3.12.x and `androidx.media` to 1.6.0 — do not bump either without re-checking API 17.

## Architecture notes that are easy to get wrong

- **`OpenRadioService` is the hub.** Phone UI and Android Auto are both MediaBrowser clients of the same service
  and the same browse tree; there is no separate car Activity. A change to browse or playback affects both.
- **Browsing is a command pattern.** Each node under `model/media/item/` (`MediaItemRoot`, `MediaItemCountriesList`,
  …) produces its children via `OpenRadioServicePresenterImpl`; `BrowseTree` caches the result and is invalidated
  on favorites, sorting, source, and local-station changes. Adding a browse node means adding a `MediaItemCommand`
  and a `MediaId` entry, not UI code.
- **Two providers behind one `SourcesLayer`.** Radio Browser paginates server-side (page size 250, in
  `DependencyRegistryCommon.PAGE_SIZE`) and discovers mirrors over DNS; WebRadioDB downloads whole JSON datasets
  and filters while parsing. Provider work usually touches a `UrlLayer*Impl` and its matching `ParserLayer*Impl`.
- **No DI framework.** `DependencyRegistryCommon`, `DependencyRegistryCommonUi`, and `mobile/dependencies/DependencyRegistry`
  construct singletons and push them into objects through single-method `*Dependency` interfaces (`configureWith(...)`).
  New collaborators follow that pattern; do not introduce Hilt/Koin.
- **Storage is deliberately split.** SharedPreferences via `AbstractStorage` for settings, favorites, local stations
  and equalizer state; Room only for station artwork (`ImagesDatabase`) and the 24-hour API response cache
  (`PersistentApiDb`). API reads go memory cache → Room cache → network, and misses include empty and `[]` values.
- **Module/package overlap.** `:common` and `:common-ui` both publish classes under `com.yuriy.openradio.shared.*`,
  and `:app` uses `com.yuriy.openradio.mobile`. The package name does not tell you the module — search all three.
  Kotlin package names keep the upstream `com.yuriy.openradio` identity; the shipped application ID is
  `com.github.jcszymansk.freenetradio`.

## Testing conventions

- No mocking or DI framework. Write small hand-written recording fakes against the existing interfaces
  (`ParserLayer`, `ApiCache`, `DownloaderLayer`, `NetworkLayer`, `OpenRadioServicePresenter`), as in
  `common/src/test/.../ModelLayerImplTest.kt`.
- Tests must make no external network calls — no Radio Browser, WebRadioDB, GitHub Pages, DNS, or real streams.
  Use inline JSON or checked-in fixtures.
- Prefer JVM tests in `common/src/test`; use `app/src/androidTest` only when SharedPreferences, Room, `Context`,
  Media3 binder behavior, or lifecycle is genuinely the subject.
- `unitTests.returnDefaultValues = true` is set in every module, so accidental Android framework calls silently
  return defaults instead of failing. Where a JVM test needs a framework value type to actually work, implement it
  in `:android-jvm-stubs` — `android.net.Uri`, `android.os.Bundle`, `android.webkit.MimeTypeMap` and the resource
  lookups of `android.content.Context` live there. That module is wired as `testRuntimeOnly`, so tests still
  compile against the real Android API and only the implementation is swapped at run time; putting the same
  class in a test source set instead makes the Kotlin compiler reject every use of the type it duplicates.
  Stubs carry their own tests: a stub that lies is worse than no stub.
- Each test owns and clears the preferences, files, and databases it touches.

## Product constraints

These come from `doc/roadmap.md` and are not negotiable defaults:

- No telemetry, advertising, automatic crash reporting, or automatic log upload. Firebase, Crashlytics, Google Cast,
  and Play Services have been removed; do not reintroduce them or any hosted backend.
- Diagnostics leave the app only through a per-report `ACTION_SEND` chooser with a `FileProvider` attachment.
- Android TV and native Automotive OS are out of scope; `:tv` and `:automotive` were deleted.
- Prebuilt APKs under `app/store/` are historical artifacts, never build inputs.

## Work tracking

Tasks live in `backlog/`, managed with the `backlog` CLI (see the Backlog.md section at the end of this file).
`doc/roadmap.md` and `doc/testing-roadmap.md` hold the reasoning and the standard each piece of work must meet;
the tracker holds the state. Start from `backlog task list --ready --plain`.

1. **One source of truth per fact.** A roadmap document states why work exists and what "done" means. The
   tracker records whether it is done. Never add a checkbox, a status line, or a progress note to a document —
   that is the half that rots, because nothing forces it to agree with the code.

2. **One task, one branch, named for the task.** Branch names are `<task-id>-<short-slug>`, lower case, e.g.
   `task-003-persistence-tests`. The branch is merged with `--no-ff` when the task is done, then deleted.
   Merges are not squashed by default.

3. **Task state moves with the work, never in a separate bookkeeping pass.** A task is normally several
   commits — a commit per review turn is preferred to an uncommitted mess that can only be discarded.
   On the task's branch:
   - The first commit that touches the task's code also sets the task In Progress.
   - The commit that makes the acceptance criteria true also marks the task Done — that is the last commit on
     the branch, before the merge, never a sweep on master afterwards.
   - Intermediate commits need not touch the task file, except when they change what is known: a discovered
     blocker, a scope change, or a decision worth keeping is recorded in the commit that caused it, while it
     is still fresh.
   - Review feedback that will not be acted on now becomes a new task in the commit that closes the review
     turn — not a TODO comment, and not an unwritten intention. It reaches master with the merge.
   - Abandoning a branch leaves the task exactly as master last saw it, which is correct. If the attempt
     taught you something — a dead end, a blocker, a wrong assumption — record it on the task in a small
     commit on master so the next attempt does not repeat it.

   A commit whose only content is catching the tracker up on work already merged means the tracker had
   drifted; that is the signal to watch for. Leave `auto_commit` off in `backlog/config.yml` so task changes
   stay in the working tree and land in the commit you choose.

## Conventions

- Kotlin fields use the inherited `m` prefix for instance state and `s` for statics; preference and command keys are
  `private const val` inside a `companion object`. Keep new code consistent with the file around it.
- Keep the Apache 2.0 header and existing copyright lines on files you touch.
- Commit subjects are short, lowercase, imperative: `cover browse tree state`, `upgrade android build tooling`.

<!-- BACKLOG.MD GUIDELINES START -->
<!-- backlog.md-instructions-version: 1.51.0 -->
<CRITICAL_INSTRUCTION>

## Backlog.md Workflow

This project uses Backlog.md for task and project management.

**At the beginning of each conversation in this project, run `backlog instructions overview` before answering or taking action. Re-read it only if you have not read it yet in the current conversation.**

Use the overview to decide whether to search, read, create, or update Backlog tasks.

Before task lifecycle actions, read the matching detailed guide:
- `backlog instructions task-creation` before creating or splitting tasks
- `backlog instructions task-execution` before planning, changing status or assignee, adding a plan or implementation notes, or implementing task work
- `backlog instructions task-finalization` before checking acceptance criteria, writing final summaries, or moving tasks to terminal statuses

Use `backlog <command> --help` before running unfamiliar commands. Help shows options, fields, and examples.

Do not edit Backlog task, draft, document, decision, or milestone markdown files directly. Use the `backlog` CLI so metadata, relationships, and history stay consistent.

</CRITICAL_INSTRUCTION>
<!-- BACKLOG.MD GUIDELINES END -->
