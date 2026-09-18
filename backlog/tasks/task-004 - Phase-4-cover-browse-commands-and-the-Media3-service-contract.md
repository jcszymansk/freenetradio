---
id: TASK-004
title: 'Phase 4: cover browse commands and the Media3 service contract'
status: Done
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-18 18:52'
labels: []
milestone: m-0
dependencies:
  - TASK-003
  - TASK-032
type: chore
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Protects the surface shared by the phone UI and Android Auto. Both clients browse the same tree through OpenRadioService, so a defect here is visible in the car.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Exit check: all offline browse nodes and supported custom commands are exercised through a real Media3 connection, including cold service startup without an Activity
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Exit check verified on the merged tree at 3e1cbcd, with emulator networking disabled and confirmed off (adb shell svc wifi disable, svc data disable; dumpsys reported "Active default network: none").

JVM: ./gradlew test --rerun-tasks, 143 tests in :common, 17 in :android-jvm-stubs, 3 in :common-ui, no failures or errors. The rerun matters, because an ordinary ./gradlew test on this tree reports every task up to date and runs nothing.

Instrumented: ./gradlew :app:connectedDebugAndroidTest on emulator-5554, API 34, 123 tests with no failures, errors or skips. The service classes account for 29 of them: OpenRadioServiceBrowseTest 14, OpenRadioServiceCommandTest 11, OpenRadioServiceSearchTest 4.

Every browse node the presenter registers is now browsed with children over a real Media3 connection: root, favorites, locals, popular, categories, countries, new, child categories and country stations, plus search through onSearch and onGetSearchResult, the empty-cache answer and the rejection of a parent id no command serves. Eight of the nine advertised custom commands are sent, and the advertised set is asserted to equal the handled set. Cold service start with no Activity in any lifecycle stage is covered.

Two deliberate exclusions, both recorded rather than argued:

- CMD_STOP_SERVICE is never sent, because closeService ends in Process.killProcess on the process the instrumentation runs in. doc/testing-roadmap.md keeps that path as a manual lifecycle check.
- The car root is not reachable from instrumentation at all. isCar is read once per process from UiModeManager at DependencyRegistryCommon.kt:113 and frozen into the presenter command map, so no test can flip it and no client can ask for the car root. That is TASK-033, which carries its own criterion to exercise the car root over a real connection once the root is selected per connecting client. Auditing this criterion is what found it.

The one browse node that could not be exercised was deleted instead: TASK-032 removed MediaItemSearchFromApp, which no client could reach because MediaResourcesManager intercepts __SEARCH_FROM_APP__ and calls getSearchResult. TASK-004 depended on it for exactly that reason.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The browse tree the phone UI and Android Auto share is covered at two levels. TASK-004.01 put OpenRadioServicePresenterImpl and all thirteen MediaItemCommand implementations under hand-written fakes, 80 JVM tests, after adding the :android-jvm-stubs module that gives JVM tests a working Uri, Bundle, MimeTypeMap and Context resource lookup. TASK-004.02 drove a real MediaBrowser against the running OpenRadioService from instrumentation: root, the store backed nodes, subscriptions, single item lookup, every advertised custom command with its invalid argument paths, search from a seeded fixture, and clear-data then reconnect. TASK-004.03 finished the browse half by serving the five remaining provider nodes from responses seeded into the Room API cache under the exact URL each command builds, so a fetch backed node is browsed with children while the device has no network.

Three production defects were on the critical path and were fixed rather than filed, because the criteria could not be met around them: ModelLayerImpl.downloadData asked about connectivity before reading either API cache (TASK-030), LatestRadioStationStorage.clear left its own cached station behind (TASK-027), and MediaItemAllCategories and MediaItemCountriesList returned without calling their result listener on an empty provider result, which left a browse request pending forever and is the ordinary offline case (TASK-029). TASK-032 deleted MediaItemSearchFromApp, a browse command no client could reach.

Verified on the merged tree with emulator networking disabled and confirmed off: 123 instrumented tests on API 34 and 163 JVM tests across three modules, all green. The car root stays uncovered by instrumentation and is TASK-033, which has to select the root from the connecting client before any test can reach it.
<!-- SECTION:FINAL_SUMMARY:END -->
