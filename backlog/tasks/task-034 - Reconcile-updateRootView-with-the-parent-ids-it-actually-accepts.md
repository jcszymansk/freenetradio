---
id: TASK-034
title: Reconcile updateRootView with the parent ids it actually accepts
status: To Do
assignee: []
created_date: '2026-09-18 09:44'
labels: []
dependencies: []
type: chore
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaPresenterImpl.updateRootView carries a KDoc saying it should be called when the current media id is MEDIA_ID_ROOT or MEDIA_ID_BROWSE_CAR if the application runs on car. The guard below it accepts only MEDIA_ID_ROOT and logs a warning and returns for everything else, so the car half of that sentence has never been true.

The single caller is onLocationChanged, reached from the country picker in GeneralSettingsDialog and from the automatic location path in MediaPresenterImpl. The root composition depends on the country, because MediaItemRoot adds the country stations entry when a non-default country is known, so a country change is exactly when the root needs rebuilding.

That leaves a second question the comment hides: what happens when the country changes while the user is browsing any node other than the root. The refresh is skipped with a warning, and whether the root is rebuilt on the way back, or is served stale from BrowseTree, has not been established. Settle it rather than only deleting the comment.

TASK-033 decides the car half. If the browse root is selected per connecting client, a phone client never sits on MEDIA_ID_BROWSE_CAR and the clause is simply obsolete. Found while auditing browse coverage for TASK-004.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The KDoc states the parent ids the guard actually accepts, with no claim about car mode that the code does not implement
- [ ] #2 The behavior of a country change made while browsing a node other than the root is established: either the root is correctly rebuilt when the user returns to it, or the stale case is fixed
- [ ] #3 Whichever of those two holds is covered by a test rather than left to the warning in the log
<!-- AC:END -->
