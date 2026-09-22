---
id: TASK-061
title: Move the URL layer test to the JVM and cover country code encoding
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 17:46'
updated_date: '2026-09-22 07:13'
labels:
  - test
milestone: m-0
dependencies:
  - TASK-062
type: chore
ordinal: 76000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
UrlLayerTest lives in app/src/androidTest although UrlLayer*Impl is pure string work. It was written about a month before :android-jvm-stubs implemented Uri.parse and Uri.encode, and that is the only reason it ever needed a device. The consequence shows in the JVM coverage report, where UrlLayerRadioBrowserImpl and UrlLayerWebRadioImpl both read 0% line coverage, which is the measurement criterion 4 of TASK-007 depends on. The test also has a hole: 88d4955 added Uri.encode to getStationsByCountry, and the only fixture is "PL", which encodes to itself, so reverting that one line leaves the suite green. getConnectionUrl stays out of scope, since it is the one method that resolves DNS and no test may call it. Found while auditing criteria 4 and 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 UrlLayerTest runs under ./gradlew test with no device attached
- [x] #2 getStationsByCountry is covered with a country code that changes under encoding
- [x] #3 The JVM coverage report attributes both UrlLayer implementations to this test
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Move UrlLayerTest from app/src/androidTest to common/src/test/java/com/yuriy/openradio/shared/model/net/, dropping AndroidJUnit4 so it runs as a plain JVM test against the :android-jvm-stubs Uri.
2. Widen it to every Uri-building method of both implementations, so the two classes clear the 60% per-class line floor on this test alone and verifyPureCoreAttribution can name it as their owner.
3. Cover getStationsByCountry with a code that changes under Uri.encode, pinning WebRadioDB's encoded output (reverting 88d4955 must fail) and Radio Browser's raw output, which is the one method there that skips encodeValue.
4. Record the Radio Browser gap as a follow-up task rather than fixing it here.
5. Claim both UrlLayer rows in gradle/pure-core-coverage.tsv for the new owner test.
6. Verify: ./gradlew test, ./gradlew localCoverageReport, ./gradlew verifyPureCoreCoverage, ./gradlew verifyPureCoreAttribution, and ./gradlew :app:assembleDebugAndroidTest to prove the androidTest source set still compiles.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
UrlLayerTest now lives in common/src/test/java/com/yuriy/openradio/shared/model/net/ as a plain JUnit4 class. Nothing about it needed a device once TASK-062 took getConnectionUrl out of the interface: :android-jvm-stubs answers Uri.parse and Uri.encode, and DependencyRegistryCommon.PAGE_SIZE is a const val, so reading it inlines the literal and never touches the registry.

It grew from three assertions over three methods to ten tests over all fourteen, which is what lets verifyPureCoreAttribution name one owner for both classes.

The encoding fixture is "A&", two characters like every real code, with a second one that means something in an address. WebRadioDB carries the country code in a query parameter, so leaving the ampersand alone would end countryId early and filter on "A"; that is the assertion reverting 88d4955 has to fail. Radio Browser splices the same argument into the path unencoded, the one method in that class that skips encodeValue, so the test states that rather than hide it behind an ISO code that encodes to itself. Filed as TASK-074, referenced from the test, and left alone here.

Verified on the committed branch state: ./gradlew test PASS, ./gradlew :app:assembleDebugAndroidTest PASS (the androidTest source set still compiles after the deletion), ./gradlew localCoverageReport PASS.

verifyPureCoreAttribution reports [19/28] com.yuriy.openradio.shared.model.net.UrlLayerTest (:common) 2 owned class(es) ok. It still fails overall, on RadioStationManagerLayerImplTest and AutoDetectParserTest, both recorded in TASK-007 before this branch. verifyPureCoreCoverage fails the same way: aggregate line 85.8% (1255/1462, was 83.7%), branch 77.2%, and neither UrlLayer implementation appears in any failure list. Both are TASK-068's remaining work.

Ten mutations were put to the test one at a time, each run alone: dropping any of the five Uri.encode calls, flattening either paging stride to PAGE_SIZE, swapping the WebRadioDB countries dataset for the stations one, pointing lastchange at topclick, and adding encodeValue to the Radio Browser country path. All ten failed the suite. The last one matters most: it proves radioBrowserLeavesTheCountryCodeUnencoded pins current behaviour in both directions rather than passing by accident on an ISO code.

Round 1 of the review loop returned PASS with no findings.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
UrlLayerTest moved from app/src/androidTest to common/src/test and runs on the JVM. Nothing about it needed a device once :android-jvm-stubs implemented Uri.parse and Uri.encode and TASK-062 took getConnectionUrl out of the interface; PAGE_SIZE is a const val, so reading it inlines the literal. The suite grew from three assertions over three methods to ten tests over all fourteen, which is what lets verifyPureCoreAttribution name one owner for both implementations, and gradle/pure-core-coverage.tsv now records UrlLayerTest on both rows instead of NONE. The country code fixture is "A&", so WebRadioDB's Uri.encode is pinned (reverting 88d4955 fails) and Radio Browser's missing one is stated rather than hidden; that gap is TASK-074, which the test names. No production code changed. Verified with ./gradlew test, :app:assembleDebugAndroidTest, localCoverageReport, verifyPureCoreAttribution (2 owned classes ok for this owner) and verifyPureCoreCoverage (line 83.7% to 85.8%, no UrlLayer row left in any failure list), plus ten mutations that the suite killed.
<!-- SECTION:FINAL_SUMMARY:END -->
