---
id: TASK-094
title: >-
  Let the now-playing description fall back the same way for a missing and an
  empty field
status: To Do
assignee: []
created_date: '2026-10-05 05:39'
labels: []
milestone: m-1
dependencies: []
priority: low
type: bug
ordinal: 108000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MediaItemHelper.getDisplayDescription, which fills the description line of the now-playing view in MediaPresenterImpl, tries the subtitle, then the description, then the artist extra, then the caller's default. An empty subtitle moves on to the description, but a null subtitle returns the default at once without looking at the description or the artist; the description step has the same split. So a station that sets no subtitle shows the default even when it carries a description, and the KDoc lists the chain in a different order from the code. MediaItemHelperTest pins the current behaviour and says it looks unintended. Found while clearing the TASK-068 gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A missing field and an empty field fall through to the next one alike, at every step of the chain, pinned by MediaItemHelperTest
- [ ] #2 The KDoc describes the chain in the order the code follows
<!-- AC:END -->
