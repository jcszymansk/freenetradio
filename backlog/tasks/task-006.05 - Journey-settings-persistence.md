---
id: TASK-006.05
title: 'Journey: settings persistence'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 07:33'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Settings survive Activity recreation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Change network, buffering and general settings
- [ ] #2 Recreate the Activity and verify their values
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the two dialog helpers LocalStationLifecycleJourneyTest keeps private (awaitDialog, dialogView) into a shared JourneyDialogs, and give it the path the settings dialogs are actually reached by: NavigationView.menu.performIdentifierAction, which runs MainActivity's own OnNavigationItemSelectedListener, plus a dismiss that waits for the fragment to be gone. Point the local station journey at it.
2. Add SettingsPersistenceJourneyTest. Change a setting in each of the three dialogs the task names, through the widgets a user touches: the use-mobile box in NetworkDialog, the four buffer fields in StreamBufferingDialog, and the last-known-station box, the custom user agent box and field, and the default country spinner in GeneralSettingsDialog.
3. Leave the Bluetooth auto-play box and the master volume bar alone, and say why: the first asks for a runtime permission on API 31+ and would put a system prompt over the Activity, and the second only persists from onStopTrackingTouch, which a programmatic progress never fires. OpenRadioServiceCommandTest already covers the master volume command.
4. AC1: assert the defaults first, change every setting, then assert each store holds what was typed. Buffering and the custom user agent are written only in the dialog's onPause, so leaving the dialog is what commits them.
5. AC2: dismiss every dialog, recreate the Activity with ActivityScenario.recreate(), reopen the three dialogs and read the values off the widgets. Dismissing first is what makes that a storage read rather than a fragment restoring its own view state.
6. Add the disk check: each value has to be in its own preference file rather than only in this process's SharedPreferences, since every store writes with apply().
7. Add the case where the Activity is recreated with the buffering dialog still up, which is the pause that commits what the user had typed.
8. Clear every preference file in tearDown. ServiceStorages.clear does not own OpenRadioPref, NetworkSettingsStorage or LocationPref, so leaving them would hand the next class a chosen country and changed buffers.
9. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data.
<!-- SECTION:PLAN:END -->
