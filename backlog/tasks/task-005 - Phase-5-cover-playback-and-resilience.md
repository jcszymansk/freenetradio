---
id: TASK-005
title: 'Phase 5: cover playback and resilience'
status: Done
assignee: []
created_date: '2026-09-17 18:24'
updated_date: '2026-09-19 16:56'
labels: []
milestone: m-0
dependencies:
  - TASK-004
type: chore
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The roadmap's highest operational risk, tested without relying on real radio streams. Do not invoke the service's process-killing stop path inside instrumentation: extract its decision logic for a component test and retain one manual lifecycle check.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Exit check: core playback and recovery behavior passes with networking disabled and no physical audio output required
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Phase closed against a full re-run rather than the subtasks' recorded results. Emulator-5554 (API 34) with wifi and mobile data disabled, verified unreachable during the run (ping answers "Network is unreachable", dumpsys reports "Wi-Fi is disabled"): 178 instrumented tests, 0 failures, 0 errors, 0 skipped, 35.6s. JVM suite re-run with --rerun-tasks: 365 executions across the debug and release variants of :android-jvm-stubs, :common and :common-ui, 0 failures. No audio device is attached to the emulator, so the playback cases are proven not to need one.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Phase 5 is complete. TASK-005.01 added the playback seams (the error classifier and the single isPlaybackBlockedByMobileNetwork question), TASK-005.02 added the coverage: 68 cases over playback against a generated local WAV, playlist resolution, error classification, network loss and recovery, the becoming-noisy and bluetooth receivers, and the sleep timer. Three paths are covered below the service because nothing above it can reach them (two protected broadcasts and a completion path that kills the process), and each file records why. Exit check verified by re-running both suites: 178 instrumented tests pass on emulator-5554 with networking disabled and no audio device, and the JVM suite passes clean. Nine defects the coverage exposed are tracked as TASK-040 through TASK-048; TASK-037 owns the resume half of network recovery.
<!-- SECTION:FINAL_SUMMARY:END -->
