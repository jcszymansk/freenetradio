---
id: TASK-045
title: Harden the service's broadcast receivers
status: To Do
assignee: []
created_date: '2026-09-19 10:42'
labels: []
milestone: m-0
dependencies: []
type: chore
ordinal: 60000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Three things about how the service's receivers are registered and what they accept, found while covering them for TASK-005.02.

AbstractReceiver.register passes ContextCompat.RECEIVER_EXPORTED for every receiver. Both receivers it registers, BecomingNoisyReceiver and BTConnectionReceiver, subscribe only to protected system broadcasts that no other application can send, so exporting them buys nothing and offers a surface that does not need to exist. RECEIVER_NOT_EXPORTED is the flag that matches what they listen for.

BTConnectionReceiver.onReceive never checks intent.action, unlike BecomingNoisyReceiver, which does. Only the intent filter keeps a foreign intent out today, which is exactly the guarantee the exported flag weakens.

BTConnectionReceiver also logs the full intent bundle at info level on every connection event, which puts the remote device's MAC address in the log the diagnostics feature lets the user share.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Receivers that listen only to protected broadcasts are registered not exported
- [ ] #2 BTConnectionReceiver acts only on the action it subscribed to
- [ ] #3 No Bluetooth hardware address is written to the log at info level
<!-- AC:END -->
