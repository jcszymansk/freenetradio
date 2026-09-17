---
id: TASK-001
title: 'Phase 1: repair the test foundation'
status: Done
assignee: []
created_date: '2026-09-17 18:21'
updated_date: '2026-09-17 18:22'
labels: []
milestone: m-0
dependencies: []
type: chore
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The inherited suite contained assertion-free, misplaced and order-dependent tests, and had no coverage reporting. This phase made the existing suite honest, deterministic and measurable before new tests were written on top of it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Assertion-free storage merge test deleted or restored
- [x] #2 Pure tests moved from app/src/androidTest to common/src/test: MediaIDHelperTest, RadioStationJsonSerializerTest, EqualizerSerializationTest
- [x] #3 Legacy JUnit3 media-ID test converted to JUnit4
- [x] #4 Serializer tests strengthened into complete round trips
- [x] #5 Every test owns and clears its SharedPreferences, files and database state
- [x] #6 Shared station factories, provider fixtures and recording fakes introduced where duplication appeared
- [x] #7 Android Gradle Plugin JaCoCo support enabled without a second coverage framework
- [x] #8 Separate local and instrumented coverage reports produced
- [x] #9 Canonical commands established for JVM tests, instrumentation compilation, offline emulator tests and coverage
- [x] #10 Exit check: no assertion-free, disabled, order-dependent or externally networked test remains
<!-- AC:END -->
