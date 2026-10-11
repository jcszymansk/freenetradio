---
id: TASK-104
title: >-
  Keep the playlist fallback off blocked mobile data and resumable through a
  policy pause
status: Done
assignee:
  - '@claude'
created_date: '2026-10-10 06:59'
updated_date: '2026-10-11 19:48'
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
- [x] #1 No playlist is downloaded while playback over the mobile network is blocked
- [x] #2 A station whose playlist resolution meets a policy pause resumes when a network it may use connects
- [x] #3 The service's own stop before a playlist read no longer counts as the user stopping the station
- [x] #4 Covered by a test that needs neither a network nor a real stream
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

8. Review rounds 1-7 extended step 3: a NetworkPermit (one per playlist read) asks the policy before every request, every redirect hop (GET redirects are now followed in getHttpURLConnection) and, through GatedInputStream, every read; it latches the first refusal so a read the policy stopped is never mistaken for an empty one. The service revokes the permits of reads under way on a policy pause (off the main thread); revocation disconnects held connections, a connection only starts through NetworkPermit.connect under a lock revoke also takes, and the policy is asked again at that start.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Validation: ./gradlew test, verifyPureCoreCoverage, assembleDebug and :app:assembleDebugAndroidTest pass. Full :app:connectedDebugAndroidTest on the offline test34 emulator: 252/252 on the final commit (OpenRadioPlayerPolicyPauseTest.aStationThePolicyPausedPlaysAgainOverANewConnectionWhenResumed is flaky on master too, about one run in five, filed as TASK-106). Mutation checks: the player's awaited-stream handling, GatedInputStream, the NetworkPermit latch, revoke disconnecting, the connect lock and the policy check at connect each turn the matching tests red. Codex review loop: 7 rounds, last verdict FAIL on two findings that round 7 fixed; the loop hit its round limit before reviewing that fix, and the user chose to close as is. Residual: the policy turning between the final check at connect and the system connect call cannot be closed. Not verified on a real phone on blocked mobile data. Every GET in the app now follows redirects in NetUtils rather than in HttpURLConnection, verified only against the loopback server.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
A station url the player could not read is no longer resolved over blocked mobile data, and a station whose playlist resolution meets a policy pause resumes on a usable network. The service stops the player for a replacement stream through OpenRadioPlayer.stopForReplacementStream, which is not a user stop, and the policy pause counts such a station as wanted. PlaylistFallback gained onReadBlocked and abandonRead, and a blocked resolved stream pauses for the policy instead of giving up. Playlist reads go through a per-read NetworkPermit that checks the policy before every request, redirect hop and read, and that the service revokes on a policy pause to disconnect reads under way. Verified by PlaylistFallbackTest, NetworkRecoveryTest, GatedInputStreamTest, NetworkPermitTest, PlaylistResolutionTest and OpenRadioPlayerPolicyPauseTest, with the full JVM and offline instrumented suites passing.
<!-- SECTION:FINAL_SUMMARY:END -->
