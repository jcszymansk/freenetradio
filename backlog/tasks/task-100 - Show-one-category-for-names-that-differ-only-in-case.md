---
id: TASK-100
title: Show one category for names that differ only in case
status: To Do
assignee: []
created_date: '2026-10-05 17:01'
labels: []
milestone: m-1
dependencies: []
priority: low
type: bug
ordinal: 114000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
To a listener, rock and Rock are the same genre, but the category list shows both as separate rows with the same title. Radio Browser reports tags case-sensitively, so its tag list can hold rock and Rock with separate station counts; WebRadioDB genres are free text and can do the same. Since TASK-092 the list keeps every distinct id, so both rows appear side by side, ordered by id.

Merging the rows is not enough on its own, because opening a category asks the provider for its stations by the id. Radio Browser's stations/bytag/ is the non-exact search; whether it already returns every case variant must be checked against the API documentation, not assumed. WebRadioDB's getRadioStationByGenre compares the genre with the id exactly (ParserLayerWebRadioImpl), so a merged row would lose the stations filed under the other spelling. The merged row's station count also has to be decided: a sum can count a station tagged with both spellings twice.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A category name that differs only in case appears once in the list, from either provider
- [ ] #2 Opening the merged category lists the stations of every spelling it merged, and none twice
- [ ] #3 The merged row's title and station count follow a stated rule, pinned by JVM tests in ParserLayerMappingTest
<!-- AC:END -->
