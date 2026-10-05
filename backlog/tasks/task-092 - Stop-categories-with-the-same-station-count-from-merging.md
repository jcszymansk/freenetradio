---
id: TASK-092
title: Stop categories with the same station count from merging
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-05 05:22'
updated_date: '2026-10-05 15:01'
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
- [ ] #1 Two categories with the same station count and different ids both reach the browse list, from either provider, pinned by a JVM test
- [ ] #2 The order the list is shown in is a deliberate, tested choice, with ties broken deterministically
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Replace Category's count-only natural ordering with an explicit BROWSE_ORDER comparator: station count descending, then title case-insensitively, then id, so only categories with the same id are treated as one.
2. Build both parsers' sets with that comparator; log a duplicate category id from Radio Browser instead of dropping it silently.
3. JVM tests: CategoryTest for the order, ParserLayerMappingTest for equal counts from both providers.
4. Category gains logic, so move it from EXCLUDED to an owner test in gradle/pure-core-coverage.tsv.
<!-- SECTION:PLAN:END -->
