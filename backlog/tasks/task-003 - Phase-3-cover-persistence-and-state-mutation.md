---
id: TASK-003
title: 'Phase 3: cover persistence and state mutation'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-17 19:19'
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
- [ ] #1 FavoritesStorage: add, remove, duplicate handling, lookup and sort IDs
- [ ] #2 DeviceLocalsStorage: ID allocation, add, edit, remove, propagation to favorites and latest station
- [ ] #3 LatestRadioStationStorage: empty default, save, reload and clear
- [ ] #4 Settings storage: defaults, writes, reloads and invalid values
- [ ] #5 Abstract station deserialization: invalid records, ordering and sort-ID normalization
- [ ] #6 Storage merge: duplicates, conflicts and empty inputs
- [ ] #7 PersistentApiCache: put, get, remove, clear, replacement and expiry boundary
- [ ] #8 File import and export through app-private temporary files, including malformed and partial input
- [ ] #9 Exit check: favorites, locals, settings, latest station, Room cache and file round trips are deterministic and covered for success and corrupted input
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add instrumented tests under app/src/androidTest/.../model/storage: FavoritesStorageTest, DeviceLocalsStorageTest, LatestRadioStationStorageTest, SettingsStorageTest, AbstractRadioStationsStorageTest (deserialization/ordering/sort-id), StorageManagerLayerTest (merge), PersistentApiCacheTest, FileStoreManagerTest (file round trip via app-private temp files and file:// Uris).
2. Every test clears the preference files, the Room cache table and the temp files it touches in @Before and @After; no shared state between tests, no network.
3. Reuse the existing androidTest conventions: AndroidJUnit4 runner, InstrumentationRegistry target context, hand-written fakes, no mocking framework.
4. Verify by compiling the test APK and running :app:connectedDebugAndroidTest on a headless API 34 emulator with wifi and data disabled.
5. Record any production defect the tests expose as its own follow-up task rather than fixing it here; the test pins the current behavior and names the task.
<!-- SECTION:PLAN:END -->
