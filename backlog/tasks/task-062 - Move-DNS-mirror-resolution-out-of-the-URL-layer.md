---
id: TASK-062
title: Move DNS mirror resolution out of the URL layer
status: To Do
assignee: []
created_date: '2026-09-21 18:27'
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
- [ ] #1 DNS mirror lookup and fallback live in their own type, separate from UrlLayer
- [ ] #2 UrlLayer builds Uris only and reaches no resolver, no network and no platform service
- [ ] #3 HTTPDownloaderImpl takes the resolver it actually uses instead of the whole URL layer
- [ ] #4 The Radio Browser base url prefix has one definition shared by the builder and the resolver
- [ ] #5 Both sources are wired to their matching resolver and existing behaviour is unchanged
<!-- AC:END -->
