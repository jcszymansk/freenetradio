---
id: TASK-006.06
title: 'Journey: service-first startup'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 10:47'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Android Auto starts the service before any Activity exists, so this ordering must work.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Start and browse the service before opening the Activity
- [x] #2 Open the Activity and verify consistent state
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add ServiceFirstStartupJourneyTest, the sixth phone journey. Profile as every other journey: cleared data, offline, Radio Browser bound, one device-local station seeded through LocalStationsFixture and pointed at a generated WAV.
2. AC1 is asserted rather than assumed. OpenRadioServiceBrowseTest already asks privately whether the process holds an Activity, and that is registry-wide state rather than a property of the class asking, so extract it to a shared ActivityPresence next to the other service fixtures and point the browse test at it. It reads ActivityLifecycleMonitorRegistry on the main looper, skips DESTROYED so an earlier class's torn-down Activity cannot make the check order-dependent, names each Activity and its stage so a failure says what is holding the process, and offers awaitNone for the front of a bracket where one is still going away.
3. AC1 case: with no Activity alive, the ServiceBrowser JourneyProfile connected answers the library root, the whole offline root menu, the locals node and - after the favorite command - the favorites node. Marking from the session is CMD_FAVORITE_OFF: both favorite commands name the state the control is leaving, and CMD_FAVORITE_ON would unmark silently, answering RESULT_SUCCESS and notifying the root anyway. The browser subscribes to the root first, because notifyChildrenChanged reaches subscribers only and a client showing a list subscribes to it anyway. ActivityManager.getRunningServices says OpenRadioService is up while the process holds no Activity, which is the Android Auto contract stated positively.
4. AC2 cases, each starting from a headless browse and only then launching MainActivity: the rendered root equals the ids the service answered before the Activity existed; a station marked through the service before the Activity existed comes up marked in both the locals list and the favorites node the root grew; and a station started through the session before the Activity existed is on the bar the Activity puts up, with the queue and the current item unchanged by the Activity connecting.
5. One case for the other half of the ordering: closing the Activity leaves the service answering the same tree and still playing, which is what keeps a head unit working when the phone app is gone.
6. Say what the suite cannot reach: the service shares this process, so it is already created by the time this class runs and no test can make it start fresh. What is pinned is the Activity-relative ordering; a genuinely new process per test is TASK-031.
7. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data, and check the guard, the mark and the playback by mutation.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Every case brackets its work with ActivityPresence rather than relying on having launched no Activity. The process is shared with every other test class, so 'launched none' and 'holds none' are different claims; the setup waits for the previous class's scenario to let go, then asserts again after the connect, refresh and browse.

CMD_FAVORITE_ON unmarks. Both favorite commands name the state the control is leaving, which is also how the phone's own check box sends them, so marking a station from the session is CMD_FAVORITE_OFF. Sending the wrong one is silent: the service removes a station that was never there, answers RESULT_SUCCESS and still notifies the root, so only the store read afterwards catches it.

notifyChildrenChanged reaches subscribers only, so the browser subscribes to the root before the mark. That is what a client showing a list does anyway, and it is the only way the push is observable from here.

JUnit runs tearDown whether or not setUp finished, and the journey's first act can fail, so cleanup was able to be handed a fixture that did not exist yet. Reproduced: an early throw in setUp came back as 'lateinit property mStations has not been initialized' with the real failure gone from the console.

Fixed where it comes from rather than with isInitialized guards, which would be branches no green run ever takes. The fixtures are plain fields, since constructing them connects to nothing; JourneyProfile.finish skips the tree refresh when the browser never connected, which is the path a run with networking still on takes, so that operator now reads the message telling them to disable it; and LocalStationsFixture.parkThePlayer returns before touching the browser when it seeded nothing. ServiceBrowser gained isConnected for the one caller that has to ask.

All six journeys benefit: every one of them calls JourneyProfile.finish from a tearDown that runs after a failed start.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added ServiceFirstStartupJourneyTest, the sixth and last phone journey, and extracted ActivityPresence from OpenRadioServiceBrowseTest so the service tests and the journeys ask one shared question about what the process is holding.

Five cases, each bracketed by ActivityPresence rather than by having launched nothing. AC1: the service answers the library root, the offline catalogue, the locals node and - after a favorite command from the session - the favorites node, while the process holds no Activity and ActivityManager reports the service itself running. AC2: the Activity that opens afterwards renders exactly the root the service had already answered; finds the station marked before it existed marked in both the favorites node and the locals list; and raises its now-playing bar for the station the session was already playing, without moving the player or rebuilding its queue. A fifth case reads the ordering backwards, closing the Activity and finding the service still serving and still playing.

Verified on a clean API 34 emulator with wifi and mobile data disabled, from a fresh install: ./gradlew :app:connectedDebugAndroidTest ran 207 tests with 0 failures and 0 skipped, up from 202, and ./gradlew test --rerun-tasks passed. The assertions were checked by mutation as well: launching an Activity before the guard fails it naming 'MainActivity in RESUMED'; dropping the favorite command fails the marked root; and dropping the playback leaves the bar showing the previous case's station, which is what the run-wide unique station name is there to separate.
<!-- SECTION:FINAL_SUMMARY:END -->
