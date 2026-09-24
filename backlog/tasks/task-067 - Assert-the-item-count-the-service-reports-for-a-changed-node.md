---
id: TASK-067
title: Assert the item count the service reports for a changed node
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 19:59'
updated_date: '2026-09-24 18:07'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 82000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
bcea65a changed notifyChildrenChanged from PAGE_SIZE to Int.MAX_VALUE. The count tells a Media3 browser how many children to expect, so at 250 a node holding more than a page would have been under-fetched by the client, which is the defect that was fixed. Nothing asserts it. ServiceBrowser records ChildrenChanged(parentId, itemCount) for every callback, and itemCount is read in exactly one place, OpenRadioServiceSearchTest, which asserts the search result count rather than the browse one. The browse-side value could go back to 250, or to 1, and the suite would stay green.

The subscription tests already prove that the right parent ids are notified, so what is missing is one assertion on the value that travels with them. Found while auditing criterion 6 of TASK-007: this is one of the sub-fixes inside a commit whose subject named something else, and so never got a decision about where its test belonged.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A browse node refresh asserts the item count the service reports, not only that the node was notified
- [x] #2 The assertion fails if the count goes back to a page size
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. In OpenRadioServiceBrowseTest.subscribesAndUnsubscribesRootAndChildNodes, assert the itemCount of the ROOT and LOCALS pushes after CMD_UPDATE_TREE equals Int.MAX_VALUE, Media3's documented 'unknown' count (the service invalidates the node and cannot know its size before the rebuild).
2. Exact equality fails for 250 (PAGE_SIZE), 1, or any other finite value.
3. Compile the androidTest APK and run the class on an offline emulator; mutate the service to PAGE_SIZE and confirm the test goes red.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Asserted exact Int.MAX_VALUE: Media3 1.2.1 documents itemCount as the number of children or Integer.MAX_VALUE if unknown, and the service invalidates the node before it knows the new size. Our own MediaResourcesManager ignores the count, so the defect is for other Media3 browsers. Verified: OpenRadioServiceBrowseTest 14/14 on an offline emulator; with notifyChildrenChanged mutated to 250 the test fails with 'The push for __ROOT__ reported a child count instead of Media3's unknown expected:<2147483647> but was:<250>'; ./gradlew test green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
subscribesAndUnsubscribesRootAndChildNodes now asserts that the ROOT and local stations pushes after CMD_UPDATE_TREE carry itemCount Int.MAX_VALUE, Media3's unknown count, so a regression to the page size (or any finite value) fails. Verified on an offline emulator, including a mutation run with the count set back to 250.
<!-- SECTION:FINAL_SUMMARY:END -->
