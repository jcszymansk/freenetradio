---
id: TASK-002.01
title: Cover provider parsing
status: Done
assignee: []
created_date: '2026-09-17 18:22'
updated_date: '2026-09-17 18:23'
labels: []
milestone: m-0
dependencies: []
parent_task_id: TASK-002
type: chore
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ParserLayerRadioBrowserImpl and ParserLayerWebRadioImpl turn untrusted third-party JSON into RadioStation values, so malformed input must not reach the rest of the application.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Valid station mapping
- [x] #2 Missing station ID
- [x] #3 Missing stream URL
- [x] #4 Malformed and empty JSON
- [x] #5 Unknown fields
- [x] #6 Country-code mapping
- [x] #7 Category counts and title normalization
- [x] #8 Filtered stations
- [x] #9 WebRadioDB category, country and case-insensitive search filtering
<!-- AC:END -->
