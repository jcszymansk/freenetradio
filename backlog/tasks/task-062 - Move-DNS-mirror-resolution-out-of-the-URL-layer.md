---
id: TASK-062
title: Move DNS mirror resolution out of the URL layer
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 18:27'
updated_date: '2026-09-21 18:47'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 77000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
UrlLayer mixes two unrelated jobs. Six of its seven methods build request Uris from strings and constants, with no platform service behind them. The seventh, getConnectionUrl, is the only code in the application that resolves DNS: UrlLayerRadioBrowserImpl looks up all.api.radio-browser.info, caches the mirrors it finds, picks one at random and falls back to three hardcoded hosts. The two live in one interface, so the pure half inherits the impure half. That has two costs. No test may call getConnectionUrl, so no test constructs the class honestly and both implementations read 0% line coverage in the JVM report. And the pure-core gate of TASK-007 cannot admit a class that reaches a resolver, so the Uri builders stay outside a gate they plainly belong in. HTTPDownloaderImpl is the only production caller and it uses the UrlLayer for nothing else, so it should take the resolver directly.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 DNS mirror lookup and fallback live in their own type, separate from UrlLayer
- [x] #2 UrlLayer builds Uris only and reaches no resolver, no network and no platform service
- [x] #3 HTTPDownloaderImpl takes the resolver it actually uses instead of the whole URL layer
- [x] #4 The Radio Browser base url prefix has one definition shared by the builder and the resolver
- [x] #5 Both sources are wired to their matching resolver and existing behaviour is unchanged
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a ConnectionUrlResolver interface holding the single resolve method, so the one seam that may reach a name server has a type of its own.
2. Split the two existing implementations of getConnectionUrl into DnsMirrorUrlResolver (Radio Browser mirror discovery) and DirectUrlResolver (parse the address as given), keeping behaviour identical.
3. Drop getConnectionUrl from UrlLayer and both UrlLayer implementations, leaving them as Uri builders.
4. Keep BASE_URL_PREFIX in UrlLayerRadioBrowserImpl, where the address is stamped, and have the resolver read it there, so the impure class depends on the pure one and not the other way round.
5. Point HTTPDownloaderImpl at the resolver, which is the only thing it ever used the UrlLayer for, and select the resolver alongside the parser and URL layer in DependencyRegistryCommon.
6. Cover both resolvers on the JVM as far as no name server is involved: the direct resolver fully, the mirror resolver on its pass-through branch, which is what keeps artwork and stream probes from being redirected at a mirror.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ConnectionUrlResolver is the new one-method interface, with DnsMirrorUrlResolver carrying the Radio Browser lookup, cache, random selection and hardcoded fallbacks, and DirectUrlResolver parsing the address as given. Both moved without a behaviour change.

HTTPDownloaderImpl turned out never to use the UrlLayer for anything but getConnectionUrl, so it takes the resolver rather than the layer, and DependencyRegistryCommon selects it in getUrlResolver alongside getUrlLayer and getParserLayer.

BASE_URL_PREFIX stays in UrlLayerRadioBrowserImpl, where addresses are stamped with it, and DnsMirrorUrlResolver reads it there. That leaves one definition and points the dependency from the impure class at the pure one, not the reverse.

The pass-through branch of the mirror resolver is covered deliberately: an address without the prefix must come back untouched, because station artwork and stream probes go through the same downloader and must not be redirected at a Radio Browser mirror. No test may pass a prefixed address, since that is the one input that reaches the lookup, and the test class KDoc says so.

Two changes worth recording. While rewriting the Uri builder I added Uri.encode to the Radio Browser getStationsByCountry, which the original did not have, and reverted it: country codes are two-letter ISO values so it changes nothing, and a behaviour change hidden in a move commit is the habit that left six defects without a regression test. And the local helper getConnectionUrl inside BytesDownloader is now openConnection, because it returns a Response and opens a connection, and was named only after the interface method that just moved.

Verified: ./gradlew test 196 tests, 0 failures, 0 skipped (was 191, plus the 5 new). ./gradlew assembleDebug and :app:assembleDebugAndroidTest both succeed. Offline instrumented suite on the API 34 emulator with wifi and data off and app data cleared: 207 tests, 0 failures, 0 errors, 0 skipped, all 28 journeys among them.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
UrlLayer held two unrelated jobs: six methods that build request Uris from constants, and getConnectionUrl, the only code in the application that resolves DNS. They are now separate. ConnectionUrlResolver has the single resolve method, implemented by DnsMirrorUrlResolver for Radio Browser mirror discovery and DirectUrlResolver for everything else, and HTTPDownloaderImpl takes the resolver it actually uses instead of the whole URL layer.

Both UrlLayer implementations are left as pure Uri builders that reach no resolver, no network and no platform service, which is what lets them into the pure-core set the TASK-007 gate measures, and what lets TASK-061 move their test off a device. Five JVM tests cover the direct resolver and the mirror resolver pass-through branch; the DNS path stays untested by design and the test class says why.

Verified with the full JVM suite (196 tests, 0 failures), debug and instrumentation assembly, and the offline instrumented suite on API 34 (207 tests, 0 failures, 28 journeys).
<!-- SECTION:FINAL_SUMMARY:END -->
