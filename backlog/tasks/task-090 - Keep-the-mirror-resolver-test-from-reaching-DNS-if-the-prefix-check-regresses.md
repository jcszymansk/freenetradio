---
id: TASK-090
title: Keep the mirror resolver test from reaching DNS if the prefix check regresses
status: To Do
assignee: []
created_date: '2026-10-05 05:13'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 104000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ConnectionUrlResolverTest constructs the real DnsMirrorUrlResolver to show that a url outside the API prefix passes through untouched. If the prefix check regressed so that every url went to the lookup, InetAddress.getAllByName(all.api.radio-browser.info) would run inside a JVM test, and because replaceFirst leaves a non-API url unchanged the test would still pass while making a real DNS query. The JVM suite has no offline guard like the instrumented OfflineTestRunner, so nothing else would notice. Found by the criterion 7 re-audit on TASK-068; left out of it because the fix needs a production seam for the lookup and the gate run could not change production after the Android Auto check of 8ab2329.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 No JVM test can reach a name server through DnsMirrorUrlResolver, whatever its prefix check does
- [ ] #2 A regressed prefix check fails ConnectionUrlResolverTest instead of passing it
<!-- AC:END -->
