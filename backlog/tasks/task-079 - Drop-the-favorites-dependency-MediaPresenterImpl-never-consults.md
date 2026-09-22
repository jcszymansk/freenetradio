---
id: TASK-079
title: Drop the favorites dependency MediaPresenterImpl never consults
status: To Do
assignee: []
created_date: '2026-09-22 15:16'
labels: []
milestone: m-0
dependencies: []
references:
  - >-
    common-ui/src/main/java/com/yuriy/openradio/shared/presenter/MediaPresenterImpl.kt
  - >-
    common-ui/src/main/java/com/yuriy/openradio/shared/presenter/MediaPresenter.kt
  - >-
    common-ui/src/main/java/com/yuriy/openradio/shared/dependencies/DependencyRegistryCommonUi.kt
  - >-
    common-ui/src/main/java/com/yuriy/openradio/shared/view/list/MediaItemsAdapter.kt
type: chore
ordinal: 93000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaPresenterImpl takes a FavoritesStorage constructor parameter that no line of the class reads, and DependencyRegistryCommonUi.init passes sFavoritesStorage into it. It is not an orphan on its own: MediaPresenter declares isFavorite(mediaId: String), MediaPresenterImpl answers a hardcoded false, and nothing anywhere calls it. The parameter, the interface method and the stub are one dead trio, all of it inherited from upstream bc70ede ('Save the progress', 2023-06-21).

The stub is superseded rather than unfinished, which matters because the obvious reading is that someone left the favorites lookup half written and it should now be wired up. The favorite checkbox in the list gets its state from MediaItemsAdapter.handleFavoriteAction, which reads MediaItemHelper.isFavoriteField off the media metadata the service already stamped, and the two classes that genuinely need a storage answer, EditStationPresenterImpl and OpenRadioServicePresenterImpl, each hold their own FavoritesStorage and call isFavorite(radioStation) on it. There is no caller left for a presenter-level lookup by media id, and adding one would duplicate the metadata the browse tree already carries.

So the work is deletion, not completion. Check that reading before deleting: if a caller does turn up, or the metadata flag proves unreliable for some node, this becomes a different task.

Found while covering handleChildrenLoaded for task-058, where the unused parameter forced the test to build a FavoritesStorage the code under test never touches.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 MediaPresenterImpl no longer takes a FavoritesStorage, and DependencyRegistryCommonUi no longer passes one to it
- [ ] #2 MediaPresenter.isFavorite and its implementation are gone, or a documented caller is named that justifies keeping them
- [ ] #3 MediaPresenterChildrenLoadedTest no longer constructs a FavoritesStorage
- [ ] #4 The favorite checkbox still shows the right state for a favorited station, verified through the existing favorite lifecycle journey
<!-- AC:END -->
