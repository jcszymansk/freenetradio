---
id: TASK-092
title: Stop categories with the same station count from merging
status: Done
assignee:
  - '@claude'
created_date: '2026-10-05 05:22'
updated_date: '2026-10-05 17:07'
labels: []
milestone: m-1
dependencies: []
priority: medium
type: bug
ordinal: 106000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Both parsers collect categories into a TreeSet<Category> (ParserLayerRadioBrowserImpl and ParserLayerWebRadioImpl, getAllCategories), and Category.compareTo compares the station count and nothing else. A TreeSet treats compareTo returning 0 as the same element, so of two genres that happen to carry the same number of stations only the first survives, and the category list the user browses silently loses entries. Real provider data has many genres with equal counts, small ones especially. ParserLayerMappingTest.categoriesIncludeCountsAndNormalizedTitles uses distinct counts, which is why no test sees it. Found while clearing the TASK-068 gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Two categories with the same station count and different ids both reach the browse list, from either provider, pinned by a JVM test
- [x] #2 The order the list is shown in is a deliberate, tested choice, with ties broken deterministically
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace Category's count-only natural ordering with an explicit BROWSE_ORDER comparator: station count descending, then title case-insensitively by character (not locale collation), then id, so a sorted set never merges categories with different ids.
2. Build both parsers' sets with that comparator. Radio Browser keeps the first entry of a repeated tag name, since the id becomes the child's media id; WebRadio's ids are map keys and cannot repeat.
3. Move the singular/plural label lookup from Category into MediaItemBuilder.buildChildCategory, its only caller, so Category holds no resource lookup and can join the pure-core set (rule 3) with CategoryTest as owner.
4. JVM tests: CategoryTest for the order and its precedence, ParserLayerMappingTest for equal counts and repeated ids from both providers, MediaItemAllCategoriesTest for equal counts reaching the browse children.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decided: Radio Browser tags that differ only in case (rock, Rock) stay two entries with the same title, ordered by id. The ids differ, which is what AC1 protects, and the old code already listed both whenever their counts differed. Whether they should be merged is a product question left open. Plurals for the station label and a malformed stationcount aborting the whole parse are pre-existing and tracked as TASK-098 and TASK-099.

Follow-up: merging categories that differ only in case is tracked as TASK-100.

Validation: ./gradlew test verifyPureCoreCoverage :app:assembleDebugAndroidTest passed; verifyPureCoreAttribution passed with CategoryTest owning Category. Codex review: PASS.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Category sorted on station count alone, so the TreeSet both parsers collect into merged genres with equal counts. Category now has an explicit BROWSE_ORDER (count descending, then title ignoring case by character, then id), used by both parsers; Radio Browser keeps the first entry of a repeated tag name so no two browse children share a media id. The station label moved from Category to MediaItemBuilder so Category holds no resource lookup and joins the pure-core set, owned by CategoryTest. Pinned by CategoryTest, ParserLayerMappingTest (equal counts from both providers, repeated ids) and MediaItemAllCategoriesTest (equal counts reach the browse children). Follow-ups: TASK-098, TASK-099, TASK-100.
<!-- SECTION:FINAL_SUMMARY:END -->
