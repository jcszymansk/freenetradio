---
id: TASK-033
title: 'Select the browse root from the connecting client, not the process UI mode'
status: To Do
assignee: []
created_date: '2026-09-18 09:00'
labels: []
dependencies: []
type: bug
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DependencyRegistryCommon reads UiModeManager.getCurrentModeType once at process start and freezes the answer into isCar, which decides whether OpenRadioServicePresenterImpl registers MediaItemRootCar plus MediaItemBrowseCar or the phone MediaItemRoot. That decision is then shared by every service instance and every browser connection for the life of the process.

Two defects follow from it.

First, the signal does not track Android Auto. Android documents that on Android 12 and higher Android Auto does not change the UI mode of the device, and that UI_MODE_TYPE_CAR is the normal case only on Automotive OS or when an app calls enableCarMode itself. The check was written for the deleted :automotive module, where it was correct, and was left behind on the phone build by TASK-010. If the documentation holds for this build, MediaItemRootCar and MediaItemBrowseCar are dead code and Android Auto has been receiving the seven-entry phone root instead of the three-entry car root with its Browse node. That is navigable, which is consistent with TASK-017 finding browse usable in the vehicle, but it is the flat wide shape the car root exists to avoid and it bears on the Play Store quality requirements in TASK-021.

Second, even a correct global flag would be wrong. OpenRadioService.onGetLibraryRoot already has the caller in ControllerInfo and logs browser.packageName before discarding it, returning one constant root to every client, and BrowseTree is keyed by parent media id with no client dimension, so a phone client and a car client connected at once share one cache and each can be served the other children. Android guidance for a media browser service is to look at the calling package at connection time and return a different root per client type.

Confirm the first defect empirically before fixing it: DependencyRegistryCommon already logs the CurrentModeType value at startup, so one logcat line from the real phone while connected to the head unit settles it. TASK-017 owns that environment.

Related: TASK-004.02 recorded the same freezing pattern for the provider Source, which init also reads once and binds into the root command. The two may want one fix. Found while auditing browse-node coverage for TASK-004, where the car root turned out to be unreachable from instrumentation for this reason.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The CurrentModeType the phone reports while connected to the head unit is recorded, so the premise rests on an observation rather than on documentation
- [ ] #2 A client connecting from Android Auto receives the car root regardless of which surface started the app process
- [ ] #3 A phone client receives the phone root, including while a car client is connected at the same time
- [ ] #4 Browse results cached for one client class are never served to the other
- [ ] #5 The car root and its Browse node are exercised over a real Media3 connection in the instrumented suite, which the process-wide flag made impossible
- [ ] #6 No process-wide UI-mode flag decides the browse root any more, and the vestigial DependencyRegistryCommon.isCar setter is gone
<!-- AC:END -->
