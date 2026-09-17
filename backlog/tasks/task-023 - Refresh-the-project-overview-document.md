---
id: TASK-023
title: Refresh the project overview document
status: To Do
assignee: []
created_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
references:
  - doc/project-overview.md
type: docs
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
doc/project-overview.md has drifted from the tree: it reports four JVM test files when there are twenty, and its build table lists Gradle 8.0 and AGP 8.1.2 while the wrapper is 8.2 and constants.gradle sets 8.2.2.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test counts and coverage description match the tree
- [ ] #2 Build and toolchain table matches constants.gradle and the Gradle wrapper
- [ ] #3 Statements about removed features re-checked against the code
<!-- AC:END -->
