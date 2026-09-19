---
id: TASK-041
title: Stop the initial playlist from shadowing a station's real parent list
status: To Do
assignee: []
created_date: '2026-09-19 10:26'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 56000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService.maybeCreateInitialPlaylist stores the now-playing list it builds under the active station's own id: mBrowseTree[rs.id] = BrowseData(list, radioStations). BrowseTree.getMediaItemsByMediaId searches keys before it searches children, so from then on that station id resolves to the initial playlist rather than to the list the station actually appears in, and nothing invalidates the entry: CMD_UPDATE_TREE invalidates the root and the locals node, never a station id.

onSetMediaItems answers a selection through the same lookup, so selecting that station from any list afterwards loads the stale playlist instead of the list the user was looking at. When favorites are empty and the provider answers with nothing, that playlist is the station alone, and the user loses next and previous entirely.

Found while covering playback for TASK-005.02: the instrumented tests had to avoid triggering maybeCreateInitialPlaylist at all, and seed station ids that no earlier test could have left behind, to keep the effect out of unrelated assertions.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A station id no longer keys a browse-tree entry that shadows the list the station belongs to
- [ ] #2 Selecting a station after an initial playlist has been created loads the list it was selected from
- [ ] #3 Covered by a test that needs no network
<!-- AC:END -->
