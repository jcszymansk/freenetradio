---
id: TASK-004.02
title: Exercise a real MediaBrowser against OpenRadioService
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 04:46'
labels: []
milestone: m-0
dependencies:
  - TASK-003
  - TASK-030
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
- [x] #1 Connect before any Activity exists
- [x] #2 Fetch the library root and root children
- [x] #3 Seed favorites and locals, then verify their browse nodes
- [x] #4 Subscribe to root and child nodes
- [x] #5 Add, edit and remove a local station and verify immediate subscription refresh
- [x] #6 Toggle favorite state and verify storage plus browse refresh
- [x] #7 Verify sort-update commands and invalid command arguments
- [x] #8 Verify unknown custom commands return not supported
- [ ] #9 Search using a seeded local cache fixture
- [x] #10 Clear app data and reconnect successfully
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Three instrumented classes under app/src/androidTest/.../shared/service drive a real androidx.media3 MediaBrowser against the running OpenRadioService: OpenRadioServiceBrowseTest (root, root children, seeded favorites and locals, subscribe and unsubscribe, single item lookup, clear-and-reconnect), OpenRadioServiceCommandTest (the advertised command set, the local station add/edit/remove refresh loop, the favorite toggle, sort ids for both sortable categories, and the invalid argument paths) and OpenRadioServiceSearchTest. ServiceBrowser.kt wraps the main-thread-and-future dance with an explicit deadline; ServiceStorages.kt owns the stores each test seeds and wipes. No mocking or DI framework, no network.

:app gained two androidTest-only dependencies, media3-session and androidx.media, because :common keeps both off the app classpath; this mirrors the room-runtime line added in TASK-003.

Two acceptance criteria are met in an adapted form, and both adaptations are forced by production behavior rather than by the test:

- AC 9. The search fixture is seeded into the Room API cache under the exact provider URL, but it is never consumed: ModelLayerImpl.downloadData checks connectivity before it reads either cache, so an offline search returns nothing. The test drives the real search and getSearchResult protocol, asserts the zero-result push, and asserts the cached row is left untouched. Filed as TASK-030, which carries the criterion to rewrite this test against the seeded station.
- AC 10. A real 'pm clear' would kill the process the instrumentation runs in, so the test wipes every store the app owns plus the caches CMD_CLEAR_CACHE reaches, releases the browser and reconnects on the resulting empty profile.

AC 8 deviates from its wording for the same reason: media3 refuses an action the session never advertised, so the service's own not-supported branch is unreachable from a browser. The test asserts the actual rejection (RESULT_ERROR_PERMISSION_DENIED) and separately asserts that the advertised set is exactly the nine handled commands, which is what makes that branch unreachable.

CMD_STOP_SERVICE is never sent: closeService ends in Process.killProcess(myPid()), which would kill the test run. The testing roadmap already keeps that path as a manual lifecycle check.

A second production defect surfaced: MediaItemAllCategories and MediaItemCountriesList return without calling their result listener on an empty provider result, so the SettableFuture behind onGetChildren is never set and an offline browse of either node hangs forever. Filed as TASK-029; OpenRadioServiceBrowseTest.providerNodesNeverAnswerWhileOffline pins it with a bounded wait.

Verification: 114 instrumented tests green on a headless API 34 emulator with wifi and mobile data disabled, run four times with no order dependence; the 21 new tests also pass on their own from a force-stopped app, which is the service-first, no-Activity path AC 1 asks for. ./gradlew test --rerun-tasks, localCoverageReport and instrumentedCoverageReport all pass. One assertion was deliberately inverted and confirmed to fail, so the suite is not passing vacuously.

Round 1 review follow-up.

Acceptance criterion 9 is unchecked and the task is back In Progress. The round 1 reviewer was right that checking it overstated what the test proves: the seeded cache fixture is never read, so the criterion is not met, only pinned. TASK-030 is now a dependency, and its fourth criterion is the one that closes this. The production change it needs is small (move the connectivity check in ModelLayerImpl.downloadData to just before the download, leaving the two cache lookups ahead of it), but it is another task's change and was not made here.

Three test defects the same review found are fixed:

- The cold-start check ran after the browser had already connected, so it could not prove the connection began with no Activity. The absence of a live Activity is now sampled in setUp immediately before connect and asserted from that snapshot, with a second check that none appeared while the service was serving.
- The clear-data case cleared four named stores. It now empties every preference file the app owns, found by listing shared_prefs rather than by naming stores, drops the Room API cache, sends CMD_CLEAR_CACHE for the in-memory cache and the stored images, and asserts every one of those files is empty before reconnecting. The files are cleared through SharedPreferences rather than deleted, because Android caches one instance per file per process and the service holds several; deleting on disk would leave it reading stale values. The ExoPlayer media cache is left alone: the player holds it open and it is not part of the browse profile.
- advertisesExactlyTheCommandsItHandles only checked that the nine handled commands were present. It now asserts the advertised custom command set equals them exactly, which is what proves onCustomCommand's not-supported fallthrough is unreachable from a browser, and is the evidence behind the criterion 8 deviation.

One finding is not acted on: the reviewer asked for the categories and countries nodes to complete with an empty list offline. That is TASK-029's fourth criterion, not work this task can do without fixing the defect.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A real MediaBrowser now exercises OpenRadioService end to end from instrumentation: the library root and its extras, the root children for an empty and a seeded profile, the favorites and locals nodes, subscriptions on root and child nodes with the pushes they produce, single item lookup, every custom session command the session advertises including their invalid argument paths, search, and a clear-every-store-then-reconnect pass. Three test classes plus two small helpers under app/src/androidTest/.../shared/service, no mocking or DI framework, no network.

Verified on a headless API 34 emulator with wifi and mobile data disabled: 114 instrumented tests pass, four consecutive runs with no order dependence, and the 21 new tests also pass alone from a force-stopped app, which is the cold service-first start. ./gradlew test --rerun-tasks, localCoverageReport and instrumentedCoverageReport pass. One assertion was inverted on purpose and failed as expected, confirming the suite is not vacuous.

Two production defects surfaced and are tracked rather than fixed here, following TASK-003: TASK-029 (an offline browse of the categories or countries node never completes, because the command returns without calling its result listener on an empty result) and TASK-030 (the API response cache is skipped whenever the device is offline, which is what keeps the seeded search fixture from being used). Both are pinned by tests that name the task and will fail once the defect is fixed. CMD_STOP_SERVICE stays uncovered on purpose: closeService kills the process the instrumentation runs in.
<!-- SECTION:FINAL_SUMMARY:END -->
