---
id: TASK-063
title: Cover JsonUtils directly
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 19:10'
updated_date: '2026-09-22 12:52'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 78000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
JsonUtils is nine functions over JSONObject that every translator in the project reads untrusted JSON through, and nothing tests it. It sits at 81.6% line coverage, so no percentage flags it, but every one of those lines is executed by serializer tests that were testing something else: the pure-core gate records its owner as NONE because the single test that carries it is EqualizerSerializationTest, which is the shape criterion 5 of TASK-007 exists to reject. A defect in getListValue or an array accessor would surface as a corrupted station or a lost equalizer preset, far from the cause. One fix already went in blind this way: 0cb7b55 fixed getListValue returning a single empty string for an empty value, and the only test that would catch a regression asserts equalizer round trips. org.json behaves for real under the JVM unit test classpath, as the existing serializer tests show, so this needs no device.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Each accessor is covered for a present value, a missing key and a value of the wrong type
- [x] #2 getListValue is covered for an empty value, which previously yielded a list holding one empty string
- [x] #3 The short and int array accessors are covered for empty, single and multiple entries
- [x] #4 The default-value overloads are covered for both the present and the absent case
- [x] #5 JsonUtils has an owning test in gradle/pure-core-coverage.tsv that verifyPureCoreAttribution confirms
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Branch task-063-jsonutils-tests, task In Progress.
2. Write common/src/test/.../utils/JsonUtilsTest.kt covering all eight JsonUtils accessors: a present value, a missing key and a wrong-type value each; getListValue on an empty value; the short and int array accessors on empty, single and multiple entries; both default-value overloads present and absent.
3. Use a nested JSONArray as the wrong-type value everywhere, because that is the one shape the reference org.json on the JVM test classpath and Android's lenient org.json on a device both reject; a number would diverge between them and the test would pin the test classpath rather than the code.
4. Pin the single-entry array behaviour as it stands (one entry yields an empty array) and record the data loss it causes as a follow-up task rather than fixing it here.
5. Point the JsonUtils row of gradle/pure-core-coverage.tsv at the new test, re-run verifyPureCoreAttribution to confirm the owner, then verifyPureCoreCoverage to confirm no NONE rows remain.
6. Commit with the task Done, merge --no-ff.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified on 2026-09-22 against the branch.

JsonUtilsTest is 17 tests over all eight accessors. ./gradlew :common:testDebugUnitTest --tests com.yuriy.openradio.shared.utils.JsonUtilsTest: 17 tests, 0 failures. ./gradlew test: BUILD SUCCESSFUL.

Wrong-type values are always a nested JSONArray, never a number or a boolean. Two org.json implementations run this code and they disagree about coercion: the reference org.json:json:20230618 on the JVM test classpath refuses to hand a non-String to getString, Android's coerces a number or a boolean to its text. A structured value is the one shape both refuse, so the assertions pin JsonUtils rather than the classpath the test happens to run on. Probed rather than assumed: getStringValue on the integer 42 returns "42" on a device and throws JSONException here.

Isolated measurement, the way verifyPureCoreAttribution takes it: with common/build/outputs/unit_test_code_coverage, common/build/test-results and the debug coverage report deleted first, ./gradlew :common:testDebugUnitTest --tests com.yuriy.openradio.shared.utils.JsonUtilsTest :common:createDebugUnitTestCoverageReport left one test-results file and one <sessioninfo>, and JsonUtils at LINE 49/49, BRANCH 28/28, METHOD 8/8. It had been 40 of 49 lines, all of it incidental.

./gradlew verifyPureCoreAttribution printed '[28/31] com.yuriy.openradio.shared.utils.JsonUtilsTest (:common) 1 owned class(es) ok'. The task itself still fails on two owners this row does not touch, RadioStationManagerLayerImplTest and AutoDetectParserTest, which TASK-059 and TASK-066 carry; owners are evaluated independently.

./gradlew verifyPureCoreCoverage then re-ran the whole suite: line 89.4% (1307/1462), branch 79.9% (509/637), up from 88.8% and 79.7%, which is the 9 JsonUtils lines nothing reached before. The unowned section is gone entirely, no NONE row is left in the table. The task still fails on the same three below-floor classes, which TASK-059 and TASK-066 carry.

Discovered while covering criterion 3 and not fixed here: getShortArray and getIntArray return an empty array for a single entry, because the guard that discards the one element an empty value splits into cannot tell it from a real lone entry. A one-band equalizer loses its band levels and centre frequencies on every reload. The current behaviour is pinned in the two tests named for it and TASK-077 carries the fix.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
JsonUtilsTest covers all eight JsonUtils accessors directly: a present value, a missing key and a wrong-type value each, getListValue on an empty value, the short and int array accessors on empty, single and multiple entries, and both default-value overloads present and absent. Wrong-type values are a nested JSONArray throughout, the one shape the reference org.json on the JVM test classpath and Android's lenient org.json both refuse, so the assertions pin the code and not the classpath. Run alone the test carries JsonUtils 49/49 lines, 28/28 branches and 8/8 methods, up from 40 incidental lines, and verifyPureCoreAttribution confirms it as the owner of the row that read NONE. No NONE row is left in gradle/pure-core-coverage.tsv. The single-entry array loss the coverage exposed is pinned as it stands and tracked as TASK-077.
<!-- SECTION:FINAL_SUMMARY:END -->
