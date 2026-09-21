---
id: TASK-061
title: Move the URL layer test to the JVM and cover country code encoding
status: To Do
assignee: []
created_date: '2026-09-21 17:46'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 76000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
UrlLayerTest lives in app/src/androidTest although UrlLayer*Impl is pure string work. It was written about a month before :android-jvm-stubs implemented Uri.parse and Uri.encode, and that is the only reason it ever needed a device. The consequence shows in the JVM coverage report, where UrlLayerRadioBrowserImpl and UrlLayerWebRadioImpl both read 0% line coverage, which is the measurement criterion 4 of TASK-007 depends on. The test also has a hole: 88d4955 added Uri.encode to getStationsByCountry, and the only fixture is "PL", which encodes to itself, so reverting that one line leaves the suite green. getConnectionUrl stays out of scope, since it is the one method that resolves DNS and no test may call it. Found while auditing criteria 4 and 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 UrlLayerTest runs under ./gradlew test with no device attached
- [ ] #2 getStationsByCountry is covered with a country code that changes under encoding
- [ ] #3 The JVM coverage report attributes both UrlLayer implementations to this test
<!-- AC:END -->
