---
id: TASK-004.02
title: Exercise a real MediaBrowser against OpenRadioService
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 05:21'
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
- [x] #8 Verify unknown custom commands are refused and never reach the service, and that every advertised command is handled
- [x] #9 Search using a seeded local cache fixture
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

Round 2 review follow-up.

TASK-030 is fixed and criterion 9 is genuinely met. The second review returned to the same point the first one raised, and it was right that deferring it left the task incomplete: the criterion cannot be satisfied without the production change, so that change was on this task's critical path rather than incidental to it, which is what separates it from the defects TASK-003 filed and left. ModelLayerImpl.downloadData now reads both caches before asking about connectivity. OpenRadioServiceSearchTest asserts the seeded Radio Browser station comes back with the right media id, title and playable flag, and that the push reports one item.

Criterion 8 is reworded rather than documented around. The old wording asked for RESULT_ERROR_NOT_SUPPORTED, which media3 makes unobservable: it refuses an unadvertised action inside the controller, and every advertised action is handled. The criterion now states the contract that exists and is verified, namely that an unknown command is refused, never reaches the service, and that the advertised set equals the handled set.

Three more changes from this round:

- The clear-data case seeds the cache it is meant to empty and waits for that row to disappear. CMD_CLEAR_CACHE answers before its coroutine has run, so its success code proved nothing; reaching the end of that wait is what proves the presenter's clear completed.
- ServiceStorages.clear now resets the provider selection. It decides which URL a fixture has to be keyed to and which nodes the root offers, so a selection left behind by another test would have made the search fixture miss and the test pass on an empty result. OpenRadioServiceSearchTest also asserts the active source before relying on the fixture.
- Each search case now owns a query no other case uses. The service files search results in the browse tree under the query string and never invalidates them, so once search started returning data the cases began answering each other. This surfaced as a real failure and was fixed rather than worked around.

New coverage the cache fix made possible: aCachedProviderNodeIsBrowsableWhileOffline browses the categories node from a seeded response, so a provider backed node is now exercised offline through a real Media3 connection, not only the preference backed ones.

Verification: 116 instrumented tests green twice, plus the 23 service tests alone; ./gradlew test --rerun-tasks, localCoverageReport and instrumentedCoverageReport all pass. Networking confirmed off before every run.

Round 3 review follow-up. Two real test-isolation defects fixed, both in the helper rather than the cases.

ServiceStorages now takes the registry's own storage instances through the single-method injection hooks instead of building parallel ones. The parallel instances shared the preference file but not the memory in front of it, and FavoritesStorage caches every answer it has given about a station while LatestRadioStationStorage caches the station itself, so the service could answer from a cache no test had touched. clear() also removes each favorite through remove() before wiping the file, because the inherited clear() empties the file and leaves the answer cache still saying those stations are favorites.

The provider assertion added in round 2 was vacuous and has been replaced. DependencyRegistryCommon.init reads the source once, at process start, and binds both the URL layer and the root command from that one value, so asserting the preference proved nothing about what the service was using; after clear() it could not fail. The bound value is observable in the root menu, because MediaItemRoot adds the two Radio Browser only nodes from the same Source, so OpenRadioServiceSearchTest now asserts those. Confirmed live by inverting it, which failed with the real root listing.

Criterion 8 stands as reworded, and the reasoning is now in the test rather than only in these notes: the service's RESULT_ERROR_NOT_SUPPORTED is not unverified, it is the answer to an advertised command that cannot be carried out, and three cases observe it over a real connection. What cannot be reached from any client is the fallthrough for an unadvertised action, because media3 refuses those in the controller.

The request for a real pm clear is TASK-031, which weighs running the suite under Test Orchestrator. It is a suite-wide change, and it would not make clear-data something a single test can perform and then assert, so this task's in-process reset stays either way.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A real MediaBrowser exercises OpenRadioService end to end from instrumentation: the library root and its search hint, the root children for an empty and a seeded profile, the favorites and locals nodes, a provider backed node served from a seeded cache, subscriptions on root and child nodes with the pushes they produce, single item lookup, every custom session command the session advertises including the invalid argument paths, search from a seeded fixture, and a clear-every-store-then-reconnect pass. Three test classes and two helpers under app/src/androidTest/.../shared/service, no mocking or DI framework, no network.

One production change was needed rather than filed: ModelLayerImpl.downloadData asked about connectivity before reading either API cache, which made the ninth criterion unsatisfiable, so it was on this task's critical path. It now reads both caches first. That is TASK-030, closed with it, and ModelLayerImplTest changed to match.

Verified on a headless API 34 emulator with wifi and mobile data disabled, networking confirmed off before every run: 116 instrumented tests pass, repeated with no order dependence, and the 23 service tests also pass alone from a force-stopped app, which is the cold service-first start. ./gradlew test --rerun-tasks, localCoverageReport and instrumentedCoverageReport pass. An assertion was inverted on purpose in an earlier round and failed as expected, confirming the suite is not vacuous.

TASK-029 remains open and is pinned by providerNodesNeverAnswerWhenNothingIsCached: a provider node with nothing cached never completes its browse, because the command returns without calling its result listener. CMD_STOP_SERVICE stays uncovered on purpose, since closeService kills the process the instrumentation runs in.
<!-- SECTION:FINAL_SUMMARY:END -->
