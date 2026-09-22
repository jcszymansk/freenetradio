---
id: TASK-083
title: Drop or fix the playlist entry track number
status: To Do
assignee: []
created_date: '2026-09-22 16:32'
labels: []
milestone: m-0
dependencies: []
references:
  - >-
    common/src/main/java/wseemann/media/jplaylistparser/playlist/PlaylistEntry.kt
priority: low
type: chore
ordinal: 97000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Every parser writes PlaylistEntry.TRACK, and nothing in the application reads it. The numbers it holds are not meaningful anyway: each parser instance counts from 1, so a playlist that names other playlists repeats numbers, and an ASX entry whose REF is followed as a playlist uses up a number the entries after it skip. Found while covering the ASX parser in TASK-066.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Either TRACK is removed from every parser and from PlaylistEntry, or it numbers the entries of one resolution 1..n without gaps or repeats and a test pins that
<!-- AC:END -->
