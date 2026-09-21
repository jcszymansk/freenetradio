---
id: TASK-070
title: Pin the entries the phone root test walks
status: To Do
assignee: []
created_date: '2026-09-21 20:32'
labels:
  - test
milestone: m-0
dependencies: []
ordinal: 84000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemRootTest.everyRootEntryIsBrowsableAndCarriesAnIcon is the shape TASK-054 fixed in MediaItemCarRootTest, in the same directory, and was not in that task's list. listener.awaitResult() pins nothing, and two loops over listener.items carry every assertion about browsability, media type, icon and title. A regression that dropped entries from the phone root leaves both loops satisfied by iterating over what is left: the second loop filters out the country entry, so it can run zero times and still pass. The single { } for the country entry is the only thing keeping a wholly empty result from passing, which makes the test fail for the wrong reason rather than assert the root it is named for.

The phone root is built by MediaItemRoot and its expected entries are already spelled out by the other cases in the file. Found while reviewing TASK-054.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 everyRootEntryIsBrowsableAndCarriesAnIcon asserts which entries it walked before walking them
- [ ] #2 The test fails when the root comes back short, not only when it comes back empty
<!-- AC:END -->
