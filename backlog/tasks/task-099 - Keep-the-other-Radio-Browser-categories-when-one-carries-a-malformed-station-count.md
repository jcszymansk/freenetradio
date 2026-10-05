---
id: TASK-099
title: >-
  Keep the other Radio Browser categories when one carries a malformed station
  count
status: To Do
assignee: []
created_date: '2026-10-05 15:09'
labels: []
milestone: m-1
dependencies: []
priority: low
type: bug
ordinal: 113000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ParserLayerRadioBrowserImpl.getAllCategories reads stationcount with JSONObject.getInt outside any try, so a single tag whose count is not a number throws out of the whole parse, and the categories node loses every entry rather than the one bad row. The per-element catch in the same loop shows the intent was to skip what cannot be read. Found while reviewing TASK-092.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A tag with a non-numeric station count is skipped or read as zero, by a stated choice, and the rest of the list is returned
- [ ] #2 A JVM test in ParserLayerMappingTest pins that choice
<!-- AC:END -->
