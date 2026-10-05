---
id: TASK-089
title: Pin the downloader read limit on the JVM
status: To Do
assignee: []
created_date: '2026-10-05 05:13'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 103000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-080 bounded what HTTPDownloaderImpl reads when a playlist names a live stream. The bound is decided in HTTPDownloaderImpl.toByteArray(input, maxBytes), which is stream arithmetic with no platform behind it, but it is private to the companion, so its only test is the instrumented PlaylistResolutionTest.aReferencedPlaylistLongerThanTheLimitIsRefused over a LoopbackHttpFixture. That is not the lowest appropriate layer, and the test serves a padded finite response, whereas TASK-080's third criterion talks about an endless one. A JVM test can feed an InputStream that never ends. Judged on TASK-068: the regression test exists, so criterion 6 holds, but its placement is a debt, and moving it needs a production seam that the gate run could not add after the Android Auto check of 8ab2329.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A JVM test shows the read stops at the limit on an input stream that never ends, and fails if the limit is removed
- [ ] #2 The instrumented loopback test is kept only for what needs a real connection, or removed if nothing does
<!-- AC:END -->
