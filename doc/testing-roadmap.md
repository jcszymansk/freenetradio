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

| Layer | Current tests | Assessment |
| --- | ---: | --- |
| Local JVM | 15 distinct tests | Passed for debug and release |
| Instrumented | 13 test methods | Test APK compiles; execution was not verified because no device or emulator was connected |
| End-to-end | One narrow service integration test | Covers first-local-station root refresh |
| Coverage reporting | None | No JaCoCo or Kover configuration |

Canonical commands (with `ANDROID_HOME` configured):

```sh
# JVM tests
./gradlew test

# Instrumentation compilation
./gradlew :app:assembleDebugAndroidTest

# Instrumented tests on a running emulator, with external networking disabled
adb shell svc wifi disable
adb shell svc data disable
./gradlew :app:connectedDebugAndroidTest

# Local and instrumented coverage reports
./gradlew localCoverageReport
./gradlew instrumentedCoverageReport
```

Known problems in the current suite:

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

## [x] Phase 1: Repair the foundation

**Purpose:** make the existing suite honest, deterministic, and measurable.

1. [x] Delete or restore the assertion-free storage merge test.
2. [x] Move pure tests from `app/src/androidTest` to `common/src/test`:
   - `MediaIDHelperTest`
   - `RadioStationJsonSerializerTest`
   - `EqualizerSerializationTest`
3. [x] Convert the legacy JUnit3 media-ID test to JUnit4.
4. [x] Strengthen serializer tests into complete round trips.
5. [x] Ensure every test owns and clears its SharedPreferences, files, and database state.
6. [x] Introduce shared station factories, provider fixtures, and recording fakes only where duplication appears.
7. [x] Enable the Android Gradle Plugin's JaCoCo support without adding another coverage framework.
8. [x] Produce separate local and instrumented coverage reports.
9. [x] Establish canonical commands for JVM tests, instrumentation compilation, offline emulator tests, and coverage generation.

**Exit check:** no assertion-free, disabled, order-dependent, or externally networked test remains. Existing behavior is covered at least as strongly as before.

## Phase 2: Cover the pure data and domain core

**Purpose:** establish the large, fast base of the test pyramid.

### Provider parsing

Test `ParserLayerRadioBrowserImpl` and `ParserLayerWebRadioImpl` for:

- [x] Valid station mapping
- [x] Missing station ID
- [x] Missing stream URL
- [x] Malformed and empty JSON
- [x] Unknown fields
- [x] Country-code mapping
- [x] Category counts and title normalization
- [x] Filtered stations
- [x] WebRadioDB category, country, and case-insensitive search filtering

### Cache and download policy

Test `ModelLayerImpl` with recording fakes:

- [x] No connectivity returns no data and touches no cache or downloader.
- [x] A memory hit bypasses persistence and download.
- [x] A persistent hit is promoted to memory.
- [x] Empty and `[]` cache values are misses.
- [x] A successful download replaces both cache levels.
- [x] An empty download is not cached.
- [x] The parser receives exactly the selected response.

### Identifiers and browse state

Test:

- [x] `MediaId` construction, normalization, search IDs, country IDs, and sortable and refreshable classifications
- [x] `BrowseTree` replacement, append, item lookup, station lookup, parent-list lookup, and invalidation
- [x] Indexable command page reset and advancement
- [x] Catalogue-change decisions

### Serialization and local utilities

Test:

- [ ] Complete `RadioStation` round trips
- [ ] Complete equalizer-state round trips and malformed data
- [ ] Map import and export with malformed and missing entries
- [ ] Playlist dispatch for M3U, M3U8, PLS, ASX, and XSPF using in-memory streams
- [ ] In-memory API cache operations
- [ ] URL construction, query encoding, and pagination
- [ ] Filter rules, including empty-stream behavior

**Exit check:** the selected pure-core package set reaches at least 80% line coverage and 70% branch coverage, and every listed class has normal, edge, and failure-path tests.

## Phase 3: Cover persistence and state mutation

**Purpose:** protect user data before further roadmap work changes storage or migrations.

Cover these components with instrumented tests:

- [ ] `FavoritesStorage`: add, remove, duplicate handling, lookup, and sort IDs
- [ ] `DeviceLocalsStorage`: ID allocation, add, edit, remove, and propagation to favorites and latest station
- [ ] `LatestRadioStationStorage`: empty default, save, reload, and clear
- [ ] Settings storage: defaults, writes, reloads, and invalid values
- [ ] Abstract station deserialization: invalid records, ordering, and sort-ID normalization
- [ ] Storage merge: duplicates, conflicts, and empty inputs
- [ ] `PersistentApiCache`: put, get, remove, clear, replacement, and expiry boundary
- [ ] File import and export through app-private temporary files, including malformed and partial input

Each test must get fresh application state or explicitly clear every store it touches.

**Exit check:** favorites, locals, settings, latest station, Room cache, and file round trips are deterministic and covered for success and corrupted-input behavior.

## Phase 4: Cover browse commands and the Media3 service contract

**Purpose:** protect the shared phone and Android Auto surface.

First test `OpenRadioServicePresenterImpl` and each `MediaItemCommand` with fake collaborators:

- [ ] Root composition
- [ ] Favorites and Locals appearing only when populated
- [ ] Country entry rules
- [ ] Phone versus car root command registration
- [ ] Categories, countries, popular, new, and search nodes
- [ ] Playable and browsable metadata
- [ ] Empty-state behavior
- [ ] Pagination and refresh
- [ ] Invalid stations being omitted

