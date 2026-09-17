---
id: TASK-003
title: 'Phase 3: cover persistence and state mutation'
status: To Do
assignee: []
created_date: '2026-09-17 18:24'
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
