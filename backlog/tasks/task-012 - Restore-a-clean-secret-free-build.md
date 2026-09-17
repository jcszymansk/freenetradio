---
id: TASK-012
title: 'Restore a clean, secret-free build'
status: In Progress
assignee: []
created_date: '2026-09-17 18:26'
updated_date: '2026-09-17 18:26'
labels: []
milestone: m-1
dependencies: []
type: chore
ordinal: 26000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The inherited build required a private sign.properties file, a google-services.json and unconditional Firebase plugins, so a clean checkout could not build at all.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Gradle wrapper builds a debug APK from a fresh checkout
- [x] #2 sign.gradle no longer requires sign.properties for ordinary builds
- [x] #3 Release signing configuration required only when a signed release is explicitly requested
- [x] #4 Unconditional Google Services and Crashlytics plugin requirements removed
- [x] #5 No placeholder credentials or dummy google-services.json added
- [x] #6 Gradle, AGP, Kotlin, Media3 and SDK settings updated only as far as a maintainable build needs
- [x] #7 One canonical build command documented with its required JDK and Android SDK versions
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
assembleDebug was verified working in this working copy, and the canonical commands are documented in CLAUDE.md. Criterion 1 stays unchecked until a build is confirmed from a genuinely fresh clone with no ignored files present.
<!-- SECTION:NOTES:END -->
