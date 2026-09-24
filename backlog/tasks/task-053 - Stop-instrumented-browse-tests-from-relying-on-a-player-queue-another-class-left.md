---
id: TASK-053
title: >-
  Stop instrumented browse tests from relying on a player queue another class
  left
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 17:45'
updated_date: '2026-09-24 08:37'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 68000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService calls maybeCreateInitialPlaylist on any page-0 browse. With an empty player queue and an active station stored, that path calls getNewStations, which downloads stations/lastchange/250, and then handlePlayRequestUiThread, which starts playing whatever came back. This is the one path in the suite that could put internet audio through the speakers during a test run. The service is process-wide and outlives every test class, so "the queue is not empty" is currently an accident of ordering rather than an asserted invariant. Six classes browse the root without calling LocalStationsFixture.parkThePlayer: OpenRadioServiceBrowseTest, OpenRadioServiceSearchTest, ColdLaunchJourneyTest, FavoriteLifecycleJourneyTest, LocalStationLifecycleJourneyTest and SettingsPersistenceJourneyTest. parkThePlayer itself has three silent early returns, the last being mItems.firstOrNull() ?: return, so a class whose setup failed before seeding leaves exactly the state that arms the path for the next class, and nothing reports that it happened. Found while auditing criterion 7 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every instrumented class that browses page 0 either parks the player or asserts the queue is not empty before it does
- [x] #2 parkThePlayer reports when it cannot park instead of returning silently
- [x] #3 The invariant holds when a class runs alone and when it runs after any other class
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Have OpenRadioService publish its active station id in the session extras (EXTRA_ACTIVE_STATION_ID) in onCreate and on playback, since the active station is otherwise private and no test-only signal can tell it exactly.
2. Make ServiceBrowser.connect refuse a service a page-0 browse would start playing (empty queue plus a published active station), so every class that connects through it asserts the precondition structurally.
3. Make parkThePlayer fail when it cannot put a queue back into such a service, and log the harmless early returns.
4. Assert the same precondition in the classes that reach the service without a ServiceBrowser (MediaResourcesManagerTest, AddStationDialogTest, EditStationDialogTest), and keep teardowns from burying a refused setup.
5. Cover both guards and the published id with InitialPlaylistGuardTest, which builds the armed state on purpose.
6. Verify the full suite, every service-reaching class alone, and a mixed ordering.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The service's active station is private. The guard reads two signals that each follow from it: the session custom layout (the favorite button, set on onCreate adoption and playback, never cleared) and the registry latest-station store. Layout alone was tried first and false-fired: favorite commands set the layout with no active station, which failed OpenRadioServiceCommandTest when run alone. The store alone misses a running service whose store was cleared after adoption. Requiring both closes each one's false positive; the remaining blind spot is a service whose queue and store were both emptied after adoption, which only a class emptying both itself or a teardown whose parkThePlayer already failed can leave.

Scope: AC1 names six classes, but MediaResourcesManagerTest, AddStationDialogTest and EditStationDialogTest also browse page 0 (an Activity's own browser browses the root on connect), so they assert the precondition too. The assertion is 'queue not empty, or no active station' rather than 'queue not empty', because a class run alone in a fresh process has an empty queue and must still pass (AC3).

The five service classes skip their teardown CMD_UPDATE_TREE when the browser is not connected, because on-device the unguarded call's 'Browser is not connected' was the failure reported in place of the setup refusal.

Superseded the two-signal guard after review: every class clears the latest-station store before connecting and every teardown clears it after parking, so the store signal was always empty at connect and the connect guard could not fire across classes. With the user's approval the service now publishes its active station id in the session extras (OpenRadioService.EXTRA_ACTIVE_STATION_ID, set in onCreate and on playback), which a controller receives on connect. The guard reads that, so it is exact: no favorite-command false positive and no blind spot after a store clear. This is a production seam, added because no test-only signal could answer the question.

Validation: ./gradlew test assembleDebug PASS; :app:connectedDebugAndroidTest full suite 216/216 on an offline API emulator after pm clear; every service-reaching class run alone after pm clear PASS (Browse 14, Search 4, Recovery 7, Command 12, Playback 11, InitialPlaylistGuard 7, ColdLaunch 3, FavoriteLifecycle 5, LocalStationLifecycle 5, SettingsPersistence 4, OfflinePlayback 6, ServiceFirstStartup 5, MediaResourcesManager 2, AddStationDialog 1, EditStationDialog 2); a mixed ordering of seven classes PASS. Codex review loop: round 1 FAIL (store signal blind at connect), round 2 PASS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Instrumented tests no longer rely on the queue an earlier class left. OpenRadioService publishes its active station id in the session extras. ServiceBrowser.connect refuses (and releases) a service with an empty queue and an active station, so every class connecting through it asserts the precondition before its first page-0 browse. MediaResourcesManagerTest and the two dialog tests assert the same before their Activity or manager connects. parkThePlayer now fails its teardown when it cannot park an armed service and logs its benign early returns, and the service test teardowns skip browser calls after a refused setup so the refusal stays the reported failure. InitialPlaylistGuardTest builds the armed state on purpose and checks the refusal (also after a store clear), the park failure, parking, and the published id. Verified with the full offline instrumented suite (216/216), every service-reaching class run alone, a mixed ordering, and the JVM suite.
<!-- SECTION:FINAL_SUMMARY:END -->
