# Testing Roadmap

This roadmap must be completed before work resumes on the main roadmap in [`roadmap.md`](roadmap.md).

FreeNetRadio needs a risk-based test suite with three layers:

1. Many local JVM contract tests for parsers, caching, identifiers, serialization, and state transitions.
2. A smaller instrumented component suite for SharedPreferences, Room, Android resources, and Media3 service integration.
3. A handful of offline end-to-end journeys through the actual phone UI and `MediaLibraryService`.

Do not optimize for a blanket repository-wide coverage percentage. That would reward testing Android wrappers and generated Room code instead of the failure-prone core. Measure coverage on selected production packages, then require explicit journey coverage for the service and UI.

## Constraints

- Tests must not call Radio Browser, WebRadioDB, GitHub Pages, public DNS, real radio streams, or any other external service.
- Use inline JSON or checked-in fixtures for provider responses.
- Use recording fakes for downloader, cache, network, presenter, and other existing interfaces.
- Use seeded SharedPreferences and Room databases for Android integration tests.
- Use a generated local WAV file for playback tests.
- A loopback-only HTTP server is acceptable only when raw HTTP behavior itself must be exercised.
- Do not add a mocking or dependency-injection framework. Existing interfaces and small hand-written fakes are sufficient.
- Run offline end-to-end tests with emulator networking disabled.
- Add production seams only where unavoidable, and keep them minimal.

## Baseline recorded on 2026-08-03

A historical snapshot of the suite as it stood when this roadmap was written. It is not updated; current state
lives in the tracker.

| Layer | Current tests | Assessment |
| --- | ---: | --- |
| Local JVM | 15 distinct tests | Passed for debug and release |
| Instrumented | 13 test methods | Test APK compiles; execution was not verified because no device or emulator was connected |
| End-to-end | One narrow service integration test | Covers first-local-station root refresh |
| Coverage reporting | None | No JaCoCo or Kover configuration |

The canonical build, test, and coverage commands live in `AGENTS.md` so there is one copy of them.

Known problems recorded at that baseline:

- `app/src/androidTest/java/com/yuriy/openradio/shared/model/storage/RadioStationsStorageTest.kt` has no active assertions and therefore always passes.
- `RadioStationJsonSerializerTest` only verifies that serialization returns a non-null value.
- Pure tests such as `MediaIDHelperTest` and serialization tests unnecessarily run on a device.
- `espresso-core` is declared but unused.
- `unitTests.returnDefaultValues = true` is enabled in every module and can conceal accidental Android-framework calls in JVM tests.
- The only substantial Media3 integration test is `MediaResourcesManagerTest`, which connects a real `MediaBrowser` to the real service and verifies subscription refresh.
- Both provider parsers, cache precedence, `BrowseTree`, URL pagination, most storage mutation, media-item commands, service commands, and playback recovery have no direct coverage.

## Test layers

### Local JVM tests

Use local JVM tests whenever Android itself is not the subject. Test inputs and outputs, state transitions, cache precedence, normal cases, malformed data, empty data, and boundaries.

The project already exposes replaceable interfaces for `ParserLayer`, `ApiCache`, `DownloaderLayer`, `NetworkLayer`, `OpenRadioServicePresenter`, and related components. Use small recording fakes rather than a mocking framework.

### Instrumented component tests

Use an emulator only for behavior requiring:

- SharedPreferences
- Room
- Android `Context`, resources, `Uri`, or intents
- Media3 binder and session behavior
- ExoPlayer with a local media file
- Activity or dialog lifecycle

Prefer component tests below the UI when they can establish the same contract.

### End-to-end tests

Use end-to-end tests only where integration is the contract:

- UI to presenter to `MediaBrowser` to `OpenRadioService`
- Service browse hierarchy
- Local-station mutation and subscription refresh
- Playback of a local audio fixture
- Persistence across activity and service recreation

## Phases

