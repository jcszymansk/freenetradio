---
id: TASK-076
title: Record SortUtilsTest as the owner of SortUtils in the pure-core set
status: To Do
assignee: []
created_date: '2026-09-22 09:36'
labels:
  - test
milestone: m-0
dependencies:
  - TASK-064
type: chore
ordinal: 90000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-064 added SortUtilsTest and took SortUtils from nothing to 19 of 19 lines and 10 of 10 branches, but never claimed its row in gradle/pure-core-coverage.tsv, which still reads NONE. The two tasks either side of it did claim theirs in the same commit as the test, 2358abb for the URL layer and fd40cdb for the storage manager; TASK-064 closed without it and cannot be reopened.

The row matters on its own. verifyPureCoreCoverage fails on a NONE row whatever the measured coverage says, because an unowned class is a recorded gap in the TASK-007 gate rather than a formality, so the gate still counts SortUtils as untested and TASK-068 cannot pass while it does. With this row claimed, JsonUtils is the only NONE left and TASK-063 carries it.

The owner has already been measured the way verifyPureCoreAttribution measures it: :common:testDebugUnitTest --tests com.yuriy.openradio.shared.utils.SortUtilsTest with the coverage exec data cleared first, nothing else in the results directory, puts SortUtils at 19 of 19 lines, far above the 60% per class floor. That measurement was taken on 2026-09-22, right after the TASK-064 merge. The file forbids recording an owner that verifyPureCoreAttribution has not confirmed, so running the confirmation is part of the work and not a formality, and it has to be re-run rather than taken from this description.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 gradle/pure-core-coverage.tsv names com.yuriy.openradio.shared.utils.SortUtilsTest in :common as the owner of com/yuriy/openradio/shared/utils/SortUtils
- [ ] #2 verifyPureCoreAttribution runs SortUtilsTest alone and confirms it carries SortUtils above the per-class line floor
- [ ] #3 verifyPureCoreCoverage no longer lists SortUtils among the classes with no owning test recorded
- [ ] #4 No other row of the table changes
<!-- AC:END -->
