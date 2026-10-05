---
id: TASK-081
title: Decide whether an HLS entry in a playlist is followed or kept as a stream
status: Done
assignee:
  - '@claude'
created_date: '2026-09-22 16:32'
updated_date: '2026-10-05 09:46'
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
- [x] #1 One rule decides whether an entry ending in .m3u8 is followed, and every parser applies it
- [x] #2 Tests pin the rule for a PLS entry, an ASX REF and an M3U entry
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Rule: an address a playlist names whose file name (last path segment without query, fragment or path parameters) has the extension .m3u8, in any case, is kept as a stream, because media3-exoplayer-hls plays HLS, while reading it as a playlist turns a master playlist into its variants and a media playlist into its segments.
2. Decide it once in AutoDetectParser.isHlsUrl; isFollowedEntryUrl consults it, and getFileExtension reads the exact extension of the file name.
3. Apply it in AbstractParser to every entry (parseEntry) and to ASX ENTRYREF (follow); drop PLSPlaylistParser's own substring check.
4. Make the player read a kept HLS address as HLS: the replacement MediaItem takes its MIME type from the resolved url (MediaItemBuilder.withStreamUrl), and AppUtils.getMimeTypeFromUri drops path parameters; the MimeTypeMap stub follows the platform rule.
5. Pin the rule for PLS, M3U, M3U8, XSPF entries, ASX REF and ENTRYREF, and pin the MIME type against Media3's own content type inference.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Rule: an address whose path ends in .m3u8 (any case, query and fragment ignored) that a playlist names is kept as a stream, decided by AutoDetectParser.isHlsUrl and applied in AbstractParser to every entry and to ASX ENTRYREF. getFileExtension now reads the last path segment only, because a dot in a query (index.m3u8?t=1.5) or an upper case extension before a query hid the extension and let an ENTRYREF expand HLS. Keeping the stream only works if the player reads it as HLS: OpenRadioService swapped only the uri and kept the station playlist's MIME type (AUDIO_UNKNOWN for .pls/.asx/.xspf), which makes Media3 open it progressively, so the replacement item now gets its MIME type from the resolved url (MediaItemBuilder.withStreamUrl). Follow-ups from review: TASK-096 (unbounded re-resolution when the resolved stream fails too), TASK-097 (an extensionless HLS station url is still resolved to segments or a variant).

Validation: ./gradlew test verifyPureCoreCoverage assembleDebug pass on b4dfb38. Every new parser and builder test was run against the code before its fix and failed there. Codex review loop passed in round 4.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Every playlist parser now keeps an address whose file name ends in .m3u8 as a stream instead of reading it, through one rule (AutoDetectParser.isHlsUrl) applied in AbstractParser to entries and to ASX ENTRYREF; PLS lost its own substring check. getFileExtension now reads the exact extension of the file name, so a query, fragment or path parameter cannot hide it and .m3u8x is not HLS. So that the kept address actually plays as HLS, the item that replaces a station's playlist takes its MIME type from the resolved url (MediaItemBuilder.withStreamUrl), getMimeTypeFromUri ignores path parameters, and the MimeTypeMap stub now matches the platform. Verified by AutoDetectParserTest, ASXPlaylistParserTest, MediaItemBuilderTest (against Media3's Util.inferContentTypeForUriAndMimeType) and MimeTypeMapTest; full JVM suite, pure-core gate and assembleDebug pass. Follow-ups: TASK-096, TASK-097.
<!-- SECTION:FINAL_SUMMARY:END -->
