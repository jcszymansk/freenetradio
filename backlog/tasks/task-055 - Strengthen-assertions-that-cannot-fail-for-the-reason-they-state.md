---
id: TASK-055
title: Strengthen assertions that cannot fail for the reason they state
status: To Do
assignee: []
created_date: '2026-09-21 17:45'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 70000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Four shapes assert something weaker than the test name claims. MediaMetadata.isPlayable and isBrowsable are nullable, so assertFalse(x == true) passes on null and states "not explicitly true" rather than "explicitly false"; an item that forgot to set the flag satisfies it, in MediaItemAllCategoriesTest, MediaItemCarRootTest, MediaItemCountriesListTest, MediaItemFavoritesListTest and MediaItemRootTest. OpenRadioServicePresenterImplTest.settingsAnswerWithTheirStoredDefaults and anUntouchedProfileStreamsOverMobile build the presenter on ContextWrapper(null), so with returnDefaultValues getSharedPreferences returns null, AbstractStorage falls back to the default it was handed, and the test reduces to assertEquals(X, X) with nothing stored and nothing read; the file already has preferencesContext() and uses it in gate(). BrowseTreeTest computes both sides from BrowseTree, and get and getMediaItemsByMediaId return the same list instance, so it asserts self-consistency and would not catch set storing the wrong children. MediaItemCommandTestSupport.assertNoError sleeps 250 ms and then asserts no error arrived, used by twelve tests; the boolean is asserted so it is not a swallow, but the negative claim is probabilistic and decays into a flaky pass, and since result and error come from the same coroutine, asserting the error count straight after awaitResult is stronger and instant. Found while auditing criterion 8 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The isPlayable and isBrowsable assertions state the value they expect rather than "not true"
- [ ] #2 The presenter settings tests read back through a preferences fake instead of a null preferences layer
- [ ] #3 BrowseTreeTest asserts the children it stored rather than a value the tree computed
- [ ] #4 The no-error claim is asserted deterministically rather than after a fixed settle
<!-- AC:END -->
