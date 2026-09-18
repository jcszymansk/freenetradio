---
id: TASK-035
title: Clear the memoized country list when app data is cleared
status: To Do
assignee: []
created_date: '2026-09-18 13:54'
labels: []
dependencies: []
type: bug
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioServicePresenterImpl memoizes the provider's country list into a TreeSet that DependencyRegistryCommon builds once per process, and nothing ever empties it. clear(), behind CMD_CLEAR_CACHE, drops the two API caches, the stored images and the latest station; close(), on service destroy, drops the in-memory API cache; a source switch only writes a preference that a future process reads. So the first non-empty country list is served for the rest of the process and survives a clear, which is the same class of bug as TASK-027.

Found while writing TASK-004.03. The instrumented suite shares one process, so once any case browses countries the offline-empty case for that node can never be observed again, in any order. TASK-004.03 therefore dropped __COUNTRIES_LIST__ from providerNodesAnswerWithAnEmptyListWhenNothingIsCached and left that path covered by MediaItemCountriesListTest against a fake presenter. MediaItemCountryStations fills the same memo as a side effect, because it warms countries before its own fetch and discards the result.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Clearing the app data empties the memoized country list along with the API caches
- [ ] #2 A browse of __COUNTRIES_LIST__ after a clear reads the provider again rather than the memo
- [ ] #3 The instrumented suite observes the offline-empty case for __COUNTRIES_LIST__ again, after another case has already browsed that node
<!-- AC:END -->
