---
id: TASK-013
title: Remove inherited online services
status: Done
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: chore
ordinal: 27000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Firebase, Crashlytics, Firestore backup, the hosted featured-stations feed, Cast and fused location all belonged to infrastructure this project neither owns nor trusts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Firebase Analytics, Crashlytics, Authentication and Firestore backup removed
- [x] #2 Firestore-hosted featured stations and the account/cloud UI removed
- [x] #3 Cast removed
- [x] #4 Automatic country selection reimplemented on Android's platform location API
- [x] #5 Resolved runtime dependency graph contains no Firebase or Google Play Services artifacts
<!-- AC:END -->
