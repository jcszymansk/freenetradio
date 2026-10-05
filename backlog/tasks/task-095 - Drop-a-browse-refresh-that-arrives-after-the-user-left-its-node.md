---
id: TASK-095
title: Drop a browse refresh that arrives after the user left its node
status: In Progress
assignee:
  - '@claude'
created_date: '2026-10-05 06:19'
updated_date: '2026-10-05 19:11'
labels: []
milestone: m-1
dependencies: []
priority: medium
type: bug
ordinal: 109000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Editing or removing a local station sends CMD_UPDATE_TREE, and the service notifies the root and the locals node. MediaResourcesManager.onChildrenChanged checks that the pushed parent is the one subscribed to and then launches getChildren, but does not check again when the fetch returns. If the user presses back meanwhile, subscribe(ROOT) unsubscribes the locals node and renders the root, and the late locals answer still reaches MainActivity's MediaItemsSubscriptionCallback.onChildrenLoaded and MediaPresenterImpl.handleChildrenLoaded, neither of which compares the parent id with the top of the media item stack. The adapter then shows the locals list while the presenter stands at the root, and the add button, which follows the stale parent id, is hidden. Logcat from a failing journey run on 2026-10-05: children changed for the locals list at 13.569, subscribe ROOT at 13.584, root rendered with five children at 13.600, then the locals answer with zero children at 13.679 on top of it. Master has the same window: in six passing runs the walk back started 10 to 30 ms after the push. LocalStationLifecycleJourneyTest now waits for the refresh to render before walking back, so it no longer provokes the race, and FavoriteLifecycleJourneyTest will meet it once TASK-050 makes unmarking refresh the favorites node. Found while clearing the TASK-068 gate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A children answer for a node the user is no longer showing never replaces the list that is showing, pinned by a JVM test in common-ui
- [ ] #2 Both paths that fetch children, the change notification and subscribe, are covered by that guard
- [ ] #3 A journey that walks back while a refresh is in flight ends on the root list
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Make the presenter's browse stack the single record of the node being shown: addMediaItemToStack moves an id already on the stack to the top, so the top is always the node last asked for.
2. Hand MediaResourcesManager a presenter-owned subscription instead of the activity callback. It forwards an answer (children or error) only when its parent id is the top of the stack, and logs the dropped one. Both fetch paths (subscribe, and onChildrenChanged, which reuses the callback given to subscribe) then pass through it, and so does every side effect MainActivity's callback has (progress bar, add button, empty message).
3. JVM tests in common-ui: a late answer for a node that was left is dropped after a back press and after navigating forward; the shown node's answer still renders; errors follow the same rule; re-adding an id already on the stack moves it to the top.
4. Journey: fill the locals store with a full page of stations so the locals answer reliably arrives after the root, open the locals list, ask for a refresh and walk back as soon as it is acknowledged; assert the root rows, the rendered node and the add button. A single seeded station and a refresh burst never lost the race, and walk-back cycles caught it in one run of three.
5. Run JVM tests, the coverage gate, and the instrumented suite offline.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Verified: ./gradlew test and verifyPureCoreCoverage pass; full :app:connectedDebugAndroidTest offline, 221 tests, pass. The new journey case failed 3/3 against the pre-fix presenter (add button hidden at the root) and passed 3/3 with the fix. MediaPresenterShownNodeTest: 7 of 9 cases fail with the guard disabled; askingAgainForANodeDeeperInTheStackShowsThatNode fails without the move-to-top.
<!-- SECTION:NOTES:END -->
