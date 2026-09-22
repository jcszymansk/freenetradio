---
id: TASK-075
title: Reserve the dropped station's slot when a station is dragged down
status: To Do
assignee: []
created_date: '2026-09-22 08:35'
labels:
  - storage
milestone: m-0
dependencies:
  - TASK-064
type: bug
ordinal: 89000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SortUtils.resortIds renumbers a category after the user drags a row, and it reserves the requested slot for the dragged station by skipping one number when it meets the station that currently holds that sort id. That station is met after the running counter has already passed the reserved value whenever the drag goes towards the bottom of the list, so the gap is opened in the wrong place and two stations come out of the renumbering sharing one sort id. Dropping a row back where it was picked up is the same defect from its other side: the station holding the requested sort id is the dragged one, nothing triggers the skip, and the station below it collides instead.

A collision is not visible at once, because AbstractRadioStationsStorage.getAll renumbers the collection from zero on the next read. What the user sees is that the row landed one place away from where it was dropped, and which of the two colliding stations ends up first is settled by RadioStation.compareTo falling through to the station name.

The rule's intent is to skip the number the dragged station was given, so the condition belongs on the counter rather than on the station being looked at. SortUtilsTest pins the current behaviour, including both collisions, so the fix has to rewrite those expectations; running the suite with 'counter == sortId' in place of 'item.sortId == sortId' fails exactly the two collision tests and produces the contiguous order in both.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A station dragged towards the bottom of a category ends up at the requested sort id and no other station shares it
- [ ] #2 Dropping a station back on the position it was picked up from leaves the category's sort ids unchanged
- [ ] #3 Dragging towards the top of a category keeps working as it does now
- [ ] #4 SortUtilsTest states the corrected order for favorites and for stations added on the device, and no test still expects two stations to share a sort id
<!-- AC:END -->
