---
id: TASK-044
title: Pause only for the Bluetooth device the audio was going to
status: To Do
assignee: []
created_date: '2026-09-19 10:42'
labels: []
milestone: m-0
dependencies: []
type: bug
ordinal: 59000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
BTConnectionReceiver.onReceive reads the disconnecting device's address for its log line and then ignores it: the STATE_DISCONNECTED branch asks only whether mConnectedDevice is not empty, which is true from the first connection of any device onwards. OpenRadioService wires that callback straight to callPause, so any Bluetooth device dropping off pauses playback, including one that was never carrying the audio. A smartwatch going out of range pauses the music playing through the car stereo.

The connect branch does compare addresses; only the disconnect branch does not. Found while covering the receiver for TASK-005.02, and pinned as current behavior by the instrumented test aDisconnectionIsReportedWhicheverDeviceItCameFrom, which has to be rewritten when this is fixed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A disconnection is reported only for the device the receiver last saw connect
- [ ] #2 A disconnection from any other device changes nothing
- [ ] #3 Covered by a test driven with broadcast intents
<!-- AC:END -->
