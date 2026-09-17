---
id: TASK-028
title: Drop the write-only local flag from playable media items
status: To Do
assignee: []
created_date: '2026-09-17 21:09'
labels: []
dependencies: []
type: chore
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemBuilder.buildPlayable takes an isLocal argument and MediaItemHelper.updateLocalRadioStationField writes it into the media item extras under KEY_IS_LOCAL. Nothing reads that key: no production code, no test, and no browse client, since the key is private to MediaItemHelper. MediaItemLocalsList is the only caller that passes true. Either the flag has a purpose that was never wired up - the phone list decides local state from the station itself - or it is dead payload carried on every station item. Found while covering the browse commands in TASK-004.01, where the flag could not be asserted because production offers no reader for it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The flag either gains a reader that the browse clients actually use, or the argument, the helper and the key are removed together with their callers
- [ ] #2 MediaItemLocalsListTest states the resulting behavior
<!-- AC:END -->
