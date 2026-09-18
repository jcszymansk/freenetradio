---
id: TASK-029
title: Answer the browse request when a provider node comes back empty
status: Done
assignee:
  - '@claude'
created_date: '2026-09-18 04:27'
updated_date: '2026-09-18 07:32'
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
- [x] #1 Browsing __ALL_CATEGORIES__ with no categories available completes with an empty list rather than hanging
- [x] #2 Browsing __COUNTRIES_LIST__ with no countries available completes with an empty list rather than hanging
- [x] #3 A parent id that matches no MediaItemCommand completes with an error rather than leaving the future unset
- [x] #4 OpenRadioServiceBrowseTest asserts the completed empty results instead of pinning the hang
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Pull the empty-node answer into one place: an internal extension on MediaItemCommandDependencies that delivers the collected (empty) media items through the result listener and then reports the no-data message. MediaItemCommandImpl.handleDataLoaded already does exactly this by hand, so it adopts the helper too.
2. MediaItemAllCategories.loadAllCategories and MediaItemCountriesList.loadAllCountries call that helper instead of only pushing the playback-state message, so the browse future is always set.
3. OpenRadioService.callWhenSourceReady and callWhenSearchReady: narrow the generic from T to LibraryResult<V> so an error result can be built, and set RESULT_ERROR_BAD_VALUE on the future when no MediaItemCommand matched instead of returning it unset.
4. JVM tests: MediaItemAllCategoriesTest and MediaItemCountriesListTest assert the empty case now delivers an empty result *and* the no-data message.
5. OpenRadioServiceBrowseTest: replace providerNodesNeverAnswerWhenNothingIsCached with a test asserting both provider nodes complete with an empty success list while offline, and add one for a parent id no command matches.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Fixed in MediaItemAllCategories.loadAllCategories and MediaItemCountriesList.loadAllCountries: the empty branch now goes through MediaItemCommandDependencies.reportNoData, which delivers the (empty) result before pushing the no-data message. MediaItemCommandImpl.handleDataLoaded already did both and now shares that helper, so the rule has one copy.

OpenRadioService.callWhenSourceReady and callWhenSearchReady are generic over the action's return type, which is why an error result could not be built there. Narrowed both from T to LibraryResult<V>; the unmatched-command branch now sets RESULT_ERROR_BAD_VALUE instead of returning an unset future. The unused pageSize parameter went with it.

Verification, all on emulator-5554 (API 34) with wifi and data disabled:
- ./gradlew test — BUILD SUCCESSFUL, whole JVM suite.
- ./gradlew :app:connectedDebugAndroidTest — 117 tests, 0 failures, 0 errors.
- The two rewritten JVM cases were confirmed to fail against the pre-fix commands (2 of 6 failed) before the fix was restored.
- OpenRadioServiceBrowseTest.providerNodesAnswerWithAnEmptyListWhenNothingIsCached completes in 0.19s where it previously had to wait out an 8s bounded timeout.

Not addressed, and outside the acceptance criteria: a MediaItemCommand whose body throws still leaves the same future unset, though on Dispatchers.IO with a plain Job that surfaces as an uncaught exception rather than a hang.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Browsing a provider node that comes back empty now completes with an empty list instead of hanging the request.

The two commands that hand-rolled the empty branch, MediaItemAllCategories and MediaItemCountriesList, only pushed a playback-state message and returned; OpenRadioService completes the browse future from the result listener alone, so onGetChildren stayed pending. Both now call the new MediaItemCommandDependencies.reportNoData, which delivers the empty result and then reports the message, the same order MediaItemCommandImpl.handleDataLoaded already used and now shares.

A parent id matching no command left the same future unset in callWhenSourceReady and callWhenSearchReady. Both are pinned to LibraryResult<V> so an error can be constructed, and that branch now answers RESULT_ERROR_BAD_VALUE.

Verified with the whole JVM suite (./gradlew test) and 117 instrumented tests on an emulator with networking disabled, 0 failures. The rewritten JVM cases were checked to fail against the pre-fix commands. OpenRadioServiceBrowseTest now asserts an empty success for both nodes and rejection for an unknown parent id.
<!-- SECTION:FINAL_SUMMARY:END -->
