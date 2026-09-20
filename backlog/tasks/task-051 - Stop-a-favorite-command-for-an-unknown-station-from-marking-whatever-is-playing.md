---
id: TASK-051
title: >-
  Stop a favorite command for an unknown station from marking whatever is
  playing
status: To Do
assignee: []
created_date: '2026-09-20 05:53'
updated_date: '2026-09-20 06:11'
labels: []
dependencies: []
type: bug
ordinal: 66000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioService.handleFavorite resolves the media id the caller named, and when it cannot it falls back to the active station unconditionally: the second fallback branch copies mActiveRS and rewrites mediaId to that station's id, whatever id was asked about. It then writes the favorites store through updateRadioStationFavorite and only afterwards looks the media item up in the browse tree, so a command it is about to refuse with RESULT_ERROR_NOT_SUPPORTED has already changed the store. The answer and the effect disagree, and the station that is marked is one the caller never named.

mActiveRS is set when a station starts playing and is never cleared for the life of the process, so this is reachable for the whole session after the first play, from any connected controller: the phone, Android Auto or a test browser.

Found by TASK-006.04's offline playback journey, which is the first test in the suite to leave a station active before OpenRadioServiceCommandTest runs. Its favoriteCommandRejectsAStationOutsideTheBrowseTree then fails on the store it asserts is empty, having passed until now only because nothing had ever played in that process. The comment in the code says the fallback is for a favorite changed from the automotive UI, where the event can only concern the now playing item, so the fallback itself is wanted - it is applying it to an id the caller did name that is not.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A favorite command naming a station the service cannot resolve stores nothing
- [ ] #2 A command that reports RESULT_ERROR_NOT_SUPPORTED leaves the favorites store as it found it
- [ ] #3 A controller that names the active station, or names nothing, still marks the now playing station
- [ ] #4 Covered by a test that reaches no network and does not depend on whether a station has played in the same process
- [ ] #5 OpenRadioServiceCommandTest.aFavoriteCommandForAnUnknownStationMarksTheStationThatIsPlaying asserts the refusal and the untouched store instead of the mark it pins today
- [ ] #6 Both pinned cases in OpenRadioServiceCommandTest, the RESULT_SUCCESS branch and the refusing one, assert a store the command left alone
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
The fallback has two outcomes and both are now pinned in OpenRadioServiceCommandTest. When the active station is still in the browse tree the command answers RESULT_SUCCESS and marks it; when it is not, the store is written on the way to RESULT_ERROR_NOT_SUPPORTED, so the answer and the effect disagree. The second is the one a full suite run meets, because a class that played something clears up after itself.

The contract the command is supposed to keep cannot be asserted from an instrumented test while this stands: mActiveRS is set on the first play and never cleared, and the service shares its process with the instrumentation, so no later test can get back to having no active station. Restoring that coverage is part of this fix rather than something a test can do first.
<!-- SECTION:NOTES:END -->
