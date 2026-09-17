---
id: TASK-011
title: Establish an independent application identity
status: In Progress
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: feature
ordinal: 25000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The application must not be confusable with the commercial original, and must not reuse any signing identity or branding associated with the previous owner. Name, application ID, provider authorities, command IDs, notification and diagnostic text and the user agent are already migrated; icons and a release signing key are not.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Project and application name that cannot be confused with the commercial application
- [x] #2 applicationId changed before user data is created or builds are published
- [ ] #3 Package-dependent authorities, labels, icons, links and contact details replaced
- [ ] #4 Release signing key generated and stored outside the repository with an offline backup
- [x] #5 Debug builds left on Android's normal debug signing path
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Done: renamed to FreeNetRadio, applicationId com.github.jcszymansk.freenetradio, provider authorities, media-session command IDs, notification and diagnostic text and user agent all migrated. Funding config, the original author's profile link and the upstream project/issue URLs were removed and repointed at this repository. Outstanding: new application icons, and generating the release signing key.
<!-- SECTION:NOTES:END -->
