---
id: TASK-069
title: Stop a timed-out browse command from reading as an empty catalogue
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 20:32'
updated_date: '2026-09-22 04:36'
labels:
  - test
milestone: m-0
dependencies: []
ordinal: 83000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemCommandTestSupport.AWAIT_SECONDS is 10 while MediaItemCommand.CMD_TIMEOUT_MS is 5000, so a command that exhausts its own timeout still answers inside the latch the tests wait on. The timeout path calls resultListener.onResult() with its defaults: no items, page UrlLayer.FIRST_PAGE_INDEX, no error. Any test whose expectation is that state passes on a command that did nothing. TASK-054 closed this one test at a time, by asserting the presenter counter each of five tests could name, which leaves every future empty-expectation test to remember the same thing. Shortening the await below the command timeout closes the class instead: a timed-out command then fails awaitResult by name.

Five restored-instance tests are the remaining live instance, and the counters do not reach them because their claim is entirely negative. MediaItemAllCategoriesTest, MediaItemChartsTest, MediaItemChildCategoriesTest, MediaItemCountryStationsTest and MediaItemSearchTest each name themselves after delivering a cached node and then assert that no items arrived and the provider was not asked, which is bit for bit what a command that never ran delivers. Their fixtures (mCategories, mPopularStations, mSearchStations) are never observed. Found while reviewing TASK-054.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A command that exhausts CMD_TIMEOUT_MS fails the test that awaits its result, rather than delivering an empty one inside the await
- [ ] #2 The five restored-instance tests assert the cached node they are named for, not only that nothing was delivered
- [ ] #3 assertMediaIds rejects an empty expectation, which today degenerates to comparing two empty lists
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Bind RecordingCommandListener's await deadline to MediaItemCommand.CMD_TIMEOUT_MS (half of it) so a command that has not answered by then fails awaitResult/awaitError by name instead of passing as an empty result.
2. Give RecordingCommandListener an assertion for the restored-instance contract: the result is already delivered when execute() returns (the early return happens before the coroutine launches), and it carries no items, no stations, and the first page index.
3. Rewrite the five restored-instance tests around that assertion and rename them so the name matches the claim - the command leaves the node to BrowseTree, it does not deliver it.
4. Make assertMediaIds(first, vararg rest) so an empty expectation does not compile.
5. Cover the harness itself in MediaItemCommandTestSupportTest: the await is shorter than the command timeout, and a command that does not answer in time fails awaitResult by name.
6. Run ./gradlew test.
<!-- SECTION:PLAN:END -->
