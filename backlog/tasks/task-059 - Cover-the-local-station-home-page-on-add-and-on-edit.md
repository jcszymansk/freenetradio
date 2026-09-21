---
id: TASK-059
title: Cover the local station home page on add and on edit
status: To Do
assignee: []
created_date: '2026-09-21 17:46'
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
<!-- AC:END -->
