---
id: TASK-047
title: Make sleep timer completion reach every listener
status: To Do
assignee: []
created_date: '2026-09-19 10:43'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 62000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
SleepTimerModelImpl.SleepTimerListenerImpl.onComplete fans the completion out in a plain loop, and the only try/catch around it is CoroutineTimerTask's, which wraps the whole action rather than each listener. A listener that throws therefore swallows the exception and every listener after it, including OpenRadioService's, which is the one that actually stops playback. The user's sleep timer silently does nothing.

The loop is also wrapped in synchronized(mTimerListenerExt) while neither addSleepTimerListener nor removeSleepTimerListener takes that monitor, so the lock guards nothing; the queue is a ConcurrentLinkedQueue and does not need it. mTimerListenerExt is a var that is never reassigned.

Found while covering the timer for TASK-005.02.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 One listener failing does not stop the others from being called
- [ ] #2 The lock that guards nothing is gone, or it guards every access
- [ ] #3 Covered by a test with a listener that throws
<!-- AC:END -->
