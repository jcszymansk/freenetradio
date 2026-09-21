---
id: TASK-064
title: Cover station reordering in SortUtils
status: To Do
assignee: []
created_date: '2026-09-21 19:11'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 79000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SortUtils has no test anywhere: 19 lines and 10 branches at 0% coverage, reached from one production caller, OpenRadioServicePresenterImpl.updateSortIds. It is what renumbers favorites and local stations when the user drags a row, so a defect reorders or collides the list the user arranged by hand, and the only thing that would notice is the user.

The renumbering rule in the private resortIds is not obvious and should be established rather than assumed. Walking the set, the dragged station takes the requested sortId, and every other station takes a running counter that is incremented twice when it already holds the requested sortId and once otherwise. Whether the double increment is deliberate room-making or an off-by-one is exactly what a test has to settle; if it turns out to be wrong, the fix belongs in its own task with this one pinning the behaviour first.

It takes the concrete FavoritesStorage and DeviceLocalsStorage, so a JVM test needs the preferences fake that common/src/test already has in InMemoryPreferences, as OpenRadioServicePresenterImplTest.preferencesContext does.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Reordering within favorites renumbers only favorites, and within locals only locals
- [ ] #2 A category media id that is neither favorites nor locals changes nothing
- [ ] #3 The dragged station ends up with the requested sort id
- [ ] #4 The renumbering rule for the remaining stations is pinned, including the case where one already holds the requested sort id
- [ ] #5 An empty category and a media id that matches no station are covered
- [ ] #6 The tests run on the JVM against a preferences fake and reach no device
<!-- AC:END -->
