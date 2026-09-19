---
id: TASK-046
title: Reconcile the sleep timer's stored state with the timer that is running
status: To Do
assignee: []
created_date: '2026-09-19 10:43'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 61000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Three ways SleepTimerModelImpl's stored state and its running coroutine disagree, found while covering the timer for TASK-005.02.

init() writes as a side effect of reading. It calls updateTimer(loadDate().time, isEnabled()), and updateTimer unconditionally calls saveDate(time). On a fresh install SleepTimerStorage.loadDate() answers with the current time, so the first application start persists 'now' as the user's alarm. From then on an alarm nobody ever set is indistinguishable from one the user chose.

setEnabled(false) writes the flag and nothing else; it never calls mTimer.handle. A timer that is already running therefore keeps running, and will still close the service, while isEnabled() answers false. SleepTimerDialog.onDetach happens to call updateTimer afterwards, which hides this in the one flow that exists today.

isTimestampNotValid rejects a timestamp equal to the current time, while SleepTimerImpl.handle accepts it: the first uses <= and the second checks delay < 0. The two disagree about the same instant.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Reading the stored alarm does not write one
- [ ] #2 Disabling the timer cancels a timer that is running
- [ ] #3 The model and the timer agree about a timestamp equal to the current time
- [ ] #4 Covered by tests that need no service and no real clock beyond a bounded wait
<!-- AC:END -->
