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

The build needs JDK 17: Android Gradle Plugin 8.2 requires at least 17, and the Gradle 8.2 wrapper cannot
run on 21. From the Android SDK it needs platform 34 (`compileSdkVersion` in `constants.gradle`) and the
build tools 34.0.0 that AGP 8.2 defaults to. A fresh clone builds with nothing else: no `sign.properties`,
`local.properties` or `google-services.json`.

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
./gradlew verifyPureCoreCoverage                # the pure-core coverage gate; ./gradlew check runs it too
./gradlew verifyPureCoreAttribution             # re-runs each pure-core owner test alone, a few minutes
```

A class with at least one branch or 20 lines, added to a package that already holds a listed
pure-core class, fails `verifyPureCoreCoverage` until `gradle/pure-core-coverage.tsv` lists it or
excludes it with a rule. Smaller classes and classes in packages with nothing listed are not
checked, so those still have to be judged by reading; `doc/testing-roadmap.md` has the rules.

Instrumented tests must run with emulator networking disabled, against a device that is not
carrying application data from earlier use:

```sh
adb shell svc wifi disable && adb shell svc data disable
adb shell pm clear com.github.jcszymansk.freenetradio   # skip if the app is not installed
./gradlew :app:connectedDebugAndroidTest
```

With more than one device attached, `adb` refuses to guess and `connectedDebugAndroidTest` installs on every
device. When `ANDROID_SERIAL` is set, it names the one device this work may use: never pass another `-s`
serial, and never touch, clear or stop another emulator.

The test APK runs under `OfflineTestRunner`, which fails every test class without running it when
the device reports a network, so forgetting the first line costs a red run rather than traffic.
A url an instrumented fixture hands to the app points at `UNREACHABLE_ORIGIN` or a
`LoopbackHttpFixture`, never at a host name: a reserved name such as `.invalid` still sends a DNS
query off the device.

Gradle installs over whatever is on the device and keeps its data, so the clear is not optional
housekeeping. The active station provider is read once per process by `ImagesProvider.onCreate`,
which Android runs before the Application and before the instrumentation, so a device where
someone selected WebRadio in the app binds WebRadio for the whole run and no test can change it.
The suites assert the provider they got rather than the one that is stored, and fail loudly when
it is the wrong one.

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
  in `:android-jvm-stubs` — `android.net.Uri`, `android.os.Bundle`, `android.webkit.MimeTypeMap`, the resource
  lookups of `android.content.Context` and the `android.database.Observable` that every `RecyclerView.Adapter`
  notification walks live there. That module is wired as `testRuntimeOnly` in `:common` and `:common-ui`, so
  tests still compile against the real Android API and only the implementation is swapped at run time; putting
  the same class in a test source set instead makes the Kotlin compiler reject every use of the type it
  duplicates.
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

Work is tracked as beads in this repository's Gas City rig (`freenetradio`, prefix `fnr`), read and written with
`gc bd` from the repository root. `doc/roadmap.md` and `doc/testing-roadmap.md` hold the reasoning and the standard
each piece of work must meet; the beads hold the state. Start from `gc bd ready`.

Until 2026-10 the tracker was Backlog.md. Every task it held is a bead now, open or closed, with its old id as
the bead's external reference: code, tests, docs and commit messages that cite `TASK-104` find it with
`gc bd list --all --external-ref TASK-104`. Those citations stay as they are. Milestones are epics.

1. **One source of truth per fact.** A roadmap document states why work exists and what "done" means. The
   bead records whether it is done. Never add a checkbox, a status line, or a progress note to a document;
   that is the half that rots, because nothing forces it to agree with the code.

2. **A bead's state lives in the bead, not in commits.** Claim it, record decisions, blockers and scope changes
   on it with `gc bd update` while they are fresh, and close it when its acceptance criteria hold. Do not edit
   acceptance criteria to fit the work; a criterion that turns out wrong is a decision for the owner.

3. **Batches land on an integration branch.** A batch of beads is drained one bead at a time, each in its own
   worktree based on the batch's integration branch, and each reviewed bead fast-forwards that branch. The owner
   reviews the integration branch and merges it into `master` with `--no-ff`. Nothing but that merge moves
   `master`. Commit bodies end with `Bead: <id> (<external ref>)` where the bead has one.

4. **Out-of-scope findings become beads, not TODO comments.** A defect of medium severity or higher found while
   working on something else is filed with the `review-followup` label and a `discovered-from` edge to the bead
   being worked on. Lesser observations go in that bead's notes. The owner triages `review-followup` beads; they
   are not picked up automatically.

5. **Beads that need the owner say so.** Labels: `needs-human` for a decision only the owner can make (most
   `Decide ...` beads), `human-only` for work that needs the owner's hardware, keys or accounts (real phone,
   car, release signing, store listings). Neither is drained.

## Conventions

- Kotlin fields use the inherited `m` prefix for instance state and `s` for statics; preference and command keys are
  `private const val` inside a `companion object`. Keep new code consistent with the file around it.
- Keep the Apache 2.0 header and existing copyright lines on files you touch.
- Commit subjects are short, lowercase, imperative: `cover browse tree state`, `upgrade android build tooling`.
