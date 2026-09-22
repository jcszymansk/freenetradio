---
id: TASK-080
title: Bound what the downloader reads when a playlist names a live stream
status: Done
assignee: []
created_date: '2026-09-22 16:32'
updated_date: '2026-09-22 18:07'
labels: []
milestone: m-0
dependencies: []
references:
  - >-
    common/src/main/java/com/yuriy/openradio/shared/model/net/HTTPDownloaderImpl.kt
  - common/src/main/java/com/yuriy/openradio/shared/utils/NetUtils.kt
priority: high
type: bug
ordinal: 94000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Since TASK-066 every playlist that a playlist names is read through DownloaderLayer, and HTTPDownloaderImpl.BytesDownloader reads the whole response body into memory before returning. A url followed as a playlist (an ASX ENTRYREF, or an entry whose url ends in .pls, .m3u, .asx and so on) can in fact be a live audio stream, which never ends. Before TASK-066 the parser read such a stream line by line from its own connection and never finished; now the downloader buffers it and the process grows until it runs out of memory. readTimeout does not help, because a live stream never stalls. OpenRadioService calls NetUtils.extractUrlsFromPlaylist inside withTimeout, but blocking IO is not cancelled by it. ModelLayerImpl and image loading use the same downloader, so a limit must not break their payloads (WebRadioDB downloads whole JSON datasets).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A playlist read through the downloader that exceeds a stated size limit ends the read and adds nothing, and the limit is recorded with its reason
- [x] #2 Catalogue and image downloads are unaffected by the limit
- [x] #3 A test drives an endless response and shows the read ends
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Folded into TASK-066 the moment it was filed: the round 1 review of that task called the unbounded read a defect of its own change and required the fix there, so it was implemented on branch task-066-asx-entryref rather than left for a separate branch.

DownloaderLayer.downloadDataFromUri gained a maxBytes parameter defaulting to DownloaderLayer.NO_LIMIT, so catalogue and image reads are untouched. HTTPDownloaderImpl refuses a response longer than the limit whole rather than truncating it, because half a playlist parses into wrong entries rather than into an error. NetUtils passes 1 MiB for every playlist read. The four level copy/copyLarge chain the class inherited was replaced by one bounded read; its only public overload had no callers.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Playlist reads through the downloader are bounded at 1 MiB and a longer response is refused whole. Verified by PlaylistResolutionTest.aReferencedPlaylistLongerThanTheLimitIsRefused, which resolves a padded playlist under the limit and gets no urls from the same playlist padded past it, on a device with networking disabled (8/8).
<!-- SECTION:FINAL_SUMMARY:END -->
