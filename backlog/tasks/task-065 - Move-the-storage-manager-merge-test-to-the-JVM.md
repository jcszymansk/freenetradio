---
id: TASK-065
title: Move the storage manager merge test to the JVM
status: To Do
assignee: []
created_date: '2026-09-21 19:11'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 80000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
StorageManagerLayerImpl imports nothing from Android. It takes two storages and its own work is merge: concatenate two sets, sort by sort id, renumber from zero, and hand the result back. That is the logic behind importing a data file over an existing collection, and getting it wrong silently duplicates or reorders the user favorites.

Its only tests are the twelve in the instrumented StorageManagerLayerTest, which build FavoritesStorage and DeviceLocalsStorage from the instrumentation target context. So the class reads 0% line coverage in the JVM report, which is the measurement criterion 4 of TASK-007 depends on, and the pure-core gate records its owner as NONE. This is the same finding as TASK-061 one module over: the test is at the layer the storages need, not the layer the class needs.

The storages genuinely are instrumented subjects and the coverage they have should not be lost. What belongs on the JVM is the merge rule itself, driven through the preferences fake in common/src/test rather than through a device.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The merge rule is covered on the JVM: overlap between the two sets, disjoint sets, an empty incoming set and an empty existing set
- [ ] #2 Sort ids come out contiguous from zero and in the order the sort ids implied
- [ ] #3 StorageManagerLayerImpl clears the per-class line floor in the JVM coverage report
- [ ] #4 StorageManagerLayerImpl has an owning test in gradle/pure-core-coverage.tsv that verifyPureCoreAttribution confirms
- [ ] #5 The storage-level behaviour the instrumented test covers is still covered somewhere
<!-- AC:END -->
