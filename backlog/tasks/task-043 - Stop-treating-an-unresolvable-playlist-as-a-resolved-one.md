---
id: TASK-043
title: Stop treating an unresolvable playlist as a resolved one
status: To Do
assignee: []
created_date: '2026-09-19 10:32'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 58000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
NetUtils.extractUrlsFromPlaylist answers a failure and an empty result with the same value: it starts result as Array(1) { EMPTY_STRING } and only replaces it once a parse has succeeded, so a url it could not open, a connection it could not make and a body it could not parse all come back as a one-element array holding an empty string.

OpenRadioService.handlePlayListUrlsExtracted only asks whether the answer is empty. A one-element array is not, so it takes the success branch and rebuilds the current media item with setUri(urls[0]), putting an empty url on the station the user selected, then asks the player to play it. The station is left pointing at nothing until something else replaces it, and the error the user sees is about the empty url rather than about the playlist.

Found while covering playback for TASK-005.02; the instrumented test anUnreachablePlaylistLeavesTheStationWithAnEmptyUrl pins the current behavior and has to be rewritten when this is fixed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A playlist that cannot be opened or parsed is distinguishable from one that names no streams
- [ ] #2 Neither case leaves an empty url on the station
- [ ] #3 Covered by a test that needs no external network
<!-- AC:END -->
