---
id: TASK-004.03
title: Browse the remaining provider nodes from seeded cache fixtures
status: Done
assignee:
  - '@claude'
created_date: '2026-09-18 13:36'
updated_date: '2026-09-18 14:04'
labels: []
milestone: m-0
dependencies:
  - TASK-029
parent_task_id: TASK-004
type: chore
ordinal: 49000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-004.02 proved a provider backed node can be browsed over a real Media3 connection with networking off, by seeding its response into the Room API cache under the exact provider URL: aCachedProviderNodeIsBrowsableWhileOffline does it for the popular stations node. Only that one node got the treatment. TASK-029 then made the categories and countries nodes answer when nothing is cached, so they are now browsed over a real connection, but only in the empty case.

That leaves the phase exit check short. Five nodes have never been browsed with children over a real connection, and every one of them is reachable by the same seeding technique, so the gap is fixture writing rather than anything the design prevents. The class comment on OpenRadioServiceBrowseTest still says a node whose data has to be fetched cannot be reached; the popular stations case disproves it and the comment needs to go with this work.

Two nodes are deliberately not in scope. __SEARCH_FROM_APP__ is unreachable from any client and TASK-032 deletes it. The car root needs a production change before instrumentation can reach it at all, and TASK-033 carries that work along with its own criterion to exercise it.

The country stations node is the awkward one: its command calls getAllCountries in a runBlocking before its own fetch, so that case needs both URLs seeded, and it takes its country code from the parent id rather than from a stored setting.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Browsing __ALL_CATEGORIES__ over a real connection returns the seeded categories as browsable children
- [x] #2 Browsing __COUNTRIES_LIST__ over a real connection returns the seeded countries as browsable children
- [x] #3 Browsing __NEW_STATIONS__ over a real connection returns the seeded stations as playable children
- [x] #4 Browsing a __CHILD_CATEGORIES__ parent id carrying a category id returns that category stations
- [x] #5 Browsing a __COUNTRY_STATIONS__ parent id carrying a country code returns that country stations
- [x] #6 Each case owns its provider URLs and clears the static in-memory cache entry first, so no case can pass on a response another case left behind
- [x] #7 The stale class comment claiming fetch backed nodes are unreachable is gone
- [x] #8 The instrumented suite passes with wifi and mobile data disabled, repeated without order dependence
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add five seeded browse cases to OpenRadioServiceBrowseTest, each keyed to the Radio Browser URL its command builds, each removing its own InMemoryApiCache entry before seeding Room and invalidating the node: __ALL_CATEGORIES__, __COUNTRIES_LIST__, __NEW_STATIONS__, __CHILD_CATEGORIES__<id>, __COUNTRIES_LIST__<code> (which resolves to __COUNTRY_STATIONS__).
2. Drop __COUNTRIES_LIST__ from providerNodesAnswerWithAnEmptyListWhenNothingIsCached. OpenRadioServicePresenterImpl memoizes countries in a process wide TreeSet that nothing ever clears, so once any case browses countries the empty case is unobservable for the rest of the process, in any order. __ALL_CATEGORIES__ has no memo and still proves the service completes onGetChildren from an empty result; the countries empty path stays covered by MediaItemCountriesListTest.
3. Do not seed the countries URL for the country stations case. MediaItemCountryStations warms countries and discards the result, so seeding it there would let that case fill the memo the countries case needs empty to read its own fixture.
4. Assert the Radio Browser provider is bound, as OpenRadioServiceSearchTest does, so a WebRadioDB build cannot pass these fixtures silently.
5. Replace the stale class comment claiming fetch backed nodes are unreachable.
6. Run the instrumented suite with wifi and mobile data disabled, then again to check for order dependence.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Five seeded cases were added to OpenRadioServiceBrowseTest, each keyed to the URL its command builds and each dropping both API cache entries for that URL before seeding Room, through the new seedResponse/forgetCachedResponse helpers.

Two findings changed the shape of the work.

OpenRadioServicePresenterImpl memoizes the country list in a TreeSet that DependencyRegistryCommon builds once per process, and nothing empties it: not clear() behind CMD_CLEAR_CACHE, not close() on service destroy, not a source switch. The instrumented suite runs every class in one process (plain AndroidJUnitRunner, no orchestrator, see TASK-031), so after any case browses countries the offline-empty case for that node is unreachable in any order. __COUNTRIES_LIST__ was therefore dropped from the empty-result case, which keeps __ALL_CATEGORIES__ and still proves the service completes onGetChildren from an empty result; MediaItemCountriesListTest covers the countries empty answer against a fake presenter. Filed as TASK-035.

MediaItemCountryStations warms the same memo before its own fetch and discards the answer, so the country stations case deliberately does not seed the countries URL. Seeding it there would let that case fill the memo that seededCountriesBecomeBrowsableChildren needs empty to read its own fixture.

setUp now browses the root, which asserts the Radio Browser provider was bound (the fixtures key its URLs) and leaves the root as the previously browsed node, so an IndexableMediaItemCommand resets its process-wide page index and every case asks for page one.

Verification. ./gradlew :app:connectedDebugAndroidTest with 'adb shell svc wifi disable && adb shell svc data disable': 122 tests, all passing, run four times. The browse class alone: 14 tests passing.

Order independence was forced rather than assumed. JUnit4's default method order happened to run the empty-result case before the seeded categories case and the country stations case before the countries case, so the opposite orders were produced by temporarily renaming methods under @FixMethodOrder(MethodSorters.NAME_ASCENDING) and running twice more: once with the empty case last, once with the seeded cases ahead of both parent-id cases and the empty case. Both were green; the scaffolding was then removed and the suite re-run.

On criterion 6: every case owns its URL except the categories one, which the seeded case and the empty-result case necessarily share, because production builds one URL for that node. Both drop the in-memory entry and the Room row for it before browsing, which is the ordering the two forced runs above exercised.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added five seeded browse cases to OpenRadioServiceBrowseTest, covering __ALL_CATEGORIES__, __COUNTRIES_LIST__, __NEW_STATIONS__, a __CHILD_CATEGORIES__ parent id and a __COUNTRIES_LIST__<code> parent id, each keyed to the Radio Browser URL its command builds and each dropping both API cache entries for that URL first through the new seedResponse/forgetCachedResponse helpers. setUp now browses the root, which asserts the bound provider and resets the process-wide page index of the indexable commands. The class comment claiming a fetch backed node is unreachable offline is gone, and __COUNTRIES_LIST__ left the empty-result case because the presenter memoizes the country list in a set nothing empties (TASK-035); __ALL_CATEGORIES__ still covers the empty answer through a real connection and MediaItemCountriesListTest covers the countries one. Verified by running the instrumented suite offline, 122 tests green, plus two forced method orders that put each seeded case ahead of the case it could have poisoned.
<!-- SECTION:FINAL_SUMMARY:END -->
