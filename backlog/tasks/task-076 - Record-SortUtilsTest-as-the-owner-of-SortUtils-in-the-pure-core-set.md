---
id: TASK-076
title: Record SortUtilsTest as the owner of SortUtils in the pure-core set
status: Done
assignee:
  - '@claude'
created_date: '2026-09-22 09:36'
updated_date: '2026-09-22 09:52'
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
- [x] #1 gradle/pure-core-coverage.tsv names com.yuriy.openradio.shared.utils.SortUtilsTest in :common as the owner of com/yuriy/openradio/shared/utils/SortUtils
- [x] #2 verifyPureCoreAttribution runs SortUtilsTest alone and confirms it carries SortUtils above the per-class line floor
- [x] #3 verifyPureCoreCoverage no longer lists SortUtils among the classes with no owning test recorded
- [x] #4 No other row of the table changes
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Branch task-076-sortutils-owner-row, set the task In Progress.
2. Point the SortUtils row of gradle/pure-core-coverage.tsv at com.yuriy.openradio.shared.utils.SortUtilsTest in :common, touching no other row.
3. Re-run verifyPureCoreAttribution so the new owner is confirmed the way the file demands, not taken from the description.
4. Re-run verifyPureCoreCoverage last, since attribution leaves the reports holding one isolated run, and confirm SortUtils is gone from the unowned list.
5. Commit the row with the task marked Done, merge --no-ff.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified on 2026-09-22 against the branch, not taken from the description.

Isolated measurement, the way verifyPureCoreAttribution takes it: with common/build/outputs/unit_test_code_coverage, common/build/test-results and the debug coverage report deleted first, ./gradlew :common:testDebugUnitTest --tests com.yuriy.openradio.shared.utils.SortUtilsTest :common:createDebugUnitTestCoverageReport left one test-results file and one <sessioninfo> in report.xml, and SortUtils at LINE 19/19 (100.0%, floor 60.0%) and BRANCH 10/10.

./gradlew verifyPureCoreAttribution printed '[28/30] com.yuriy.openradio.shared.utils.SortUtilsTest (:common) 1 owned class(es) ok (3.2s)'. The task itself failed in 2m 1s, on two owners this row does not touch: RadioStationManagerLayerImplTest (RadioStationToAdd 4/12, RadioStationManagerLayerImpl 10/18) and AutoDetectParserTest (ASXPlaylistParser 51/97). Owners are evaluated independently, so neither can follow from this change.

./gradlew verifyPureCoreCoverage then re-ran the whole :common suite: line 88.8% (1298/1462), branch 79.7% (508/637), and the unowned list is down to one row, com/yuriy/openradio/shared/utils/JsonUtils. SortUtils appears in neither the unowned list nor the below-floor list. The task still fails on JsonUtils (TASK-063) and on the same three below-floor classes, which TASK-059 and TASK-066 carry.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
The SortUtils row of gradle/pure-core-coverage.tsv now names com.yuriy.openradio.shared.utils.SortUtilsTest in :common instead of NONE. One row changed, nothing else. Run alone, SortUtilsTest covers SortUtils 19/19 lines and 10/10 branches, verifyPureCoreAttribution reports that owner ok, and verifyPureCoreCoverage no longer counts SortUtils as unowned; JsonUtils is the last NONE row left.
<!-- SECTION:FINAL_SUMMARY:END -->
