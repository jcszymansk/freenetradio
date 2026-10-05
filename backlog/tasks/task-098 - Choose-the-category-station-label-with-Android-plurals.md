---
id: TASK-098
title: Choose the category station label with Android plurals
status: To Do
assignee: []
created_date: '2026-10-05 15:09'
labels: []
milestone: m-1
dependencies: []
priority: low
type: bug
ordinal: 112000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemBuilder.buildChildCategory labels a category's station count with one of two strings, radio_station for exactly one and radio_stations otherwise. That is English grammar: Polish, among others, needs a third form for counts ending in 2 to 4 (2 stacje, 5 stacji), so translations can only pick the wrong word for some counts. Android resolves this with a <plurals> resource and getQuantityString, which picks the form by the device locale's plural rules. The test R class numbers every resource zero, so a JVM test cannot tell the labels apart; MediaItemBuilder is excluded from the pure-core set under rule 3 for that reason. Found while reviewing TASK-092.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The category subtitle comes from a plurals resource chosen by the station count
- [ ] #2 Every locale the app ships strings for has the plural forms its grammar needs
- [ ] #3 An instrumented test reading real resources pins the label for counts 1, 2 and 5
<!-- AC:END -->
