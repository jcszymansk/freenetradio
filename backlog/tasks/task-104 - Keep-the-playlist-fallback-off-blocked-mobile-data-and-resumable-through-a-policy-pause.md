---
id: TASK-104
title: >-
  Keep the playlist fallback off blocked mobile data and resumable through a
  policy pause
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-10 06:59'
updated_date: '2026-10-11 17:45'
labels: []
milestone: m-1
dependencies: []
type: bug
ordinal: 118000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService resolves a station url the player could not read as a playlist, and NetUtils.extractUrlsFromPlaylist downloads it through the DownloaderLayer without consulting the mobile-network policy, so a playlist read that starts on or overlaps blocked mobile data still spends mobile data. resolvePlaylist also stops the player through OpenRadioPlayer.stop(), which NetworkRecovery counts as a user stop, so a policy pause that lands while a playlist resolves finds no station meant to play and the station does not resume when a usable network returns; playResolvedStream then meets the blocked policy in handlePlayRequestUiThread and gives up with only a toast. Split out of TASK-102, which stops the player's own stream loading during the policy pause.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 No playlist is downloaded while playback over the mobile network is blocked
- [ ] #2 A station whose playlist resolution meets a policy pause resumes when a network it may use connects
- [ ] #3 The service's own stop before a playlist read no longer counts as the user stopping the station
- [ ] #4 Covered by a test that needs neither a network nor a real stream
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. OpenRadioPlayer.stopForPlaylistRead(): stops the stream the player could not read without counting as a stop in NetworkRecovery, and remembers that the station awaits the stream its playlist names. prepare, stop, pause, setPlayWhenReady(false), reset and the policy pause forget it.
2. NetworkRecovery.isStreamPlayRequested takes that awaited-stream fact, so a policy pause landing between the read and the resolved stream records PAUSED_BY_NETWORK_POLICY for a station still wanted playing.
3. NetUtils.extractUrlsFromPlaylist takes a mayUseNetwork predicate checked before the station url is opened and before every nested playlist read, and answers with a PlaylistRead: Urls or Blocked. Blocked replaces a partial answer, so a read cut short by the policy is not mistaken for an empty playlist.
4. PlaylistFallback: onReadBlocked(resolution) answers the pending read with Idle and Action.PauseForNetworkPolicy; abandonRead() turns a pending read into Idle so its late answer is ignored and the resumed request reads the playlist again.
5. OpenRadioService: resolvePlaylist checks the policy before reading and passes the policy as the predicate; one main-thread pauseForNetworkPolicy path abandons a pending read and pauses the player, used by the connectivity and settings events, by a blocked read, and by playResolvedStream when the policy blocks the resolved stream (which keeps the resolved url so the resume plays it).
6. NetworkLayerImpl's network type becomes @Volatile, since the predicate reads it from the IO thread.
7. Tests: PlaylistFallbackTest and NetworkRecoveryTest on the JVM; PlaylistResolutionTest (no request reaches the loopback server while blocked, nested reads stop when the policy trips) and OpenRadioPlayerPolicyPauseTest (a stop for a playlist read is not a user stop, and a policy pause during it resumes) on the offline emulator.
<!-- SECTION:PLAN:END -->
