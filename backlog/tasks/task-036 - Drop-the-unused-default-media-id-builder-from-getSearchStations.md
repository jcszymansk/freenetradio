---
id: TASK-036
title: Drop the unused default media id builder from getSearchStations
status: To Do
assignee: []
created_date: '2026-09-18 15:55'
labels: []
dependencies:
  - TASK-032
type: chore
ordinal: 51000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioServicePresenter.getSearchStations defaults its mediaIdBuilder to MediaIdBuilderDefault(). MediaItemSearchFromApp was the only production caller that took the default; TASK-032 deleted it, so MediaItemSearchFromService is now the only caller and it always passes the builder that prefixes results with 'search:'. The default is reachable from tests alone, and a default that no production path uses invites a future caller to search without tagging its results, which is the bug TASK-032 removed the room for. Same shape as TASK-028: either the default earns a production caller or it goes away with the tests that were its only user.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 getSearchStations either has a production caller that relies on the default, or the default argument is gone and every caller passes a builder
- [ ] #2 OpenRadioServicePresenterImplTest states the resulting contract rather than exercising a default nothing uses
<!-- AC:END -->
