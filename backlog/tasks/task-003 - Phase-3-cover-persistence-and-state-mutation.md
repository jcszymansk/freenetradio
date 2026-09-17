---
id: TASK-003
title: 'Phase 3: cover persistence and state mutation'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-17 20:03'
labels: []
milestone: m-0
dependencies:
  - TASK-002
type: chore
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Protects user data before further roadmap work changes storage or migrations. These are instrumented tests: SharedPreferences and Room are the subject, so they cannot run on the JVM. Each test must get fresh application state or explicitly clear every store it touches.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 FavoritesStorage: add, remove, duplicate handling, lookup and sort IDs
- [x] #2 DeviceLocalsStorage: ID allocation, add, edit, remove, propagation to favorites and latest station
- [x] #3 LatestRadioStationStorage: empty default, save, reload and clear
- [x] #4 Settings storage: defaults, writes, reloads and invalid values
- [x] #5 Abstract station deserialization: invalid records, ordering and sort-ID normalization
- [x] #6 Storage merge: duplicates, conflicts and empty inputs
- [x] #7 PersistentApiCache: put, get, remove, clear, replacement and expiry boundary
- [x] #8 File import and export through app-private temporary files, including malformed and partial input
- [x] #9 Exit check: favorites, locals, settings, latest station, Room cache and file round trips are deterministic and covered for success and corrupted input
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add instrumented tests under app/src/androidTest/.../model/storage: FavoritesStorageTest, DeviceLocalsStorageTest, LatestRadioStationStorageTest, SettingsStorageTest, AbstractRadioStationsStorageTest (deserialization/ordering/sort-id), StorageManagerLayerTest (merge), PersistentApiCacheTest, FileStoreManagerTest (file round trip via app-private temp files and file:// Uris).
2. Every test clears the preference files, the Room cache table and the temp files it touches in @Before and @After; no shared state between tests, no network.
3. Reuse the existing androidTest conventions: AndroidJUnit4 runner, InstrumentationRegistry target context, hand-written fakes, no mocking framework.
4. Verify by compiling the test APK and running :app:connectedDebugAndroidTest on a headless API 34 emulator with wifi and data disabled.
5. Record any production defect the tests expose as its own follow-up task rather than fixing it here; the test pins the current behavior and names the task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Added eight instrumented test classes under app/src/androidTest/.../model/storage covering favorites, device locals, the latest station, the settings preferences, the AbstractStorage typed accessors, the shared radio station marshalling, the import merge, the Room API cache and the data file round trip. 85 storage tests, 89 in the whole instrumented suite, all green on a headless API 34 emulator with wifi and data disabled; ./gradlew test, localCoverageReport and instrumentedCoverageReport also pass.

Two seams were needed: FileStoreManager.REQUEST_CODE_CREATE_FILE and REQUEST_CODE_OPEN_FILE became public so a test can deliver an activity result, and :app declares androidx.room:room-runtime for androidTest because :common keeps Room off the app classpath.

Three production defects surfaced and were filed rather than fixed here: TASK-024 (cache freshness window measured in milliseconds against a constant named for seconds), TASK-025 (cache appends a row per write and reads the oldest), TASK-026 (a failed export throws out of onActivityResult instead of reporting failure). The tests pin the current behaviour and name the task to update them with.

Noted for later: instrumentedCoverageReport only reports :app sources, so it does not measure these storage classes, which live in :common. Phase 7 coverage accounting has to take that into account.

Round 1 review follow-up: tightened the API cache expiry test to bracket the boundary the cache actually applies (about 86.4 seconds) instead of two arbitrary ages; added a positive favorites lookup by media id; and reframed the latest-station clear test as a pinned defect after confirming that OpenRadioServicePresenterImpl.clear(), reached by CMD_CLEAR_CACHE, leaves the cached station readable — filed as TASK-027.

Round 3 review follow-up: mergeDeviceLocals now has its own duplicate and both conflict-order tests, and the malformed-input test covers both merge entry points. The merge algorithm is shared, but the write side is not — FavoritesStorage.add normalizes the media id and assigns a sort id where DeviceLocalsStorage uses the inherited add — so the two paths are covered separately. Suite is 93 instrumented tests.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Phase 3 of the testing roadmap is covered by ten instrumented test classes under app/src/androidTest/.../model/storage: favorites, device locals, the latest station, the settings preferences, the AbstractStorage typed accessors, the shared radio station marshalling, the import merge, the Room API cache and the data file round trip, each for success and for corrupted, partial or empty input. Verified on a headless API 34 emulator with wifi and mobile data disabled: ./gradlew :app:connectedDebugAndroidTest passes 90 tests, repeated three times with no order dependence or flakiness, and ./gradlew test, localCoverageReport and instrumentedCoverageReport all pass. Two minimal seams were added: FileStoreManager's two request codes are public so a test can deliver an activity result, and :app declares room-runtime for androidTest. Four production defects surfaced and are tracked separately as TASK-024, TASK-025, TASK-026 and TASK-027; the tests pin the current behaviour and name the task to update them with.
<!-- SECTION:FINAL_SUMMARY:END -->
