---
id: TASK-022
title: Restrict access to the station artwork content provider
status: To Do
assignee: []
created_date: '2026-09-17 18:26'
labels:
  - security
milestone: m-1
dependencies: []
references:
  - common/src/main/AndroidManifest.xml
type: bug
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ImagesProvider is declared exported with no read permission, so any application on the device can read the artwork store. That store holds directory artwork and also any image the user picked from their own gallery for a local station, which makes it a real exposure rather than only a disclosure problem. Found while rewriting the privacy policy; the current POLICY discloses the behavior honestly in the meantime.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Provider no longer readable by arbitrary applications, by permission or by not exporting it
- [ ] #2 Phone UI artwork still loads
- [ ] #3 Android Auto artwork still loads, verified on a real head unit or the Desktop Head Unit
- [ ] #4 POLICY updated once the exposure is gone
<!-- AC:END -->
