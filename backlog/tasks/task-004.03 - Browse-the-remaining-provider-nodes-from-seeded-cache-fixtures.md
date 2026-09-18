---
id: TASK-004.03
title: Browse the remaining provider nodes from seeded cache fixtures
status: To Do
assignee: []
created_date: '2026-09-18 13:36'
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
- [ ] #1 Browsing __ALL_CATEGORIES__ over a real connection returns the seeded categories as browsable children
- [ ] #2 Browsing __COUNTRIES_LIST__ over a real connection returns the seeded countries as browsable children
- [ ] #3 Browsing __NEW_STATIONS__ over a real connection returns the seeded stations as playable children
- [ ] #4 Browsing a __CHILD_CATEGORIES__ parent id carrying a category id returns that category stations
- [ ] #5 Browsing a __COUNTRY_STATIONS__ parent id carrying a country code returns that country stations
- [ ] #6 Each case owns its provider URLs and clears the static in-memory cache entry first, so no case can pass on a response another case left behind
- [ ] #7 The stale class comment claiming fetch backed nodes are unreachable is gone
- [ ] #8 The instrumented suite passes with wifi and mobile data disabled, repeated without order dependence
<!-- AC:END -->
