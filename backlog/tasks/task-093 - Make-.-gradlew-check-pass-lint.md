---
id: TASK-093
title: Make ./gradlew check pass lint
status: To Do
assignee: []
created_date: '2026-10-05 05:30'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 107000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Every Android module's check runs lintDebug, and on 2026-10-05 :common:lintDebug reported 46 errors and :common-ui:lintDebug 29, all in code that predates the test roadmap: WebpUnsupported and RestrictedApi among them, NewApi at IntentUtils.kt:84, and onClick handlers lint cannot find in :common-ui. TASK-068 wired the pure-core gate into the root check, so ./gradlew check is now the one command that should answer whether the tree is healthy, and it cannot while lint fails for reasons nobody has looked at. Some findings are likely real defects at minSdk 17, such as the NewApi one, and others may deserve a baseline or a suppression with a reason.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ./gradlew check passes on a clean checkout
- [ ] #2 Every lint error is either fixed or suppressed or baselined with a recorded reason, and a NewApi finding below minSdk is never suppressed without a version check
<!-- AC:END -->
