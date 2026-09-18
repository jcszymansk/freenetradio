---
id: TASK-030
title: Serve the API response cache while the device is offline
status: To Do
assignee: []
created_date: '2026-09-18 04:27'
labels: []
dependencies: []
type: bug
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ModelLayerImpl.downloadData asks NetworkLayer.checkConnectivityAndNotify before it consults either cache, and returns an empty response when the answer is no. A fresh response sitting in the in-memory cache or in the 24-hour Room cache is therefore discarded in exactly the situation the cache exists for, and every provider-backed node comes back empty offline.

The connectivity check belongs immediately before the download, not before the lookups: the caches are local reads that cannot fail for lack of a network. The no-network toast should follow the same move, so it only fires when a request would really have been made.

Found while covering the Media3 service contract in TASK-004.02. OpenRadioServiceSearchTest.searchAnswersFromTheProviderPathWithoutLeavingTheDevice seeds the Room cache with a Radio Browser search response and pins the current behavior: the search completes, reports zero results and leaves the cached row untouched. Fixing this also lets that test assert the seeded station, which is what the acceptance criterion for search in TASK-004.02 originally intended.

Note that the instrumented suite runs with networking disabled by design, so this defect is the only reason offline browse tests cannot assert real data.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A cached response is returned when the device reports no connectivity
- [ ] #2 The no-network notification is raised only when a download would actually have been attempted
- [ ] #3 A cache miss while offline still returns an empty response rather than attempting a download
- [ ] #4 OpenRadioServiceSearchTest asserts the seeded station comes back from the cache
<!-- AC:END -->
