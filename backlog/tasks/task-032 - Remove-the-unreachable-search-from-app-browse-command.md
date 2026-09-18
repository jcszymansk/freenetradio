---
id: TASK-032
title: Remove the unreachable search-from-app browse command
status: To Do
assignee: []
created_date: '2026-09-18 08:54'
labels: []
dependencies: []
type: chore
ordinal: 46000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemSearchFromApp is registered as the browse command for __SEARCH_FROM_APP__ in OpenRadioServicePresenterImpl, but no client ever sends that id to the service. MediaResourcesManager intercepts it on the client side and calls getSearchResult instead, which OpenRadioService routes through callWhenSearchReady to __SEARCH_FROM_SERVICE__. The only producer of the id is SearchDialog, on that same intercepted path, so the id is a client-side stack marker and the service-side command behind it is unreachable.

The two commands are near-duplicates: MediaItemSearchFromService passes a MediaIdBuilder that prefixes results with "search:", MediaItemSearchFromApp passes none. Keeping the dead twin means the search results a client actually receives are built by the one class the JVM suite barely covers, while four of the five cases in MediaItemSearchTest exercise the unreachable one.

Found while auditing browse-node coverage for TASK-004: the node cannot be exercised over a real Media3 connection because nothing can reach it, so this is a deletion rather than a missing test. Same shape as TASK-028.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 MediaItemSearchFromApp and its registration in OpenRadioServicePresenterImpl are gone
- [ ] #2 MediaId.MEDIA_ID_SEARCH_FROM_APP survives only as the client-side marker used by SearchDialog and MediaResourcesManager, with no service-side command bound to it
- [ ] #3 The MediaItemSearchTest cases that exercised the deleted class now exercise MediaItemSearchFromService, including the saved-instance and empty-result paths
- [ ] #4 Search from the phone UI still returns results, verified over a real Media3 connection rather than from code reading
<!-- AC:END -->
