---
id: TASK-084
title: Clear the executable bit from files that are not programs
status: To Do
assignee: []
created_date: '2026-09-22 18:49'
labels: []
milestone: m-0
dependencies: []
references:
  - gradlew
  - doc/source-provenance.md
priority: low
type: chore
ordinal: 98000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
563 of the 833 tracked files are mode 100755, including 327 XML resources, 182 Kotlin sources, 29 PNGs, three prebuilt APKs, LICENSE and NOTICE. Only gradlew is a program. The bits came in with the recovered OpenRadio baseline (see doc/source-provenance.md); nothing in the build reads them.

They are not harmless. core.fileMode is true here, so the mode is part of every blob: a file rewritten by a tool that creates it fresh silently flips to 100644 and the change rides along in an unrelated commit. That is how this surfaced, in the TASK-066 merge, which carried a mode change on AbstractParser.kt and ASXPlaylistParser.kt that had nothing to do with the task.

The fix is one sweep, which will conflict with any branch open at the time, so it wants a quiet tree. There are no .sh scripts and no tracked gradlew.bat; if either arrives later it keeps its bit.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every tracked file except gradlew is mode 100644, which git ls-files -s confirms
- [ ] #2 gradlew stays executable and ./gradlew test runs from a fresh clone of the result
- [ ] #3 The sweep is a single commit that changes modes only, with no content diff
<!-- AC:END -->
