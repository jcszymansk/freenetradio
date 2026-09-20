---
id: TASK-006.05
title: 'Journey: settings persistence'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-20 07:46'
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
- [x] #1 Change network, buffering and general settings
- [x] #2 Recreate the Activity and verify their values
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Driven through the real widgets in the three dialogs the navigation drawer opens: the use-mobile box in NetworkDialog, all four fields in StreamBufferingDialog, and the last-known-station box, the custom user agent box and field, and the default country spinner in GeneralSettingsDialog. Between them they cover three preference files - NetworkSettingsStorage, OpenRadioPref and LocationPref - and three write paths: straight from a listener, from the dialog's onPause, and through MediaPresenter.

Leaving a dialog is part of the journey rather than cleanup. StreamBufferingDialog validates its four values together and writes them all in onPause, and GeneralSettingsDialog writes the user agent there too, so nothing reaches a store until the fragment is taken off the Activity. JourneyDialogs.dismiss waits for that rather than for the transaction to be queued.

Every dialog is left before the recreate, and the case asserts no dialog is up before calling it. A dialog still up is restored by the framework with its own view state, so reading the widgets afterwards would pass whether or not anything had been written. recreatingTheActivityWithTheBufferingDialogUpStoresWhatTheUserHadTyped covers that half on purpose, because the pause the recreate runs is what commits what the user had typed - the rotation case.

Confirmed by experiment rather than by reading. Removing the buffering dismiss fails three cases on 'the buffer values did not reach the store', and leaving the general dialog up fails the recreate case on the guard instead of passing on restored views. Both recreate cases also assert the Activity identity changed, since a recreate that silently handed the same Activity back would pass everything after it.

Two settings on those dialogs are left alone and the class says why: the Bluetooth auto-play box requests a runtime permission on API 31+ and would put a system prompt over the Activity, and the master volume bar only persists from onStopTrackingTouch, which a programmatic progress never fires. OpenRadioServiceCommandTest already covers the master volume command.

A recreate never leaves the process, so theChangedSettingsReachDiskRatherThanOnlyTheRunningProcess reads the preference files themselves: every store writes with apply(), which makes a value readable here long before a restart could find it. A genuinely new process per test is still TASK-031.

tearDown clears every preference file. ServiceStorages.clear owns none of these three, and a chosen country alone decides the last row of the root menu for every journey after this one.

JourneyDialogs took over awaitDialog and dialogView from LocalStationLifecycleJourneyTest, which now uses it.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added SettingsPersistenceJourneyTest, the fifth phone journey, and extracted JourneyDialogs from LocalStationLifecycleJourneyTest so both read dialogs through one helper.

Four cases: changing one setting in NetworkDialog, four in StreamBufferingDialog and three in GeneralSettingsDialog stores what was typed; recreating the Activity with every dialog left brings all of them back up in freshly opened dialogs; the values reach their preference files on disk rather than only this process; and recreating with the buffering dialog still up is itself what commits what the user had typed.

Verified on a clean API 34 emulator with wifi and mobile data disabled and the app's data cleared: ./gradlew :app:connectedDebugAndroidTest ran 202 tests with 0 failures, 0 errors and 0 skipped, and ./gradlew test --rerun-tasks passed. The assertions were checked by mutation as well: dropping the buffering dismiss fails three cases on the store, and leaving the general dialog up fails the recreate case on its guard.
<!-- SECTION:FINAL_SUMMARY:END -->
