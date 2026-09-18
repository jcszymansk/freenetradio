---
id: TASK-004.01
title: Cover presenter and media item commands with fakes
status: Done
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-17 21:11'
labels: []
milestone: m-0
dependencies:
  - TASK-003
parent_task_id: TASK-004
type: chore
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioServicePresenterImpl and the MediaItemCommand implementations compose every browse node. Testing them with fake collaborators is far cheaper than driving the same cases through a real service.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Root composition
- [x] #2 Favorites and Locals appear only when populated
- [x] #3 Country entry rules
- [x] #4 Phone versus car root command registration
- [x] #5 Categories, countries, popular, new and search nodes
- [x] #6 Playable and browsable metadata
- [x] #7 Empty-state behavior
- [x] #8 Pagination and refresh
- [x] #9 Invalid stations are omitted
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add an android-jvm-stubs module wired as testRuntimeOnly so JVM tests compile against the platform API but run against working Uri, Bundle and MimeTypeMap implementations; retire TestUri.
2. Add shared command test support: a Context double that answers resource lookups, a recording OpenRadioServicePresenter, a recording ResultListener and playback state listener that can be awaited, and station, category and country builders.
3. Cover root composition for phone, car root and car browse, including favorites and locals gating, source gating and country entry rules.
4. Cover favorites and locals lists: invalid stations omitted, favorite and local flags, empty results.
5. Cover categories, child categories, countries, country stations, popular, new and both search nodes: delegation, pagination and refresh, empty-state behavior, saved-instance behavior.
6. Cover OpenRadioServicePresenterImpl: phone versus car command registration, url and model layer delegation, country cache memoization, favorite toggling, clear and close.
7. Run the JVM suite and the local coverage report; record the testing convention for the stub module in AGENTS.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
80 JVM tests added across 9 new test classes plus the stub module's own 16.

Evidence per criterion: root composition and the favorites, locals, source and country gating in MediaItemRootTest and MediaItemCarRootTest; country entry rules also in MediaItemCountriesListTest (unknown country codes skipped); phone versus car registration in OpenRadioServicePresenterImplTest; the catalogue, chart and search nodes in MediaItemAllCategoriesTest, MediaItemCountriesListTest, MediaItemChildCategoriesTest, MediaItemCountryStationsTest, MediaItemChartsTest and MediaItemSearchTest; playable and browsable metadata in MediaItemFavoritesListTest and the metadata tests of the root classes; empty-state behavior in the empty-catalogue tests of each node; pagination and refresh in MediaItemChildCategoriesTest and MediaItemCountryStationsTest; invalid stations in MediaItemFavoritesListTest and MediaItemLocalsListTest.

Two things had to change before any of this could run off a device.

1. :android-jvm-stubs, a new test-runtime-only module with working implementations of android.net.Uri, android.os.Bundle, android.webkit.MimeTypeMap and the resource lookups of android.content.Context. Without them MediaItemBuilder cannot build a playable item at all (the artwork Uri builder returns null) and every extras flag reads back as a default. The module is testRuntimeOnly on purpose: the same classes in a test source set make the Kotlin compiler treat the type as duplicated and reject every use of it, including uses inside production signatures. TestUri is gone; Uri.parse now parses.

2. The use-location rule was written out three times, and the two copies in MediaItemRoot and MediaItemBrowseCar bound the string resource to a non-null local, which crashes when a resource resolves to null. Both now call LocationService.isDefaultLocationEnabled, which was already the private third copy.

Known gap left on purpose: the withTimeoutOrNull fallback in each asynchronous command is not exercised, because reaching it means blocking a test for the full five second command timeout. Timeout and recovery behavior belongs to Phase 5 (TASK-005).

TASK-028 records a defect found while writing MediaItemLocalsListTest: the isLocal flag written into every playable item's extras has no reader anywhere. TASK-027 gained a second pin in OpenRadioServicePresenterImplTest.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Covered OpenRadioServicePresenterImpl and all thirteen MediaItemCommand implementations with hand-written fakes: 80 new JVM tests over the browse tree the phone UI and Android Auto share. Two prerequisites: a test-runtime-only :android-jvm-stubs module that gives JVM tests working Uri, Bundle, MimeTypeMap and Context resource lookups, and folding the three copies of the use-location country rule into LocationService, whose duplicates crashed off a device. Verified with a clean ./gradlew test (153 JVM tests, green), assembleDebug and :app:assembleDebugAndroidTest; ./gradlew localCoverageReport puts model/media/item at 89% line and 79% branch coverage and OpenRadioServicePresenterImpl at 94%.
<!-- SECTION:FINAL_SUMMARY:END -->
