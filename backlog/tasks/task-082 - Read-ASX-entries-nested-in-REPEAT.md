---
id: TASK-082
title: Read ASX entries nested in REPEAT
status: To Do
assignee: []
created_date: '2026-09-22 16:32'
labels: []
milestone: m-0
dependencies: []
references:
  - >-
    common/src/main/java/wseemann/media/jplaylistparser/parser/asx/ASXPlaylistParser.kt
priority: low
type: bug
ordinal: 96000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ASXPlaylistParser reads only ENTRY and ENTRYREF elements that are direct children of the root. The ASX format also allows them inside a REPEAT element, and those are dropped without a trace. Found while covering the parser in TASK-066.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ENTRY and ENTRYREF inside REPEAT are read in document order, once each
- [ ] #2 A test covers a REPEAT between two top level entries
<!-- AC:END -->
