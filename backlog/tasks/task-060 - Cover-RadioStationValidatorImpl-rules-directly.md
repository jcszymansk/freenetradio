---
id: TASK-060
title: Cover RadioStationValidatorImpl rules directly
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-21 17:46'
updated_date: '2026-09-22 19:50'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 75000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RadioStationValidatorImpl has no test anywhere. Every test that touches validation either injects a RadioStationValidator fake (RecordingValidator in RadioStationManagerLayerImplTest) or drives the whole journey against the real one without asserting its rules. 13cc159 added the guard that stops an empty home page from being probed; reverted, every station added without a home page fires onWarning, which addRadioStation wires to its own onFailure, and LocalStationLifecycleJourneyTest would not see it because it asserts only that the station was stored. The guard is reached only after the stream url has been probed over HTTP, so a JVM test needs the probe replaced before it can reach the guard without a network. TASK-038 changes how a warning reaches the user at all and wants this coverage in place first. Found while auditing criterion 6 of TASK-007.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An empty home page is not probed and produces no warning
- [ ] #2 A non-empty but unreachable home page produces a warning and not a failure
- [ ] #3 An empty or invalid stream url is reported as a failure before any probe runs
- [ ] #4 The tests run on the JVM and reach no network
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Inject the url probe into RadioStationValidatorImpl as a ResourceProbe fun interface; production wires NetUtils::checkResource in DependencyRegistryCommon.
2. Add RadioStationValidatorImplTest on the JVM with a recording probe and queued dispatchers, asserting the full ordered event sequence per rule.
3. Mutation-check the guards, then add the class to gradle/pure-core-coverage.tsv once verifyPureCoreAttribution confirms the owner.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC #3: the validator now rejects before probing any stream url the production probe could not open at all: one java.net.URL cannot parse, whose protocol is not http or https, or that has no host. NetUtils.checkResource parses with the same URL class and opens an HttpURLConnection, so the set of accepted candidates is unchanged; such a url used to fail as 'stream is invalid' after a network attempt and now fails as 'url is invalid' without one. The journeys type loopback http urls and are unaffected.
With the probe injected the validator passes rules 2 to 5 of the pure-core membership rule, so it joined gradle/pure-core-coverage.tsv with RadioStationValidatorImplTest as owner, confirmed by verifyPureCoreAttribution.

The pre-probe check also rejects a port outside 1..65535 (java.net.URL parses up to 99999 and 0; neither can be connected to). It stays syntactic: anything that needs a resolver or a connection to decide is left to the probe.
<!-- SECTION:NOTES:END -->
