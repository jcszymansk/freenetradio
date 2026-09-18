---
id: TASK-029
title: Answer the browse request when a provider node comes back empty
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-18 04:27'
updated_date: '2026-09-18 07:24'
labels: []
dependencies: []
type: bug
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemAllCategories.loadAllCategories and MediaItemCountriesList both return without calling dependencies.resultListener.onResult when the provider yields an empty set. They only push a playback-state message. The withTimeoutOrNull wrapper in execute does not rescue that: the block completes normally, so the elvis branch that would deliver an empty result never runs.

OpenRadioService.callWhenSourceReady answers onGetChildren through a SettableFuture that only the result listener sets, so the browse request is never completed. A MediaBrowser, the phone list and an Android Auto head unit all wait forever with a spinner instead of seeing an empty or error state.

An empty set is the normal case whenever the device is offline and nothing is cached, which makes this reachable in ordinary use. Found while covering the Media3 service contract in TASK-004.02; OpenRadioServiceBrowseTest.providerNodesNeverAnswerWhenNothingIsCached pins the current behavior with a bounded wait and has to be rewritten to assert an empty list once this is fixed. The adjacent case, the same kind of node browsing correctly from a seeded cache, is covered by OpenRadioServiceBrowseTest.aCachedProviderNodeIsBrowsableWhileOffline.

The same shape exists where callWhenSourceReady and callWhenSearchReady return an unset future because no command matched the parent id.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Browsing __ALL_CATEGORIES__ with no categories available completes with an empty list rather than hanging
- [ ] #2 Browsing __COUNTRIES_LIST__ with no countries available completes with an empty list rather than hanging
- [ ] #3 A parent id that matches no MediaItemCommand completes with an error rather than leaving the future unset
- [ ] #4 OpenRadioServiceBrowseTest asserts the completed empty results instead of pinning the hang
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Pull the empty-node answer into one place: an internal extension on MediaItemCommandDependencies that delivers the collected (empty) media items through the result listener and then reports the no-data message. MediaItemCommandImpl.handleDataLoaded already does exactly this by hand, so it adopts the helper too.
2. MediaItemAllCategories.loadAllCategories and MediaItemCountriesList.loadAllCountries call that helper instead of only pushing the playback-state message, so the browse future is always set.
3. OpenRadioService.callWhenSourceReady and callWhenSearchReady: narrow the generic from T to LibraryResult<V> so an error result can be built, and set RESULT_ERROR_BAD_VALUE on the future when no MediaItemCommand matched instead of returning it unset.
4. JVM tests: MediaItemAllCategoriesTest and MediaItemCountriesListTest assert the empty case now delivers an empty result *and* the no-data message.
5. OpenRadioServiceBrowseTest: replace providerNodesNeverAnswerWhenNothingIsCached with a test asserting both provider nodes complete with an empty success list while offline, and add one for a parent id no command matches.
<!-- SECTION:PLAN:END -->
