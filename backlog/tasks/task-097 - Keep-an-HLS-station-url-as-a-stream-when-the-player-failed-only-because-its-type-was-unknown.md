---
id: TASK-097
title: >-
  Keep an HLS station url as a stream when the player failed only because its
  type was unknown
status: To Do
assignee: []
created_date: '2026-10-05 09:32'
labels: []
milestone: m-0
dependencies: []
priority: low
type: bug
ordinal: 111000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A station url without a .m3u8 extension that serves HLS gets MimeTypes.AUDIO_UNKNOWN from AppUtils.getMimeTypeFromUri, so DefaultMediaSourceFactory opens it as a progressive stream and fails with UnrecognizedInputFormatException. OpenRadioService then parses it with AutoDetectParser, whose content sniffing finds #EXT-X- and picks M3U8PlaylistParser: a media playlist becomes its segment urls, so the station plays one segment and stops, and a master playlist becomes its first variant, which drops adaptive bitrate. AutoDetectParserTest.aVariantOfAMasterPlaylistIsKeptAsAStream currently pins the master-to-variant answer. TASK-081 settled HLS addresses that a playlist names; this is the same mistake for the station url itself. A likely fix is to answer HLS content at the top level with the station url and the HLS MIME type, rather than its contents.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A station url without a .m3u8 extension that serves an HLS media or master playlist is played by the HLS source, not resolved to segments or variants
- [ ] #2 JVM tests pin the answer for a media playlist and a master playlist served from an extensionless url
<!-- AC:END -->
