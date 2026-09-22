---
id: TASK-081
title: Decide whether an HLS entry in a playlist is followed or kept as a stream
status: To Do
assignee: []
created_date: '2026-09-22 16:32'
labels: []
milestone: m-0
dependencies: []
references:
  - common/src/main/java/wseemann/media/jplaylistparser/parser/AbstractParser.kt
  - >-
    common/src/main/java/wseemann/media/jplaylistparser/parser/pls/PLSPlaylistParser.kt
priority: medium
type: bug
ordinal: 95000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The parsers disagree about an entry whose url ends in .m3u8. PLSPlaylistParser.savePlaylistFile keeps it as a stream, because ExoPlayer plays HLS itself. AbstractParser.parseEntry, which ASX, M3U, M3U8 and XSPF entries go through, treats .m3u8 as a playlist extension: it reads the HLS playlist and adds what it names, so a master playlist becomes its variant urls and a media playlist becomes its segment urls, and the station then plays the first segment instead of the live stream. The behaviour predates TASK-066, which kept it unchanged; ASXPlaylistParserTest avoids a .m3u8 REF for that reason.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 One rule decides whether an entry ending in .m3u8 is followed, and every parser applies it
- [ ] #2 Tests pin the rule for a PLS entry, an ASX REF and an M3U entry
<!-- AC:END -->
