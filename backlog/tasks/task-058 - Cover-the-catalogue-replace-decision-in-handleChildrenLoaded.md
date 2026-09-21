---
id: TASK-058
title: Cover the catalogue replace decision in handleChildrenLoaded
status: To Do
assignee: []
created_date: '2026-09-21 17:46'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 73000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
bcea65a added a replace flag to the children-loaded path so a refreshed node clears its rows instead of appending to them. MediaPresenterImpl.handleChildrenLoaded carries that decision, and nothing asserts it. MediaItemsAdapterTest looks like the regression test and is not: MediaItemsAdapter.updateData(value, replace) did not exist when the fix landed, it was extracted later in 8210ea5 to make the adapter unit-testable, and it covers a four-line helper, so reverting the presenter clause leaves it green. AppUtils.isSameCatalogue returns false for root, favorites and locals, which are the only nodes the offline journeys ever render, so the journey suite cannot see it either. This was the whole point of bcea65a on the UI side and it is the most serious of the gaps found while auditing criterion 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A JVM test drives handleChildrenLoaded twice for the same non-root node and asserts that replace true clears while replace false appends
- [ ] #2 The test fails if the replace clause is removed from MediaPresenterImpl
- [ ] #3 The test drives a recording adapter and needs no device
<!-- AC:END -->
