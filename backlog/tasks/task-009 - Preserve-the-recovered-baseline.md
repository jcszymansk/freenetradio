---
id: TASK-009
title: Preserve the recovered baseline
status: Done
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
references:
  - doc/source-provenance.md
type: chore
ordinal: 23000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The upstream project disappeared from GitHub; this repository continues an archived copy. Provenance had to be recorded before modernization so the origin of the code stays provable.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Source release and provenance recorded in the repository
- [x] #2 Recovered state tagged before substantial modernization
- [x] #3 LICENSE, NOTICE and file-level copyright notices retained
- [x] #4 Checked-in APKs treated as historical artifacts, never as build inputs
<!-- AC:END -->
