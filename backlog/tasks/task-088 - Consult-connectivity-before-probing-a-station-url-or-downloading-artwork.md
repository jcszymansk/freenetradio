---
id: TASK-088
title: Consult connectivity before probing a station url or downloading artwork
status: To Do
assignee: []
created_date: '2026-10-05 05:13'
labels: []
milestone: m-1
dependencies: []
priority: low
type: bug
ordinal: 102000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
NetworkLayer.checkConnectivityAndNotify is the app's only connectivity gate, and its only consumers are ModelLayerImpl, right before a provider download, and MediaPresenterImpl.handleItemSelected. Two other paths reach the network without asking. Adding or editing a local station runs RadioStationValidatorImpl, whose probe is NetUtils.checkResource over HttpURLConnection, so offline the user is told the stream is invalid when nothing was checked. Showing artwork runs ImagesProvider.openFile, then ImagesPersistenceLayerImpl.open, which on a Room miss downloads through the shared downloader, up to six connection attempts per image, every time an image is shown while offline. TASK-052 made the instrumented suite refuse a networked device, which neutralises both paths for the tests but not for users. The toasting gate is wrong for artwork, which loads constantly in the background. Found while clearing the TASK-068 gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Adding or editing a station while offline reports that the network is unavailable instead of that the stream is invalid, pinned by a JVM test of the validator
- [ ] #2 An artwork miss while offline makes no connection attempt and shows no toast
- [ ] #3 Online behaviour of both paths is unchanged
<!-- AC:END -->
