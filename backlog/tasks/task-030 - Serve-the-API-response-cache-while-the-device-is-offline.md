---
id: TASK-030
title: Serve the API response cache while the device is offline
status: Done
assignee:
  - '@claude'
created_date: '2026-09-18 04:27'
updated_date: '2026-09-18 06:01'
labels: []
dependencies: []
type: bug
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ModelLayerImpl.downloadData asks NetworkLayer.checkConnectivityAndNotify before it consults either cache, and returns an empty response when the answer is no. A fresh response sitting in the in-memory cache or in the 24-hour Room cache is therefore discarded in exactly the situation the cache exists for, and every provider-backed node comes back empty offline.

The connectivity check belongs immediately before the download, not before the lookups: the caches are local reads that cannot fail for lack of a network. The no-network toast should follow the same move, so it only fires when a request would really have been made.

Found while covering the Media3 service contract in TASK-004.02, where it blocked that task's ninth acceptance criterion, so it was fixed there rather than left for a separate branch. OpenRadioServiceSearchTest.searchAnswersFromTheSeededCacheWithoutLeavingTheDevice seeds the Room cache with a Radio Browser search response and asserts the station comes back through a real MediaBrowser, and OpenRadioServiceBrowseTest.aCachedProviderNodeIsBrowsableWhileOffline does the same for the popular stations browse node. That node is used rather than the categories one so it shares no cache key with the case that pins TASK-029.

Note that the instrumented suite runs with networking disabled by design, so before this fix no offline browse test could assert real data.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A cached response is returned when the device reports no connectivity
- [x] #2 The no-network notification is raised only when a download would actually have been attempted
- [x] #3 A cache miss while offline still returns an empty response rather than attempting a download
- [x] #4 OpenRadioServiceSearchTest asserts the seeded station comes back from the cache
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Moved the connectivity check in ModelLayerImpl.downloadData to sit immediately before the download, leaving the in-memory and Room cache lookups ahead of it. Both are local reads that cannot fail for lack of a network, and asking first threw away a fresh cached response in the one situation the cache exists for. The no-network toast lives inside checkConnectivityAndNotify, so it now fires only when a request was really going to be made.

Fixed inside TASK-004.02 rather than on its own branch: it blocks that task's ninth acceptance criterion, so it was on the critical path rather than incidental. The two rounds of review on TASK-004.02 both returned to it.

ModelLayerImplTest changed with it. noConnectivityReturnsNoDataWithoutTouchingCachesOrDownloader pinned the old ordering and is now noConnectivityAndNoCacheReturnsNoDataWithoutDownloading, which asserts both caches are consulted and nothing is downloaded. Two cases were added for a persistent and a memory hit while offline, and the two online hit cases now assert zero connectivity checks, which is what proves no spurious toast fires on a cache hit.

Verified: ./gradlew test --rerun-tasks passes; OpenRadioServiceSearchTest asserts the seeded Radio Browser station comes back through a real MediaBrowser with emulator networking disabled, and OpenRadioServiceBrowseTest.aCachedProviderNodeIsBrowsableWhileOffline does the same for the categories browse node.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ModelLayerImpl.downloadData now reads both API caches before it asks about connectivity, so a cached response is served offline instead of being discarded, and the no-network notification fires only when a download would really have been attempted. Verified by ModelLayerImplTest on the JVM (offline persistent hit, offline memory hit, offline miss, and zero connectivity checks on an online cache hit) and end to end by two instrumented tests that browse a seeded provider node and a seeded search through a real MediaBrowser with emulator networking disabled.
<!-- SECTION:FINAL_SUMMARY:END -->
