---
id: TASK-060
title: Cover RadioStationValidatorImpl rules directly
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 17:46'
updated_date: '2026-09-23 04:15'
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
- [x] #1 An empty home page is not probed and produces no warning
- [x] #2 A non-empty but unreachable home page produces a warning and not a failure
- [x] #3 An empty or invalid stream url is reported as a failure before any probe runs
- [x] #4 The tests run on the JVM and reach no network
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Inject the url probe into RadioStationValidatorImpl as a ResourceProbe fun interface; production wires NetUtils::checkResource in DependencyRegistryCommon.
2. Add RadioStationValidatorImplTest on the JVM with a recording probe and queued dispatchers, asserting the full ordered event sequence per rule.
3. Mutation-check the guards, then add the class to gradle/pure-core-coverage.tsv once verifyPureCoreAttribution confirms the owner.

4. Per review: before probing, reject a stream url the probe could never open (java.net.URL parse, http/https, connectable host per OkHttp's rule, port 1..65535), and pin that RFC-lenient but openable urls still reach the probe.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC #3: the validator now rejects before probing any stream url the production probe could not open at all: one java.net.URL cannot parse, whose protocol is not http or https, or that has no host. NetUtils.checkResource parses with the same URL class and opens an HttpURLConnection, so the set of accepted candidates is unchanged; such a url used to fail as 'stream is invalid' after a network attempt and now fails as 'url is invalid' without one. The journeys type loopback http urls and are unaffected.
With the probe injected the validator passes rules 2 to 5 of the pure-core membership rule, so it joined gradle/pure-core-coverage.tsv with RadioStationValidatorImplTest as owner, confirmed by verifyPureCoreAttribution.

The pre-probe check also rejects a port outside 1..65535 (java.net.URL parses up to 99999 and 0; neither can be connected to). It stays syntactic: anything that needs a resolver or a connection to decide is left to the probe.

Deliberately not a strict RFC 3986 parse (URL.toURI): a JDK probe sent http://127.0.0.1:<port>/my stream.mp3 and got 200 although toURI rejects it, so strict parsing would turn away stations the probe accepts. aStreamUrlTheProbeCouldOpenIsProbed pins that.

The pre-probe check also rejects a host with a space, a control character or one of #%/:?@[\], which is the rule OkHttp applies before connecting; underscores and non-ASCII hosts still pass. The url helpers are members of the class, not of its companion, so the pure-core gate measures them.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RadioStationValidatorImpl takes its url probe as a ResourceProbe; the registry wires NetUtils::checkResource. RadioStationValidatorImplTest (13 JVM cases, recording probe, queued dispatchers) covers the whole flow: an empty home page is not probed and does not warn, an unreachable home page warns before success and never fails, and an unreachable stream fails without the home page being probed. Invalid candidates fail before anything is queued for probing: no name, an empty url, or a url the probe could never open (bad syntax, a scheme other than http(s), a bad host, or a port outside 1..65535). The answers arrive on the UI scope. Adding strict RFC 3986 checks was considered and rejected, because a JDK probe got 200 for a path containing a space. The class joined the pure-core set with this test as owner. Verified: ./gradlew test, verifyPureCoreCoverage (96.4% line, 89.5% branch), verifyPureCoreAttribution, :app:assembleDebugAndroidTest, and mutation checks on every guard. The control-character branch alone cannot be reached on the JVM, as the notes explain. Codex review loop passed in round 5.
<!-- SECTION:FINAL_SUMMARY:END -->
