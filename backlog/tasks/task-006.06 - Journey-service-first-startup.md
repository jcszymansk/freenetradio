---
id: TASK-006.06
title: 'Journey: service-first startup'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 10:28'
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
- [ ] #1 Start and browse the service before opening the Activity
- [ ] #2 Open the Activity and verify consistent state
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add ServiceFirstStartupJourneyTest, the sixth phone journey. Profile as every other journey: cleared data, offline, Radio Browser bound, one device-local station seeded through LocalStationsFixture and pointed at a generated WAV.
2. AC1 is asserted rather than assumed: a private aliveActivities() reads ActivityLifecycleMonitorRegistry on the main looper and reports every Activity in a stage other than DESTROYED, so 'no Activity exists' is a fact the case checks instead of a claim the setup makes. The setup waits for it, because the previous class's scenario is what has to have let go.
3. AC1 case: with no Activity alive, the ServiceBrowser JourneyProfile connected answers the library root, the whole offline root menu, the locals node and - after CMD_FAVORITE_ON - the favorites node. ActivityManager.getRunningServices says OpenRadioService is up while the process holds no Activity, which is the Android Auto contract stated positively.
4. AC2 cases, each starting from a headless browse and only then launching MainActivity: the rendered root equals the ids the service answered before the Activity existed; a station marked through the service before the Activity existed comes up marked in both the locals list and the favorites node the root grew; and a station started through the session before the Activity existed is on the bar the Activity puts up, with the queue and the current item unchanged by the Activity connecting.
5. One case for the other half of the ordering: closing the Activity leaves the service answering the same tree and still playing, which is what keeps a head unit working when the phone app is gone.
6. Say what the suite cannot reach: the service shares this process, so it is already created by the time this class runs and no test can make it start fresh. What is pinned is the Activity-relative ordering; a genuinely new process per test is TASK-031.
7. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Every case brackets its work with ActivityPresence rather than relying on having launched no Activity. The process is shared with every other test class, so 'launched none' and 'holds none' are different claims; the setup waits for the previous class's scenario to let go, then asserts again after the connect, refresh and browse.

CMD_FAVORITE_ON unmarks. Both favorite commands name the state the control is leaving, which is also how the phone's own check box sends them, so marking a station from the session is CMD_FAVORITE_OFF. Sending the wrong one is silent: the service removes a station that was never there, answers RESULT_SUCCESS and still notifies the root, so only the store read afterwards catches it.

notifyChildrenChanged reaches subscribers only, so the browser subscribes to the root before the mark. That is what a client showing a list does anyway, and it is the only way the push is observable from here.
<!-- SECTION:NOTES:END -->
