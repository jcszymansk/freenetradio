---
id: TASK-059
title: Cover the local station home page on add and on edit
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 17:46'
updated_date: '2026-09-22 13:19'
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
- [ ] #1 Adding a station with a home page stores that home page and a test reads it back
- [ ] #2 Opening the edit dialog for a stored station shows its home page
- [ ] #3 The add-side test runs on the JVM against a preferences fake rather than on a device
- [ ] #4 RadioStationManagerLayerImpl and RadioStationToAdd clear the per-class line floor in the JVM coverage report, which replacing ContextWrapper(null) with a preferences fake should achieve as a side effect
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
