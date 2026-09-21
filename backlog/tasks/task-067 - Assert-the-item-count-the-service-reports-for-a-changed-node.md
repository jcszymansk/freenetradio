---
id: TASK-067
title: Assert the item count the service reports for a changed node
status: To Do
assignee: []
created_date: '2026-09-21 19:59'
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
- [ ] #1 A browse node refresh asserts the item count the service reports, not only that the node was notified
- [ ] #2 The assertion fails if the count goes back to a page size
<!-- AC:END -->
