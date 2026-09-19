---
id: TASK-040
title: Let playback resumption restore the playlist it remembers
status: To Do
assignee: []
created_date: '2026-09-19 10:25'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 55000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioPlayer keeps a shadow copy of the queue in mPlaylist so that OpenRadioService.onPlaybackResumption can offer it back when the player itself has none. Every path that empties the player's queue empties the shadow copy with it: the clearMediaItems and setMediaItems overrides both clear it, and release clears it outright. The only code that leaves the two out of step is the private stopCurrentPlayer, which clears the delegate directly, and nothing reachable calls it except release, which has already cleared the copy.

media3 only routes a play request through onPlaybackResumption when the player has no current item, so the branch that returns the remembered playlist is the only one that can run, and by then it has nothing left to return. The other branch, guarded by mediaItemCount != 0, cannot be reached from a controller at all.

The result is that a play request arriving with an empty queue, which is what a media button or Android Auto sends after the service has been recreated, restores nothing. Found while covering playback for TASK-005.02; the instrumented test aPlayRequestWithNothingLoadedRestoresNothing pins the current behavior and has to be rewritten when this is fixed. Worth checking against TASK-018 before that one is investigated separately.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The playlist the player was last given survives the queue being emptied, or the remembered playlist is dropped along with the branch that reads it
- [ ] #2 A play request with an empty queue restores what was playing and starts it
- [ ] #3 The unreachable mediaItemCount != 0 branch of onPlaybackResumption is either reachable or gone
- [ ] #4 Covered by a test that needs no network and no real stream
<!-- AC:END -->
