---
id: TASK-010
title: Reduce the supported product surface
status: Done
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: chore
ordinal: 24000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Android TV and native Automotive OS could not be tested or maintained by a single maintainer. Git history remains the recovery path if someone volunteers to own either target.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 tv and automotive removed from settings.gradle
- [x] #2 tv/ and automotive/ application modules deleted
- [x] #3 Dependencies and configuration used only by those modules removed
- [x] #4 Phone Android Auto metadata, OpenRadioService, Media3 session integration and car browse behavior kept
- [x] #5 Shared TV or Automotive code removed only after reference checks and an Android Auto smoke test
- [x] #6 Phone/tablet and Android Auto documented as the only supported targets
<!-- AC:END -->
