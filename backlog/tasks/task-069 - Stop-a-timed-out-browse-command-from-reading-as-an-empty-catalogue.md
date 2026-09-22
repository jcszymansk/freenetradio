---
id: TASK-069
title: Stop a timed-out browse command from reading as an empty catalogue
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 20:32'
updated_date: '2026-09-22 04:46'
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
- [x] #1 A command that exhausts CMD_TIMEOUT_MS fails the test that awaits its result, rather than delivering an empty one inside the await
- [x] #2 The five restored-instance tests assert the cached node they are named for, not only that nothing was delivered
- [x] #3 assertMediaIds rejects an empty expectation, which today degenerates to comparing two empty lists
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified by mutating production code and watching the right tests fail.

AC1. Gave MediaItemChildCategories a delay(CMD_TIMEOUT_MS * 2) inside its withTimeoutOrNull, which is a real suspension point, so the command genuinely exhausted its timeout and fell through to the empty onResult(). All five awaiting tests in MediaItemChildCategoriesTest failed with 'Command did not deliver a result', anEmptyFirstPageIsDeliveredWithoutAnError among them - under the old ten second await that command would have answered inside the latch and the test would have gone on to assert an empty node. MediaItemCommandTestSupportTest pins the same thing from the other side: the await is shorter than CMD_TIMEOUT_MS, and a command that answers only after the await fails awaitResult by name rather than being recorded.

AC2. Replacing the synchronous 'deliverResult(dependencies); return' in the restored-instance branch with a coroutine that delivers the identical empty result failed exactly the five restored-instance tests and nothing else, in MediaItemAllCategories, MediaItemPopularStations, MediaItemChildCategories, MediaItemCountryStations and MediaItemSearchFromService. The old assertions could not tell the two apart. MediaItemCountryStations also had a country fixture added so all five presenters now hold data the command declined to ask for.

AC3. assertMediaIds(first, vararg rest) makes the empty expectation a compile error. All 23 call sites pass at least one id and none uses a spread, so nothing else moved.

Doubling AWAIT_MILLIS past CMD_TIMEOUT_MS fails theAwaitRunsOutBeforeACommandCanFallThroughItsOwnTimeout, so the constant cannot drift back.

./gradlew test green (:android-jvm-stubs, :common, :common-ui); the twelve browse command suites report zero failures. The one cost is MediaItemCommandTestSupportTest, which burns the full 2500 ms await on purpose; every other test in the package does under 3 ms of real work.
<!-- SECTION:NOTES:END -->
