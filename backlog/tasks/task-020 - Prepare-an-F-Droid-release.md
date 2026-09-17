---
id: TASK-020
title: Prepare an F-Droid release
status: To Do
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:27'
labels: []
milestone: m-2
dependencies:
  - TASK-019
type: feature
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Starts only after the personal release has been stable in regular use. Deliberately kept as one coarse task: splitting it before the work is close would be speculative planning.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Release dependency graph and APK audited for non-free libraries, trackers and unexpected network services
- [ ] #2 Anything incompatible with the F-Droid Inclusion Policy removed or isolated
- [ ] #3 Release builds unattended from tagged source with no bundled secrets or maintainer-local files
- [ ] #4 Versioning deterministic, with no release task that modifies tracked files as a side effect
- [ ] #5 Metadata, screenshots, license, source links, changelog, privacy statement and reproducible build instructions provided
- [ ] #6 Documented that Android Auto may require its developer setting for an app installed outside Google Play
- [ ] #7 Installation, upgrade, background playback and Android Auto verified with a non-Play-signed build
- [ ] #8 Submitted and policy or reproducibility findings addressed without adding distribution-only telemetry
<!-- AC:END -->
