---
id: TASK-078
title: Collapse the duplicated end of list guard in the children loaded path
status: To Do
assignee: []
created_date: '2026-09-22 15:13'
updated_date: '2026-10-05 05:39'
labels:
  - browse
milestone: m-0
dependencies: []
references:
  - >-
    common-ui/src/main/java/com/yuriy/openradio/shared/presenter/MediaPresenterImpl.kt
  - app/src/main/java/com/yuriy/openradio/mobile/view/activity/MainActivity.kt
  - >-
    common-ui/src/test/java/com/yuriy/openradio/shared/presenter/MediaPresenterChildrenLoadedTest.kt
type: bug
ordinal: 92000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PlayerUtils.isEndOfList(children) is checked twice on the way from a loaded page to the list: once in MainActivity's MediaBrowserSubscriptionCallback, immediately before the only call to handleChildrenLoaded, and once inside MediaPresenterImpl.handleChildrenLoaded. Both copies carry the same comment and both date to dc16f01, so neither is a recent mistake. The presenter's copy is dead in production: nothing else calls handleChildrenLoaded.

Deleting either one is not a cleanup, because they sit on opposite sides of 'mCurrentParentId = parentId'. With MainActivity's guard gone, an ended first page for node B advances mCurrentParentId to B while leaving node A's rows and adapter.parentId = A in place. The next scroll-triggered page for B then arrives with replace false, AppUtils.isSameCatalogue(B, B) is true, and the rows of A are appended to rather than replaced. The user is looking at a list labelled B holding stations from A, and only a navigation away and back clears it.

Which of the two guards survives is therefore a decision about where mCurrentParentId should be assigned, not a question of which copy to delete. The safe shape is one guard, in the presenter, with mCurrentParentId assigned below it, so that a page carrying nothing but the end marker changes no state at all. Confirm that against the paginated nodes before committing to it: Radio Browser pages country stations and search results server-side, so those are the nodes that can produce an ended page for a node the list is not showing yet.

Found while reviewing task-058, which covered the surviving guard but deliberately asserted nothing about mCurrentParentId so as not to lock in the current behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Only one isEndOfList guard remains on the path from MediaBrowserSubscriptionCallback to the adapter
- [ ] #2 A page carrying only the end of list marker leaves mCurrentParentId, adapter.parentId and the loaded rows all unchanged
- [ ] #3 A JVM test in common-ui drives handleChildrenLoaded with an ended page for a node other than the one already loaded, then a normal page for that same node, and asserts the second page replaces rather than appends
- [ ] #4 The test fails if mCurrentParentId is assigned above the surviving guard
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Found on TASK-068 while writing PlayerUtilsTest: isEndOfList recognises the end marker by reference, mediaId === the marker's id, so a marker whose id is equal but a different String instance, which is what arrives after a Bundle crosses a Binder, reads as an ordinary page. It is latent only because no production code builds the marker; the live end-of-list cases are a null list and a list holding one null. isEndOfList(emptyList()) is false while null and [null] are true. PlayerUtilsTest pins all three as current behaviour, so whoever settles the guard here should decide them and update the test.
<!-- SECTION:NOTES:END -->