The phases run in order. Each one is a task in the tracker, and the coverage lists that used to appear here are
now that task's acceptance criteria, so progress is recorded in exactly one place. Run `backlog task list
--ready --plain` to see what is unblocked, and `backlog task view TASK-003 --plain` to read a phase in full.

### Phase 1: Repair the foundation — `TASK-001`

**Purpose:** make the existing suite honest, deterministic, and measurable.

The inherited suite contained assertion-free, misplaced, and order-dependent tests, and had no coverage
reporting at all. Nothing could be built on it until that was fixed.

**Exit check:** no assertion-free, disabled, order-dependent, or externally networked test remains. Existing
behavior is covered at least as strongly as before.

### Phase 2: Cover the pure data and domain core — `TASK-002`

**Purpose:** establish the large, fast base of the test pyramid.

Provider parsing, cache and download policy, identifiers and browse state, and serialization, each as a subtask.
All of it runs on the JVM against hand-written recording fakes.

**Exit check:** the selected pure-core package set reaches at least 80% line coverage and 70% branch coverage,
and every listed class has normal, edge, and failure-path tests.

### Phase 3: Cover persistence and state mutation — `TASK-003`

**Purpose:** protect user data before further roadmap work changes storage or migrations.

Favorites, local stations, settings, the latest station, the Room API cache, and file import and export. These
are instrumented tests: SharedPreferences and Room are the subject, so they cannot run on the JVM. Each test
must get fresh application state or explicitly clear every store it touches.

**Exit check:** favorites, locals, settings, latest station, Room cache, and file round trips are deterministic
and covered for success and corrupted-input behavior.

### Phase 4: Cover browse commands and the Media3 service contract — `TASK-004`

**Purpose:** protect the shared phone and Android Auto surface.

First `OpenRadioServicePresenterImpl` and each `MediaItemCommand` against fake collaborators, which is far
cheaper than driving the same cases through a real service. Then an expansion of the existing real
`MediaBrowser` to `OpenRadioService` instrumentation pattern.

**Exit check:** all offline browse nodes and supported custom commands are exercised through a real Media3
connection, including cold service startup without an Activity.

### Phase 5: Cover playback and resilience — `TASK-005`

**Purpose:** test the roadmap's highest operational risk without relying on radio streams.

Playback runs against a generated local WAV file. Add only the smallest testability seams needed, reusing
existing interfaces rather than introducing a DI framework. Do not invoke the service's process-killing stop
path inside instrumentation: extract its decision logic for a component test and retain one manual lifecycle
check.

**Exit check:** core playback and recovery behavior passes with networking disabled and no physical audio
output required.

### Phase 6: Add a small phone end-to-end suite — `TASK-006`

**Purpose:** prove that the user-visible system is assembled correctly.

Six journeys, one subtask each: cold launch, local station lifecycle, favorite lifecycle, offline playback,
settings persistence, and service-first startup. Keep the suite deliberately small. Parser errors, cache
branches, and storage boundaries belong in cheaper test layers, not in UI tests.

**Exit check:** all six journeys pass on a clean API 34 emulator with networking disabled, without retries or
test-order assumptions.

### Phase 7: Establish the permanent gate — `TASK-007`

**Purpose:** define the standing condition for resuming main roadmap work.

The main roadmap restarts only when every criterion on that task holds — all suites passing, critical pure-core
coverage met, no test covered only incidentally, every fixed bug carrying a regression test, no external network
requests, no ignored or assertion-free tests, and a recorded Android Auto manual result for the current build.

Run policy:

| Trigger | Required checks |
| --- | --- |
| Every meaningful change | Affected JVM tests |
| Before commit or handoff | Full JVM suite |
| Before restarting main roadmap work | JVM plus offline emulator component and end-to-end suites |
| Before a personal release | All automated tests plus DHU and real-car checklist |

## Android Auto boundary — `TASK-008`

Automated Media3 service tests can cover the protocol shared by the phone and Android Auto, but not the complete
projected UI. Controller classification, Desktop Head Unit and real head-unit rendering and navigation,
steering-wheel and media-button behavior, voice search, real Bluetooth disconnection, audio interruption, and
wired and wireless reconnection all stay manual, and their result has to be recorded for the current build
before the Phase 7 gate can pass.

Android's guidance specifically calls for service startup before an Activity, force-stop and clear-data
scenarios, the Media Controller Test app, Desktop Head Unit testing, and real-vehicle testing.

## References

- [Android testing fundamentals](https://developer.android.com/training/testing/fundamentals)
- [What to test in Android](https://developer.android.com/training/testing/fundamentals/what-to-test)
- [Android testing strategies and test pyramid](https://developer.android.com/training/testing/fundamentals/strategies)
- [Serve content with a MediaLibraryService](https://developer.android.com/media/media3/session/serve-content)
- [Test Android apps for cars](https://developer.android.com/training/cars/testing)
