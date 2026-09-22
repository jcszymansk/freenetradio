---
id: TASK-059
title: Cover the local station home page on add and on edit
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 17:46'
updated_date: '2026-09-22 13:49'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 74000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
13cc159 fixed two halves of one silent data loss: addRadioStation never persisted homePage, and EditStationDialog never prefilled the field from the stored station, so a user who typed a home page lost it on add and lost it again on every edit. Neither half has a regression test. RadioStationManagerLayerImplTest exists but never reads a station back, because it runs on ContextWrapper(null) and its writes go nowhere, and no test opens EditStationDialog at all. The add half can use the preferencesContext() fake that common/src/test already has. Found while auditing criterion 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Adding a station with a home page stores that home page and a test reads it back
- [x] #2 Opening the edit dialog for a stored station shows its home page
- [x] #3 The add-side test runs on the JVM against a preferences fake rather than on a device
- [x] #4 RadioStationManagerLayerImpl and RadioStationToAdd clear the per-class line floor in the JVM coverage report, which replacing ContextWrapper(null) with a preferences fake should achieve as a side effect
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Measured the starting point: verifyPureCoreCoverage puts RadioStationToAdd at 4/12 lines and
   RadioStationManagerLayerImpl at 10/18. The task's premise that a preferences fake clears both
   floors as a side effect does not hold. The manager's uncovered outer-class lines are all in
   editRadioStation (2) and removeRadioStation (6); addRadioStation and the constructor are
   already covered. RadioStationToAdd's 8 uncovered lines are its toString, which nothing in the
   repository calls.
2. :common seam - give RadioStationManagerLayerImpl an injectable work dispatcher and callback
   dispatcher, defaulting to Dispatchers.IO and Dispatchers.Main. Without it neither edit nor
   remove can be driven on the JVM at all: both answer through CoroutineScope(Dispatchers.Main),
   which has no main looper to dispatch on in a unit test. Callers keep the old four-argument
   constructor.
3. Delete the unused RadioStationToAdd.toString. It is unreachable from production and from
   tests, so the only way to cover it would be a test that pins a format no caller reads.
4. Rewrite RadioStationManagerLayerImplTest onto the existing preferencesContext() fake and add:
   an accepted candidate is stored with its home page and read back through a second storage; a
   rejected candidate is not stored; the favorites copy carries the home page only when asked;
   editing rewrites the stored home page; editing an unknown media id is reported as a failure;
   removing without a context or without a media id does nothing.
5. :app androidTest - new EditStationDialogTest: seed a local station carrying a home page, open
   EditStationDialog for its media id, and assert the dialog shows that home page (plus name and
   stream url, so a dialog that shows nothing cannot pass). Give the androidTest makeStation
   helper a homePage parameter.
6. Verify: ./gradlew test, verifyPureCoreCoverage, verifyPureCoreAttribution, and the new
   instrumentation class on the offline emulator after clearing app data.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Two decisions the task description did not anticipate.

The acceptance criteria say the per-class floor should clear as a side effect of replacing
ContextWrapper(null) with a preferences fake. It does not. Measured before any change,
RadioStationManagerLayerImpl sat at 10/18 lines with every uncovered line in editRadioStation
and removeRadioStation - the constructor and addRadioStation were already covered, so the
context was never what held that class down. Both of those methods answer through
CoroutineScope(Dispatchers.Main), which has no looper to dispatch on in a JVM test, so neither
could be driven at all. The class now takes a work dispatcher and a callback dispatcher with the
production values as defaults, and the tests hand it Dispatchers.Unconfined so both paths run to
completion on the calling thread. Production callers are untouched, and one test builds the
manager the four-argument way to keep that construction asserted.

RadioStationToAdd sat at 4/12, with all eight uncovered lines in a toString that nothing in the
repository calls - not production, not a test. It is deleted rather than covered: the only way to
clear the floor on it would have been a test pinning a rendering no caller reads.

One gap stays on purpose. removeRadioStation reaches context.contentResolver to delete the
artwork when the station is found, which no JVM context can answer for, so that path is covered
by DeviceLocalsStorageTest and the local-station journey. Its guards and its not-found answer are
covered here.

Both regression tests were mutation-checked. Deleting radioStation.homePage = rsToAdd.homePage
fails four JVM tests; deleting mHomePageEdit.setText(radioStation.homePage) fails
theDialogShowsTheStoredStation with "expected:<https://example.test/home> but was:<>".
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Both halves of the 13cc159 data loss now carry a regression test, and the two classes the
pure-core gate held below its floor are at 100% line coverage.

The add half is covered on the JVM: RadioStationManagerLayerImplTest moved off
ContextWrapper(null), whose getSharedPreferences answers null under returnDefaultValues, onto the
preferencesContext() fake, so its writes land and can be read back. It asserts the stored station
field by field, the lookup by media id the dialogs perform, that a rejected candidate never
reaches the store, the favorites copy with and without the checkbox, the edit rewriting the home
page, an edit of an unknown media id reported as a failure, and the three removal guards. Deleting
radioStation.homePage = rsToAdd.homePage fails four of them.

The edit half is covered by a new EditStationDialogTest, which shows the real dialog over a real
MainActivity with the bundle MediaPresenterImpl builds, against the real EditStationPresenterImpl
and DeviceLocalsStorage. Deleting mHomePageEdit.setText(radioStation.homePage) fails it with
expected:<https://example.test/home> but was:<>.

Two changes the criteria implied but the description did not. RadioStationManagerLayerImpl takes a
work dispatcher and a callback dispatcher, defaulting to Dispatchers.IO and Dispatchers.Main,
because editRadioStation and removeRadioStation answered through a main-looper scope and could not
be driven on the JVM at all - that, not the context, was the whole of its 10/18. And
RadioStationToAdd.toString is deleted: all eight of its uncovered lines were a renderer nothing in
the repository calls.

Verified with ./gradlew test (PASS), verifyPureCoreCoverage (RadioStationManagerLayerImpl 21/21,
RadioStationToAdd 4/4, aggregate 90.5% line and 81.0% branch; the only row still under the floor
is ASXPlaylistParser at 52.6%, which is TASK-066), the owner test re-measured alone (both classes
still 100%), and :app:connectedDebugAndroidTest for EditStationDialogTest, AddStationDialogTest
and LocalStationLifecycleJourneyTest - 8 tests, API 34 emulator, wifi and mobile data off.
<!-- SECTION:FINAL_SUMMARY:END -->
