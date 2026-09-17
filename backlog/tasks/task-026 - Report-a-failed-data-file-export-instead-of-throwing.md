---
id: TASK-026
title: Report a failed data-file export instead of throwing
status: To Do
assignee: []
created_date: '2026-09-17 19:29'
labels: []
dependencies: []
type: bug
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
FileStoreManager.onActivityResult wraps the import branch in a try/catch and calls onFailure, but the export branch does not: ContentResolver.openOutputStream throws FileNotFoundException when the chosen document is gone or cannot be written, and that exception escapes into FileStorageDialog.onActivityResult and crashes the app. The failure callback that would show the user an error is never reached. Found while writing the Phase 3 persistence tests (TASK-003); FileStoreManagerTest currently pins the throwing behaviour and has to be updated together with the fix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An export to a location that cannot be opened invokes the failure callback
- [ ] #2 No exception escapes onActivityResult for either request code
- [ ] #3 FileStoreManagerTest asserts the failure callback instead of the thrown exception
<!-- AC:END -->
