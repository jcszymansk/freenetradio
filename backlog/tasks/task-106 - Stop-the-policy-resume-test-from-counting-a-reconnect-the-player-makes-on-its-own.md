---
id: TASK-106
title: >-
  Stop the policy resume test from counting a reconnect the player makes on its
  own
status: To Do
assignee: []
created_date: '2026-10-11 18:06'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 120000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OpenRadioPlayerPolicyPauseTest.aStationThePolicyPausedPlaysAgainOverANewConnectionWhenResumed asserts that the broadcast saw exactly two connections, and fails about one run in five with three, on master as well (seen while verifying TASK-104 on the test34 API 34 emulator). The player apparently opens an extra connection now and then, before the pause or on the resume, which the test does not tell apart from the pause failing to close the first one. A flaky case in the gate suite trains people to rerun red builds.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The cause of the third connection is identified and recorded on this task
- [ ] #2 The case asserts what the policy pause promises (the old connection is closed and the resume plays over a new one) without depending on how many connections the player opens
- [ ] #3 The case passes twenty consecutive runs on the offline emulator
<!-- AC:END -->
