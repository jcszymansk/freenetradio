---
id: TASK-096
title: >-
  Stop re-resolving a station playlist whose resolved stream the player cannot
  read either
status: To Do
assignee: []
created_date: '2026-10-05 09:32'
labels: []
milestone: m-0
dependencies: []
priority: medium
type: bug
ordinal: 110000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
When ExoPlayer raises UnrecognizedInputFormatException, OpenRadioService.onHandledError calls handleUnrecognizedInputFormatException, which resolves the station url (mActiveRS.getStreamUrlFixed) through NetUtils.extractUrlsFromPlaylist and replaces the current MediaItem with urls[0] (handlePlayListUrlsExtracted). If that resolved stream also fails as unrecognised, the same handler resolves the same station url again, gets the same answer, and plays it again. Nothing bounds this, and every lap reads the playlist over the network. Nothing in the service remembers that the current item is already a resolved stream, nor tries the other urls the playlist returned. Found in review of TASK-081 (which made more playlist entries resolve to an HLS address, and gave the replacement item a MIME type read from the resolved url so HLS is actually played as HLS).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A resolved stream that fails as unrecognised does not trigger another resolution of the same station url within one play request
- [ ] #2 Either the remaining urls the playlist named are tried in order, or playback stops with an error the user sees; the choice is recorded and tested
- [ ] #3 The bound is pinned by a test that fails on the current unbounded behaviour
<!-- AC:END -->
