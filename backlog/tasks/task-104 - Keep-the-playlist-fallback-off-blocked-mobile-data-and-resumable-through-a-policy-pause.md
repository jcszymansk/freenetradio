---
id: TASK-104
title: >-
  Keep the playlist fallback off blocked mobile data and resumable through a
  policy pause
status: To Do
assignee: []
created_date: '2026-10-10 06:59'
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