Then expand the existing real `MediaBrowser` to `OpenRadioService` instrumentation pattern:

1. [ ] Connect before any Activity exists.
2. [ ] Fetch the library root and root children.
3. [ ] Seed favorites and locals, then verify their browse nodes.
4. [ ] Subscribe to root and child nodes.
5. [ ] Add, edit, and remove a local station and verify immediate subscription refresh.
6. [ ] Toggle favorite state and verify storage plus browse refresh.
7. [ ] Verify sort-update commands and invalid command arguments.
8. [ ] Verify unknown custom commands return not supported.
9. [ ] Search using a seeded local cache fixture.
10. [ ] Clear app data and reconnect successfully.

**Exit check:** all offline browse nodes and supported custom commands are exercised through a real Media3 connection, including cold service startup without an Activity.

## Phase 5: Cover playback and resilience

**Purpose:** test the roadmap's highest operational risk without relying on radio streams.

Add only the smallest testability seams needed:

- [ ] Inject or isolate player error classification in `OpenRadioPlayer`.
- [ ] Inject `RadioStationValidator` into `RadioStationManagerLayerImpl` so mutation tests do not probe the internet.
- [ ] Reuse existing interfaces instead of introducing a DI framework.

Test:

- [ ] Playback of a generated local WAV file
- [ ] Expanding a selected item to the expected parent playlist
- [ ] Switching stations
- [ ] Pause, resume, stop, previous, and next
- [ ] Current-item and metadata updates
- [ ] Last-station persistence
- [ ] Malformed and unsupported playlist handling
- [ ] Network-error and HTTP 403/404 classification
- [ ] The mobile-data-disabled gate
- [ ] Network loss and recovery transitions
- [ ] Becoming-noisy pause
- [ ] Bluetooth-connect decision logic using broadcast intents
- [ ] Sleep-timer start, replacement, cancellation, and completion
- [ ] Playback resumption with and without an existing playlist

Do not invoke the service's process-killing stop path inside instrumentation. Extract its decision logic for a component test and retain one manual lifecycle check.

**Exit check:** core playback and recovery behavior passes with networking disabled and no physical audio output required.

## Phase 6: Add a small phone end-to-end suite

**Purpose:** prove that the user-visible system is assembled correctly.

Keep the suite deliberately small:

1. [ ] **Cold launch**
   - Clear application data.
   - Launch `MainActivity`.
   - Verify the root list loads without network.
2. [ ] **Local station lifecycle**
   - Add a local station through the dialog.
   - Verify Locals appears immediately.
   - Edit and remove the station.
   - Restart and verify persistence.
3. [ ] **Favorite lifecycle**
   - Seed or browse a local station.
   - Add and remove the favorite.
   - Verify the Favorites node and persisted state.
4. [ ] **Offline playback**
   - Select a local WAV-backed station.
   - Verify now-playing metadata and controls.
5. [ ] **Settings persistence**
   - Change network, buffering, and general settings.
   - Recreate the Activity and verify their values.
6. [ ] **Service-first startup**
   - Start and browse the service before opening the Activity.
   - Open the Activity and verify consistent state.

Parser errors, cache branches, and storage boundaries belong in cheaper test layers, not in UI tests.

**Exit check:** all six journeys pass on a clean API 34 emulator with networking disabled, without retries or test-order assumptions.

## Phase 7: Establish the permanent gate

The main roadmap restarts only when:

- [ ] All JVM tests pass.
- [ ] Instrumentation tests compile and pass on the canonical emulator.
- [ ] All offline end-to-end journeys pass.
- [ ] Critical pure-core coverage is at least 80% line and 70% branch.
- [ ] No critical class is considered covered solely because another class happened to execute it.
- [ ] Every fixed bug has a regression test at the lowest appropriate layer.
- [ ] The suite makes no external network requests.
- [ ] No ignored, commented-out, assertion-free, or retry-masked tests exist.
- [ ] Android Auto manual checks have a recorded result for the current build.

Run policy:

| Trigger | Required checks |
| --- | --- |
| Every meaningful change | Affected JVM tests |
| Before commit or handoff | Full JVM suite |
| Before restarting main roadmap work | JVM plus offline emulator component and end-to-end suites |
| Before a personal release | All automated tests plus DHU and real-car checklist |

## Android Auto boundary

Automated Media3 service tests can cover the protocol shared by the phone and Android Auto, but not the complete projected UI.

Keep these manual:

- [ ] Android Auto controller classification
- [ ] Desktop Head Unit and real head-unit rendering and navigation
- [ ] Steering-wheel and media-button behavior
- [ ] Voice search integration
- [ ] Real Bluetooth disconnection
- [ ] Calls and navigation audio interruption
- [ ] Wired and wireless reconnection

Android's guidance specifically calls for service startup before an Activity, force-stop and clear-data scenarios, the Media Controller Test app, Desktop Head Unit testing, and real-vehicle testing.

## References

- [Android testing fundamentals](https://developer.android.com/training/testing/fundamentals)
- [What to test in Android](https://developer.android.com/training/testing/fundamentals/what-to-test)
- [Android testing strategies and test pyramid](https://developer.android.com/training/testing/fundamentals/strategies)
- [Serve content with a MediaLibraryService](https://developer.android.com/media/media3/session/serve-content)
- [Test Android apps for cars](https://developer.android.com/training/cars/testing)
