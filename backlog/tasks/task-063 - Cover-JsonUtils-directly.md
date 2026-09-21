---
id: TASK-063
title: Cover JsonUtils directly
status: To Do
assignee: []
created_date: '2026-09-21 19:10'
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
- [ ] #1 Each accessor is covered for a present value, a missing key and a value of the wrong type
- [ ] #2 getListValue is covered for an empty value, which previously yielded a list holding one empty string
- [ ] #3 The short and int array accessors are covered for empty, single and multiple entries
- [ ] #4 The default-value overloads are covered for both the present and the absent case
- [ ] #5 JsonUtils has an owning test in gradle/pure-core-coverage.tsv that verifyPureCoreAttribution confirms
<!-- AC:END -->
