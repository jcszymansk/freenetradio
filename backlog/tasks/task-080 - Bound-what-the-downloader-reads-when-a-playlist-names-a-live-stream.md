---
id: TASK-080
title: Bound what the downloader reads when a playlist names a live stream
status: To Do
assignee: []
created_date: '2026-09-22 16:32'
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
- [ ] #1 A playlist read through the downloader that exceeds a stated size limit ends the read and adds nothing, and the limit is recorded with its reason
- [ ] #2 Catalogue and image downloads are unaffected by the limit
- [ ] #3 A test drives an endless response and shows the read ends
<!-- AC:END -->
