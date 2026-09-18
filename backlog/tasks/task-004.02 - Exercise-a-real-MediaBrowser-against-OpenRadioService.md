---
id: TASK-004.02
title: Exercise a real MediaBrowser against OpenRadioService
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 04:20'
labels: []
milestone: m-0
dependencies:
  - TASK-003
parent_task_id: TASK-004
type: chore
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Expands the existing MediaResourcesManagerTest pattern. Android's guidance specifically calls for service startup before any Activity exists, and for force-stop and clear-data scenarios.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Connect before any Activity exists
- [ ] #2 Fetch the library root and root children
- [ ] #3 Seed favorites and locals, then verify their browse nodes
- [ ] #4 Subscribe to root and child nodes
- [ ] #5 Add, edit and remove a local station and verify immediate subscription refresh
- [ ] #6 Toggle favorite state and verify storage plus browse refresh
- [ ] #7 Verify sort-update commands and invalid command arguments
- [ ] #8 Verify unknown custom commands return not supported
- [ ] #9 Search using a seeded local cache fixture
- [ ] #10 Clear app data and reconnect successfully
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add app/src/androidTest/.../shared/service/ServiceBrowser.kt: a hand written helper that builds a real androidx.media3 MediaBrowser against OpenRadioService, runs every browser call on the main looper and awaits its ListenableFuture with an explicit deadline, and records onChildrenChanged and onSearchResultChanged into queues. No mocking or DI framework.
2. OpenRadioServiceBrowseTest: connect with no Activity in any lifecycle stage (asserted through ActivityLifecycleMonitorRegistry), library root id and search-supported extra, root children for an empty profile, favorites and locals nodes after seeding plus their station lists, subscribe and unsubscribe on root and child nodes, onGetItem hit and miss, and a clear-every-store-then-reconnect case.
3. OpenRadioServiceCommandTest: the advertised session command set, an unadvertised action, the local station add/edit/remove refresh loop, the favorite on/off toggle against storage and the root refresh, CMD_UPDATE_SORT_IDS for locals and favorites, and the invalid argument paths (missing and empty media id, absent master volume extra). CMD_STOP_SERVICE is deliberately excluded because closeService() calls Process.killProcess on the process the instrumentation runs in.
4. OpenRadioServiceSearchTest: seed the Room API cache with a Radio Browser search fixture keyed by UrlLayerRadioBrowserImpl.getSearchUrl, then drive browser.search and getSearchResult.
5. Pin the production defects the suite exposes instead of fixing them here, following the task-003 precedent: each pinning test names its follow-up task.
6. Every test clears the favorites, locals, latest station and sleep timer storages and re-invalidates the browse tree in setUp and tearDown, so no case depends on another.
7. Verify with ./gradlew :app:connectedDebugAndroidTest on a headless API 34 emulator with wifi and mobile data disabled, repeated to rule out order dependence, plus ./gradlew test.
<!-- SECTION:PLAN:END -->
