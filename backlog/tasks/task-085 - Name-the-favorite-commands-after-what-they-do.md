---
id: TASK-085
title: Name the favorite commands after what they do
status: To Do
assignee: []
created_date: '2026-09-24 11:10'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 99000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The service's two favorite session commands are named for the state the control is leaving, not the one it asks for. `CMD_FAVORITE_OFF` adds the station to favorites and `CMD_FAVORITE_ON` removes it (`OpenRadioService.onCustomCommand`). The phone's favorite box (`MediaItemsAdapter.handleFavoriteAction`) and the car's custom layout buttons both follow that convention, so the behavior is correct. But any caller that reads the names literally sends the opposite command, and nothing reports it: removing a station that is not a favorite answers `RESULT_SUCCESS` and still notifies the root. The convention is explained again in comments and task notes wherever a test sends one of these commands (`OpenRadioServiceCommandTest`, `ServiceFirstStartupJourneyTest`, `FavoriteLifecycleJourneyTest`, TASK-006.06), which shows the name alone does not carry it.

Both commands are app internal. The phone UI sends them, and Android Auto only sends back the `SessionCommand`s the service itself puts in the custom layout. Neither the constant names nor their string values are a contract with anything outside the app. Found while reviewing TASK-071.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Each favorite command constant and its string value say what the command does to the station, e.g. mark and unmark, rather than naming a button state
- [ ] #2 The phone favorite box, the now-playing favorite box and the car custom layout buttons send the renamed commands and still mark and unmark correctly
- [ ] #3 Every test that sends a favorite command uses the new names, and the comments and KDoc that explain the old leaving-state convention are removed
- [ ] #4 No reference to CMD_FAVORITE_ON or CMD_FAVORITE_OFF remains in app, common or common-ui
<!-- AC:END -->
