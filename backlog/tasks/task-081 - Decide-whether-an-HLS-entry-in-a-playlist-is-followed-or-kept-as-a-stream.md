---
id: TASK-081
title: Decide whether an HLS entry in a playlist is followed or kept as a stream
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-22 16:32'
updated_date: '2026-10-05 09:32'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Decide the rule: an address ending in .m3u8 that a playlist names is an HLS stream and is kept as one, because media3-exoplayer-hls plays it, while reading it as a playlist turns a master playlist into its variants and a media playlist into its segments.
2. Put the rule in AutoDetectParser (isHlsUrl, consulted by isPlaylistUrl) so it is decided once, by extension, the same way dispatch reads extensions.
3. Apply it in AbstractParser for entries (parseEntry) and for ASX ENTRYREF (follow), and drop PLSPlaylistParser's own substring check.
4. Replace the AutoDetectParserTest case that relied on following a nested .m3u8, and pin the rule for PLS, ASX REF, ASX ENTRYREF, M3U, M3U8 and XSPF entries, including case and query strings.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Rule: an address whose path ends in .m3u8 (any case, query and fragment ignored) that a playlist names is kept as a stream, decided by AutoDetectParser.isHlsUrl and applied in AbstractParser to every entry and to ASX ENTRYREF. getFileExtension now reads the last path segment only, because a dot in a query (index.m3u8?t=1.5) or an upper case extension before a query hid the extension and let an ENTRYREF expand HLS. Keeping the stream only works if the player reads it as HLS: OpenRadioService swapped only the uri and kept the station playlist's MIME type (AUDIO_UNKNOWN for .pls/.asx/.xspf), which makes Media3 open it progressively, so the replacement item now gets its MIME type from the resolved url (MediaItemBuilder.withStreamUrl). Follow-ups from review: TASK-096 (unbounded re-resolution when the resolved stream fails too), TASK-097 (an extensionless HLS station url is still resolved to segments or a variant).
<!-- SECTION:NOTES:END -->
