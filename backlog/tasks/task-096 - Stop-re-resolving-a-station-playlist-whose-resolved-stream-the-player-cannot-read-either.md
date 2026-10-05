---
id: TASK-096
title: >-
  Stop re-resolving a station playlist whose resolved stream the player cannot
  read either
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-05 09:32'
updated_date: '2026-10-05 14:14'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add PlaylistFallback (shared.service): an immutable state machine for one play request. An unrecognised stream of a station that is not being resolved asks for one resolution of its url; the urls that resolution returns are tried in order on each further unrecognised error of that station; when none is left, or the playlist named none, the service gives up. A resolution that arrives for a station no longer being resolved is ignored.
2. Decision for AC #2: try the remaining urls in order, then stop with a visible error. Playlists commonly list mirrors of one stream, so a second entry is a real chance; once all are spent the player stops and the now-playing subtitle reads a new 'Unplayable Stream' message instead of nothing.
3. A play request starts when a controller sends a player command that starts, stops or changes playback (onPlayerCommandRequest); that resets the fallback, so pressing play again on the same station re-reads its playlist once. The service's own restarts (network and Bluetooth reconnect) are not new requests.
4. Wire OpenRadioService to it: key everything on the current media item id, read the station url from the browse tree for that id instead of mActiveRS, and drop the race where a late resolution replaced whatever item was current.
5. JVM tests own PlaylistFallback (list it in gradle/pure-core-coverage.tsv); an instrumented test in OpenRadioServiceRecoveryTest pins that a playlist whose stream is unreadable is fetched once and ends on the new subtitle, failing on the current behaviour.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The loop was not literally unbounded: PlaybackErrorClassifier counts an unrecognised format against its error budget, so the seventh lap was classified Unrecoverable and showed 'Error'. Measured on the emulator before the fix: a playlist whose only stream is unreadable was requested 7 times (the player's own read plus six resolutions). A lap that reached READY would have refilled the budget, and the budget is not reset between stations, so it was a bound by accident.

Decision (AC #2): the remaining urls are tried in order, then playback stops with the 'Unplayable Stream' subtitle (R.string.media_stream_unplayable), which the phone UI and Android Auto show under the station name. Playlists list mirrors of one stream, so a second entry is worth trying; a playlist that names nothing ends the same way.

A play request starts with any controller command that starts, stops or changes playback (PlaylistFallback.isPlayRequest, consulted in ServiceCallback.onPlayerCommandRequest). The service's own restarts after a network or Bluetooth reconnect are not requests, so they meet the exhausted state and give up without reading the playlist again.

Also fixed on the way, because the same code did it: a resolution that arrived after the user moved to another station replaced whichever item was current, and the station url came from mActiveRS, which is set asynchronously; it now comes from the browse tree for the failing media id. The withTimeout around the resolution was removed: NetUtils.extractUrlsFromPlaylist blocks without suspending, so it could never fire. An empty url in the resolution (TASK-043) is still played as given.
<!-- SECTION:NOTES:END -->
