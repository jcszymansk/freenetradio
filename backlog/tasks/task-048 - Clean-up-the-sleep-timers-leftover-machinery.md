---
id: TASK-048
title: Clean up the sleep timer's leftover machinery
status: To Do
assignee: []
created_date: '2026-09-19 10:43'
labels: []
milestone: m-0
dependencies: []
type: chore
ordinal: 63000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Code around the sleep timer that is dead, unsafe or untidy, found while covering it for TASK-005.02. None of it misbehaves today; all of it is a trap for the next change.

CoroutineTimerTask's repeat loop is unreachable: mRepeat is private, initialised to null and assigned nowhere in the repository, so the while branch can never run.

CoroutineTimerTask.mDelay is a public mutable field written immediately before start(). Nothing but SleepTimerImpl's cancel-first discipline stops two concurrent handle calls from crossing a delay with a start. The job is launched on GlobalScope, so it outlives every scope the application owns.

SleepTimerModelImpl.mCalendar is unsynchronised mutable state shared across threads: init() runs on Dispatchers.IO from MainAppCommonUi while setDate, setTime and getTime are called from the dialog on the main thread, and java.util.Calendar is not thread safe.

Cosmetic, in the same files: the log message 'Sleep timer can ot be started with negative delay', the missing space in 'enabled:Boolean' on both SleepTimer.handle and SleepTimerImpl.handle, and three empty KDoc blocks in BecomingNoisyReceiver.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The unreachable repeat branch is gone or reachable
- [ ] #2 The timer's delay cannot be changed from outside between calls
- [ ] #3 The calendar is not shared across threads unsynchronised
- [ ] #4 The log typo, the parameter spacing and the empty KDoc blocks are fixed
<!-- AC:END -->
