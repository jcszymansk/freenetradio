---
id: TASK-006.02
title: 'Journey: local station lifecycle'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 19:21'
labels: []
milestone: m-0
dependencies:
  - TASK-005
parent_task_id: TASK-006
type: chore
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The local-station path is the only fully offline content source, so it is the backbone of the offline suite.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Add a local station through the dialog
- [x] #2 Locals appears immediately
- [x] #3 Edit and remove the station from its rendered row in the locals list. Opening that list by tapping its row is TASK-049's criterion 6, because the offline gate refuses the tap
- [x] #4 The station survives the Activity that added it: it is on disk, a newly launched Activity rebuilds the root from the store, and a storage instance built from scratch reads it back. Crossing a real process boundary is TASK-031's criterion 4, because the service shares this process
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extract the browse-list reading ColdLaunchJourneyTest grew (rows by adapter position, paired with the text each row's own view shows) into a BrowseListView helper in the journey package, and repoint that test at it, so the two journeys read the list the same way and there is one copy.
2. Add LocalStationLifecycleJourneyTest under app/src/androidTest/.../mobile/journey. Its @Before starts from the cold launch preconditions: offline, cleared stores, Radio Browser bound. It also grants READ_MEDIA_IMAGES through UiAutomation, so the add and edit dialogs never raise the image-picker permission request over the Activity.
3. AC1 and AC2 in one case: click the real add_station_btn, let MainActivity show the real AddStationDialog, type a name and a stream url served by LoopbackHttpFixture, and click Add. The real validator, RadioStationManagerLayerImpl and DeviceLocalsStorage run. The url is loopback because the validator probes the stream over HTTP and loopback is the one address reachable with networking disabled. Then assert the rendered root list grows the Locals row with no further user action, which is the CMD_UPDATE_TREE the dialog sends arriving as a subscription push.
4. AC3: drive the real RSSettingsDialog for the station's browsed MediaItem, assert it offers edit and remove only because the parent is the locals node, and click through to the real EditStationDialog and RemoveStationDialog. Assert the edit lands in storage and in what the service serves, and that removing the last local station takes the Locals row off the rendered root.
5. AC4: assert the station is in the on-disk preference file, not only in memory, and that a freshly launched Activity and a storage instance built from disk both still show it. A process restart is not available in process, which is TASK-031, so say what this does and does not prove.
6. Assert the offline gate found on the way: with the Locals row on screen, tapping it leaves the list where it was. TASK-049 holds the fix.
7. Run ./gradlew test and the full :app:connectedDebugAndroidTest with networking disabled, from cleared app data.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Scope decision: kept to tests. Offline, MediaPresenterImpl.handleItemSelected refuses every browse tap, and it is the only tap-driven way into a node, so the locals list cannot be opened and there is no station row to swipe for the settings button. TASK-049 holds the fix; this journey asserts the gate instead and reaches the edit and remove dialogs through the settings dialog's own arguments.

Verified that gate by experiment rather than by reading: with the connectivity check removed from handleItemSelected, theLocalsRowDoesNotOpenWithoutANetwork fails and its diff shows the locals list open on the user's station. So TASK-049 is a real fix, not a guess, and the test is the guard that will change answer when it lands.

The same experiment turned up an order hazard in the suite itself. MediaPresenterImpl is one of the registry's singletons and its node stack outlives the Activity: init walks back into the last node on the stack, so a case that navigated leaves the next Activity somewhere other than the root, in the same process. With the gate removed, two later cases failed with an empty list for exactly that reason. Every case now asserts it starts at the root rather than assuming it, which is what Phase 6's no-order-assumptions exit check needs.

The stream url is served by LoopbackHttpFixture because RadioStationValidatorImpl probes the stream over HTTP before the add is accepted, and loopback is the one address reachable with networking disabled. The home page is left empty on purpose: an unreachable home page is reported as a failed add even though the station is stored, which is TASK-038.

READ_MEDIA_IMAGES is granted through UiAutomation in setUp. BaseAddEditStationDialog.onResume asks for it, and on a freshly cleared install that puts a system prompt over the Activity the journey is reading.

Extracted JourneyProfile and BrowseListView out of ColdLaunchJourneyTest first, so the four journeys still to come share one copy of the device state a journey starts from and one copy of reading the list by adapter position.

Review round 1. The relaunch in theStationOutlivesTheActivityThatAddedIt was reading the service's cached root, so it asserted nothing about the store: the root is the one node callWhenSourceReady does serve from mBrowseTree. Proved it by emptying the locals store before the relaunch, which the case passed, then failed two assertions later on the storage read. Dropping the cached tree first makes the relaunch rebuild the root through MediaItemRoot, and the same experiment then fails at the relaunch as it should.

The two structural gaps the review named are now acceptance criteria on the tasks that own them rather than prose in a test comment: TASK-031 carries re-running the persistence case across a real process, TASK-049 carries driving the rendered locals row and its settings action and replacing the offline-gate assertion with its opposite.

Review round 2 raised the same two structural gaps again. The edit and remove one was fixable after all, so it was fixed rather than argued: the journey now opens the locals list through MediaPresenter.addMediaItemToStack, which is the step handleItemSelected performs once past the connectivity gate, then finds the station's rendered row and clicks its real settings button. handleItemSettings builds the settings dialog's arguments from the node the presenter is standing in, so nothing about that dialog is assembled by the test any more. Only the tap that opens the node is still missing, and TASK-049's criterion now says exactly that.

Navigating means the cases have to walk back out, because the presenter's stack outlives the Activity. returnToRoot pops with handleBackPressed while the current category is not the root, never at the root, where handleBackPressed sends CMD_STOP_SERVICE and kills the process. It asserts nothing, because it runs from a finally and an assertion there would replace whatever failure sent the case into it.

The restart gap stands and cannot be closed from here: no component declares android:process, so the service, the registry and the instrumentation are one process, and killing it ends the run. TASK-006.01 accepted the same shape for its own 'clear application data' criterion, satisfied by AppDataReset rather than a real pm clear, with the remainder on TASK-031. Treating this one differently would make the phase inconsistent with itself, so AC4 stays checked and TASK-031 carries the process restart as an acceptance criterion.

Review round 3 repeated both structural findings with no implementable part left: the reviewer's own suggested fixes are conditioned on the offline gate (TASK-049) and a process-isolated harness (TASK-031), both scoped out of this task by the user. Put the question to the user, who chose to reword the two criteria to what this suite proves rather than leave the tracker over-claiming or block Phase 6.

AC3 and AC4 now state what is asserted and name the task that owns the rest, so the criterion and the evidence match and neither gap can be read as covered here. Nothing in the tests changed.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Adds LocalStationLifecycleJourneyTest, the second of the six Phase 6 phone journeys, and moves the shared half of the first one into JourneyProfile and BrowseListView.

Five cases, each starting from a cleared, offline profile and driving MainActivity. Everything below the UI is the real thing: the real RadioStationValidatorImpl, RadioStationManagerLayerImpl and DeviceLocalsStorage. The stream url is served by LoopbackHttpFixture because the validator probes the stream over HTTP before it accepts a station, and loopback is the one address that answers with networking disabled.

AC1, add through the dialog: the journey clicks the add button the root list shows, fills the real AddStationDialog and submits it. The stored station is read back through a storage instance the test built, so nothing the writer cached can answer for it, and the loopback server is asked whether the validator actually probed the stream, so a station accepted unchecked fails.

AC2, locals appears immediately: the root list is asserted to be the clean install catalogue before the add and to have grown the Locals row after it, with nothing in between done by the test. That is the dialog's CMD_UPDATE_TREE arriving as a subscription push and the adapter being rewritten.

AC3, edit and remove: both go through the real settings dialog, which is asserted to offer its edit and remove buttons only because the parent is the locals node, then through the android:onClick that lands in MainActivity, and into the real EditStationDialog and RemoveStationDialog. The edit dialog is asserted to have loaded the station it was opened for before anything is retyped, so a blind overwrite fails. The removal is asserted where an offline user can see it: the Locals row leaves the root list.

AC4, restart and persistence: the station is asserted to have reached a preference file on disk rather than only the copy Android keeps in memory, that file is asserted to be the locals store, a newly launched Activity is asserted to build its root list from it, and a DeviceLocalsStorage constructed by the test agrees.

Verified on emulator-5554, a clean API 34 AVD, with wifi and mobile data disabled and the app's data cleared: the full instrumented suite passes at 186 tests, up from 181, and ./gradlew test --rerun-tasks passes.

Non-vacuous by mutation: expecting the old name after the edit fails with the new one, and expecting the root without Locals in the offline-gate case fails with it. Removing the connectivity check from handleItemSelected makes theLocalsRowDoesNotOpenWithoutANetwork fail with the locals list open on the station, which is what TASK-049 asks for and is how that task was confirmed rather than guessed.

That experiment also exposed an order hazard: MediaPresenterImpl is a registry singleton whose node stack outlives the Activity, so a case that navigates leaves the next one somewhere other than the root. Every case now asserts its starting node instead of assuming it.
<!-- SECTION:FINAL_SUMMARY:END -->
