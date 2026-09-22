---
id: TASK-074
title: Encode the country code Radio Browser puts in the request path
status: To Do
assignee: []
created_date: '2026-09-22 06:55'
labels: []
milestone: m-0
dependencies: []
references:
  - >-
    common/src/main/java/com/yuriy/openradio/shared/model/net/UrlLayerRadioBrowserImpl.kt
  - >-
    common/src/main/java/com/yuriy/openradio/shared/model/net/UrlLayerWebRadioImpl.kt
  - common/src/main/java/com/yuriy/openradio/shared/model/media/MediaId.kt
type: bug
ordinal: 88000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
UrlLayerRadioBrowserImpl.getStationsByCountry splices the country code straight into the bycountrycodeexact path segment. It is the only method in that class that skips encodeValue: getStationsInCategory and getSearchUrl both encode, and UrlLayerWebRadioImpl encodes the same argument since 88d4955.

Nothing upstream guarantees two safe letters. OpenRadioService reads the code from MediaId.getCountryCode, which takes the last two characters of whatever parentId it was handed and uppercases them, and the parentId comes from a MediaBrowser client. A code carrying a question mark turns the rest of the address into a query string, so the offset and limit arguments are lost and Radio Browser answers for a country named after the first character; a hash truncates the address at the fragment. Neither fails loudly, both return the wrong stations.

Found while moving the URL layer test to the JVM in TASK-061. That test pins the current unencoded output in radioBrowserLeavesTheCountryCodeUnencoded and names this task, so the assertion has to be inverted here rather than deleted.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 getStationsByCountry encodes the country code, so a code carrying a URL delimiter cannot change the shape of the request
- [ ] #2 The JVM UrlLayerTest asserts the encoded output and fails if the encoding is removed
<!-- AC:END -->
