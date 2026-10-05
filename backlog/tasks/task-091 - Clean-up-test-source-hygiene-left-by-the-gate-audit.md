---
id: TASK-091
title: Clean up test-source hygiene left by the gate audit
status: To Do
assignee: []
created_date: '2026-10-05 05:13'
labels: []
milestone: m-1
dependencies: []
priority: low
type: chore
ordinal: 105000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The TASK-068 re-audit of every test file found nothing that hides a failure in these items, so they do not block the gate, but each one makes the suite harder to read or trust. Twenty-one test files carry no Apache 2.0 header: in :common FilterImplTest, BrowseTreeTest, IndexableMediaItemCommandTest, MediaIdTest, RadioStationTest, ModelLayerImplTest, ParserLayerMappingTest, InMemoryApiCacheTest, RadioStationsStorageTest, AppUtilsTest and MediaTypeTest; in :common-ui ServiceCommanderTest; in :app AbstractStorageValuesTest, DeviceLocalsStorageTest, FavoritesStorageTest, FileStoreManagerTest, LatestRadioStationStorageTest, SettingsStorageTest, StorageTestStations, PersistentApiCacheTest and AddStationDialogTest. Which copyright line each one gets depends on whether it came from upstream, so it is a judgement per file, not a sweep. Two test classes are named after classes that do not exist: RadioStationsStorageTest exercises RadioStationJsonDeserializer and a TreeSet, and MediaIDHelperTest tests MediaId and duplicates MediaIdTest. FilterImplTest numbers its tests (testFilter1 to 10 with gaps) and AutoDetectParserTest.testFileExtension says nothing about the claim. IndexableMediaItemCommandTest builds its presenter as a dynamic Proxy that answers null to everything, against the recording-fake convention, and constructs a scope it never uses. Helpers are duplicated: localStation in StorageManagerLayerImplTest and SortUtilsTest; assertRadioBrowserIsBound four times (OpenRadioServiceBrowseTest, OpenRadioServiceSearchTest, MediaResourcesManagerTest, JourneyProfile); awaitFavorites in FavoriteLifecycleJourneyTest and OfflinePlaybackJourneyTest; awaitSeededRoot three times; the live stream description and label three times. AddStationDialogTest and MediaResourcesManagerTest fields lack the m prefix.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Every test source carries the Apache 2.0 header with the copyright line its history calls for
- [ ] #2 No test class is named after a class that does not exist, and duplicated tests are merged or removed
- [ ] #3 Each duplicated helper has one home that its users share
- [ ] #4 Test names state the claim they check
<!-- AC:END -->
