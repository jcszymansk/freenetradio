/*
 * Copyright 2026 The "FreeNetRadio" Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yuriy.openradio.shared.presenter.main

import com.yuriy.openradio.shared.model.media.MediaId
import com.yuriy.openradio.shared.model.source.Source
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.utils.AppUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenModelTest {

    // --- tabs and stacks ---------------------------------------------------------------------

    @Test
    fun aConnectedModelShowsFavLoadingAndSubscribesIt() {
        val m = ModelUnderTest().startAndConnect()

        assertEquals(Tab.FAV, m.state.selectedTab)
        assertEquals(ListContent.Loading, m.state.content)
        assertEquals(FAV, m.client.lastSubscription().parentId)
        assertNull(m.state.title)
        assertFalse(m.state.canGoBack)
    }

    @Test
    fun radioBrowserOffersSixTabsAndWebRadioFour() {
        assertEquals(Tab.entries.toList(), ModelUnderTest(Source.RADIO_BROWSER).startAndConnect().state.tabs)
        assertEquals(
            listOf(Tab.FAV, Tab.MINE, Tab.COUNTRY, Tab.GENRE),
            ModelUnderTest(Source.WEB_RADIO).startAndConnect().state.tabs
        )
    }

    @Test
    fun aHiddenTabCannotBeSelected() {
        val m = ModelUnderTest(Source.WEB_RADIO).startAndConnect()

        m.model.selectTab(Tab.POPULAR)

        assertEquals(Tab.FAV, m.state.selectedTab)
    }

    @Test
    fun favAndMineAreTabsEvenWhenTheRootOmitsThem() {
        val m = ModelUnderTest().startAndConnect()
        m.client.answerFetch(MediaId.MEDIA_ID_ROOT, listOf(category(MediaId.MEDIA_ID_COUNTRIES_LIST)))

        assertTrue(Tab.FAV in m.state.tabs)
        assertTrue(Tab.MINE in m.state.tabs)
    }

    @Test
    fun selectingATabSubscribesItsRoot() {
        val m = ModelUnderTest().startAndConnect()

        m.model.selectTab(Tab.GENRE)

        assertEquals(Tab.GENRE, m.state.selectedTab)
        assertEquals(GENRES, m.client.lastSubscription().parentId)
    }

    @Test
    fun openingACountryPushesItWithItsNameAndABackArrow() {
        val m = countriesShown()

        m.model.open(POLAND)

        assertEquals(POLAND, m.client.lastSubscription().parentId)
        assertEquals("Poland", m.state.title)
        assertTrue(m.state.canGoBack)
        assertEquals(Tab.COUNTRY, m.state.selectedTab)
    }

    @Test
    fun backPopsOneLevelAndAsksForTheParentAgain() {
        val m = countriesShown()
        m.model.open(POLAND)

        assertTrue(m.model.back())

        assertEquals(COUNTRIES, m.client.lastSubscription().parentId)
        assertNull(m.state.title)
        assertFalse(m.state.canGoBack)
        assertEquals("The level keeps its rows while they are asked for again", listOf(POLAND), m.rowIds())
        assertTrue(m.state.isRefreshing)
    }

    @Test
    fun backAtATabsRootLeavesTheStackAlone() {
        val m = ModelUnderTest().startAndConnect()

        assertFalse(m.model.back())
        assertEquals(listOf(FAV), m.model.snapshot().stacks.getValue(Tab.FAV))
    }

    @Test
    fun reselectingTheTabPopsToItsRoot() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.GENRE)
        m.client.answer(GENRES, listOf(category(JAZZ, "Jazz")))
        m.model.open(JAZZ)

        m.model.selectTab(Tab.GENRE)

        assertEquals(listOf(GENRES), m.model.snapshot().stacks.getValue(Tab.GENRE))
        assertEquals(GENRES, m.client.lastSubscription().parentId)
    }

    @Test
    fun eachTabKeepsItsOwnStackAndPosition() {
        val m = countriesShown()
        m.model.open(POLAND)
        m.client.answer(POLAND, listOf(station("pl-1"), station("pl-2")))
        m.model.rememberScroll(m.state.levelKey, ScrollPosition(1, 7))

        m.model.selectTab(Tab.FAV)
        m.model.selectTab(Tab.COUNTRY)

        assertEquals("Poland", m.state.title)
        assertEquals(listOf("pl-1", "pl-2"), m.rowIds())
        assertEquals(ScrollPosition(1, 7), m.state.scroll)
        assertEquals(POLAND, m.client.lastSubscription().parentId)
    }

    @Test
    fun aLevelComesBackAtTheScrollPositionItWasLeftAt() {
        val m = countriesShown()
        m.model.rememberScroll(m.state.levelKey, ScrollPosition(12, 3))
        m.model.open(POLAND)

        m.model.back()

        assertEquals(ScrollPosition(12, 3), m.state.scroll)
    }

    @Test
    fun offlineARowTapDoesNothing() {
        val m = countriesShown()
        m.online = false
        val asked = m.client.subscriptions.size

        m.model.open(POLAND)

        assertEquals(asked, m.client.subscriptions.size)
        assertFalse(m.state.canGoBack)
    }

    @Test
    fun offlineTheTabsStillSwitch() {
        val m = ModelUnderTest().startAndConnect()
        m.online = false

        m.model.selectTab(Tab.MINE)

        assertEquals(MINE, m.client.lastSubscription().parentId)
    }

    @Test
    fun tappingAStationPlaysItFromTheShownNode() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.MINE)
        m.client.answer(MINE, listOf(station("s1")))

        m.model.open("s1")

        assertEquals(listOf("s1" to MINE), m.client.plays)
    }

    // --- shown-node filtering ------------------------------------------------------------------

    @Test
    fun aRefreshOfTheNodeLeftByGoingBackDoesNotReplaceTheParent() {
        val m = countriesShown()
        m.model.open(POLAND)
        m.client.answer(POLAND, listOf(station("pl-1")))

        m.model.back()
        m.client.answer(COUNTRIES, listOf(category(POLAND, "Poland"), category(AUSTRIA, "Austria")))
        m.client.answer(POLAND, emptyList())

        assertEquals(listOf(POLAND, AUSTRIA), m.rowIds())
    }

    @Test
    fun aRefreshThatArrivesBeforeTheParentDoesNotOutliveIt() {
        val m = countriesShown()
        m.model.open(POLAND)

        m.model.back()
        m.client.answer(POLAND, listOf(station("pl-1")))
        m.client.answer(COUNTRIES, listOf(category(AUSTRIA, "Austria")))

        assertEquals(listOf(AUSTRIA), m.rowIds())
    }

    @Test
    fun anAnswerForAnotherTabIsDropped() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.MINE)

        m.client.answer(FAV, listOf(station("fav-1")))

        assertEquals(ListContent.Loading, m.state.content)
        m.model.selectTab(Tab.FAV)
        assertEquals("FAV never accepted the answer that came while MINE was shown", ListContent.Loading, m.state.content)
    }

    @Test
    fun aLatePageOfTheNodeLeftIsNotAppendedToTheShownList() {
        val m = countriesShown()
        m.model.open(POLAND)
        m.client.answer(POLAND, listOf(station("a")))
        m.model.loadMore()

        m.model.back()
        m.client.answer(COUNTRIES, listOf(category(POLAND, "Poland")))
        m.client.answer(POLAND, listOf(station("b")), replace = false)

        assertEquals(listOf(POLAND), m.rowIds())
    }

    @Test
    fun anErrorCountsOnlyForTheShownNode() {
        val m = countriesShown()
        m.model.open(POLAND)
        m.model.back()

        m.client.fail(POLAND)

        assertEquals(listOf(POLAND), m.rowIds())
        m.model.selectTab(Tab.GENRE)
        m.client.fail(GENRES)
        assertEquals(ListContent.Error, m.state.content)
    }

    @Test
    fun answersAfterStopAreDropped() {
        val m = ModelUnderTest().startAndConnect()
        val client = m.client

        m.model.stop()
        client.answer(FAV, listOf(station("s1")))

        assertTrue(client.cleaned)
        assertEquals(ListContent.Loading, m.state.content)
    }

    @Test
    fun aRestartKeepsTheStacksAndAsksForTheShownNodeAgain() {
        val m = polandShown(listOf(station("a")))
        m.model.loadMore()
        val before = m.model.snapshot()
        val first = m.client

        m.model.stop()
        assertFalse("A page asked of the old client is not waited for", m.state.isLoadingPage)
        assertFalse(m.state.isRefreshing)
        m.startAndConnect()

        assertTrue(first !== m.client)
        assertEquals(before, m.model.snapshot())
        assertEquals("Poland", m.state.title)
        assertEquals(FakeLibraryClient.Request(POLAND, 0, NO_QUERY), m.client.lastSubscription())
        assertEquals(listOf("a"), m.rowIds())
        assertTrue(m.state.isRefreshing)
        m.client.answer(POLAND, listOf(station("a")))
        m.model.loadMore()
        assertEquals(FakeLibraryClient.Request(POLAND, 1, NO_QUERY), m.client.lastSubscription())
    }

    @Test
    fun aRestartUnderAProviderWithoutTheSelectedTabFallsBackToFav() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.POPULAR)

        m.model.stop()
        m.sources.source = Source.WEB_RADIO
        m.startAndConnect()

        assertEquals(Tab.FAV, m.state.selectedTab)
        assertEquals(listOf(Tab.FAV, Tab.MINE, Tab.COUNTRY, Tab.GENRE), m.state.tabs)
        assertEquals(FAV, m.client.lastSubscription().parentId)
    }

    // --- replace and append --------------------------------------------------------------------

    @Test
    fun aRefreshOfTheShownNodeReplacesItsRows() {
        val m = polandShown(listOf(station("a"), station("b")))

        m.client.answer(POLAND, listOf(station("c")))

        assertEquals(listOf("c"), m.rowIds())
    }

    @Test
    fun aRequestedPageIsAppended() {
        val m = polandShown(listOf(station("a"), station("b")))
        m.model.loadMore()

        m.client.answer(POLAND, listOf(station("c")), replace = false)

        assertEquals(listOf("a", "b", "c"), m.rowIds())
        assertFalse(m.state.isLoadingPage)
    }

    @Test
    fun aPageNobodyAskedForIsDropped() {
        val m = polandShown(listOf(station("a")))

        m.client.answer(POLAND, listOf(station("b")), replace = false)

        assertEquals(listOf("a"), m.rowIds())
    }

    @Test
    fun anEmptyPageEndsPagingAndKeepsTheRows() {
        val m = polandShown(listOf(station("a"), station("b")))
        m.model.loadMore()
        m.client.answer(POLAND, emptyList(), replace = false)
        val asked = m.client.subscriptions.size

        m.model.loadMore()

        assertEquals(listOf("a", "b"), m.rowIds())
        assertEquals("No page is asked for past the end", asked, m.client.subscriptions.size)
    }

    @Test
    fun anEmptyFirstAnswerShowsTheTabsEmptyLine() {
        val m = ModelUnderTest().startAndConnect()
        m.client.answer(FAV, emptyList())
        assertEquals(ListContent.Empty(EmptyKind.NO_FAVORITES), m.state.content)

        m.model.selectTab(Tab.MINE)
        m.client.answer(MINE, emptyList())
        assertEquals(ListContent.Empty(EmptyKind.NO_STATIONS), m.state.content)

        m.model.selectTab(Tab.GENRE)
        m.client.answer(GENRES, emptyList())
        assertEquals(ListContent.Empty(EmptyKind.NOTHING_FOUND), m.state.content)
    }

    // --- paging --------------------------------------------------------------------------------

    @Test
    fun loadMoreAsksForTheNextPageOfARefreshableNode() {
        val m = polandShown(listOf(station("a")))

        m.model.loadMore()

        assertEquals(FakeLibraryClient.Request(POLAND, 1, NO_QUERY), m.client.lastSubscription())
        assertTrue(m.state.isLoadingPage)
    }

    @Test
    fun noSecondPageIsAskedForWhileOneIsInFlight() {
        val m = polandShown(listOf(station("a")))
        m.model.loadMore()
        val asked = m.client.subscriptions.size

        m.model.loadMore()

        assertEquals(asked, m.client.subscriptions.size)
    }

    @Test
    fun anAppendedPageMovesLoadMoreToTheFollowingPage() {
        val m = polandShown(listOf(station("a")))
        m.model.loadMore()
        m.client.answer(POLAND, listOf(station("b")), replace = false)

        m.model.loadMore()

        assertEquals(FakeLibraryClient.Request(POLAND, 2, NO_QUERY), m.client.lastSubscription())
    }

    @Test
    fun aFailedPageIsAskedForAgainByTheNextLoadMore() {
        val m = polandShown(listOf(station("a")))
        m.model.loadMore()
        m.client.fail(POLAND)
        assertFalse(m.state.isLoadingPage)

        m.model.loadMore()

        assertEquals(FakeLibraryClient.Request(POLAND, 1, NO_QUERY), m.client.lastSubscription())
        m.client.answer(POLAND, listOf(station("b")), replace = false)
        assertEquals(listOf("a", "b"), m.rowIds())
    }

    @Test
    fun aNodeThatDoesNotPageIsNotAskedForMore() {
        val m = ModelUnderTest().startAndConnect()
        m.client.answer(FAV, listOf(station("a")))
        val asked = m.client.subscriptions.size

        m.model.loadMore()

        assertEquals(asked, m.client.subscriptions.size)
    }

    // --- error and reload ----------------------------------------------------------------------

    @Test
    fun aFailedFirstLoadIsAnErrorAndReloadAsksAgain() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.POPULAR)
        m.client.fail(POPULAR)
        assertEquals(ListContent.Error, m.state.content)

        m.model.reload()

        assertEquals(ListContent.Loading, m.state.content)
        assertEquals(POPULAR, m.client.lastSubscription().parentId)
        m.client.answer(POPULAR, listOf(station("p1")))
        assertEquals(listOf("p1"), m.rowIds())
    }

    @Test
    fun aFailedRefreshKeepsTheRows() {
        val m = polandShown(listOf(station("a")))
        m.model.reload()

        m.client.fail(POLAND)

        assertEquals(listOf("a"), m.rowIds())
        assertFalse(m.state.isRefreshing)
    }

    // --- search --------------------------------------------------------------------------------

    @Test
    fun searchPushesOntoTheSelectedTabWithTheQueryAsTitle() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.GENRE)

        m.model.search("rock")

        assertEquals(FakeLibraryClient.Request(MediaId.MEDIA_ID_SEARCH_FROM_APP, 0, "rock"), m.client.lastSubscription())
        assertEquals("rock", m.state.title)
        assertTrue(m.state.canGoBack)
        assertEquals(listOf(GENRES, MediaId.MEDIA_ID_SEARCH_FROM_APP), m.model.snapshot().stacks.getValue(Tab.GENRE))
    }

    @Test
    fun backFromSearchReturnsWhereTheSearchStarted() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.GENRE)
        m.model.search("rock")

        m.model.back()

        assertEquals(GENRES, m.client.lastSubscription().parentId)
        assertNull(m.state.title)
    }

    @Test
    fun aSecondSearchReplacesTheFirst() {
        val m = ModelUnderTest().startAndConnect()
        m.model.search("rock")

        m.model.search("jazz")

        assertEquals(listOf(FAV, MediaId.MEDIA_ID_SEARCH_FROM_APP), m.model.snapshot().stacks.getValue(Tab.FAV))
        assertEquals("jazz", m.state.title)
    }

    @Test
    fun aSecondSearchAlsoDropsWhatWasOpenedFromTheFirst() {
        val m = ModelUnderTest().startAndConnect()
        m.model.search("rock")
        m.client.answer(MediaId.MEDIA_ID_SEARCH_FROM_APP, listOf(category(JAZZ, "Jazz")))
        m.model.open(JAZZ)

        m.model.search("blues")

        assertEquals(listOf(FAV, MediaId.MEDIA_ID_SEARCH_FROM_APP), m.model.snapshot().stacks.getValue(Tab.FAV))
        m.model.back()
        assertEquals(FAV, m.client.lastSubscription().parentId)
    }

    // --- COUNTRY and YOUR COUNTRY --------------------------------------------------------------

    @Test
    fun theUsersCountryComesFirstUnderYourCountry() {
        val m = countriesShown(rootHasCountry = true)

        assertEquals(
            listOf(
                "section:YOUR_COUNTRY",
                MediaId.MEDIA_ID_COUNTRY_STATIONS,
                "section:ALL_COUNTRIES",
                POLAND
            ),
            m.rowIds()
        )
    }

    @Test
    fun withoutTheRootsCountryItemTheCountriesStandAlone() {
        val m = countriesShown(rootHasCountry = false)

        assertEquals(listOf(POLAND), m.rowIds())
    }

    @Test
    fun anEmptyCountriesListStillShowsYourCountry() {
        val m = ModelUnderTest().startAndConnect()
        m.client.answerFetch(MediaId.MEDIA_ID_ROOT, listOf(category(MediaId.MEDIA_ID_COUNTRY_STATIONS, "Poland")))
        m.model.selectTab(Tab.COUNTRY)

        m.client.answer(COUNTRIES, emptyList())

        assertEquals(listOf("section:YOUR_COUNTRY", MediaId.MEDIA_ID_COUNTRY_STATIONS), m.rowIds())
    }

    @Test
    fun tappingYourCountryDrillsIntoTheCountrysStations() {
        val m = countriesShown(rootHasCountry = true)

        m.model.open(MediaId.MEDIA_ID_COUNTRY_STATIONS)

        assertEquals(MediaId.MEDIA_ID_COUNTRY_STATIONS, m.client.lastSubscription().parentId)
        assertEquals("Poland", m.state.title)
    }

    @Test
    fun yourCountryIsOnlyOnCountrysRoot() {
        val m = countriesShown(rootHasCountry = true)
        m.model.open(POLAND)

        m.client.answer(POLAND, listOf(station("pl-1")))

        assertEquals(listOf("pl-1"), m.rowIds())
    }

    @Test
    fun refreshRootReadsTheCountryItemAgain() {
        val m = countriesShown(rootHasCountry = true)

        m.model.refreshRoot()
        m.client.answerFetch(MediaId.MEDIA_ID_ROOT, emptyList())

        assertEquals(listOf(POLAND), m.rowIds())
    }

    // --- favourites, long-press, FAB -----------------------------------------------------------

    @Test
    fun theHeartMarksAStationAndTellsTheService() {
        val m = polandShown(listOf(station("a", favorite = false)))

        m.model.toggleFavorite("a")

        assertTrue((m.rows().single() as RowState.Station).isFavorite)
        assertEquals(OpenRadioService.CMD_MARK_FAVORITE, m.client.commands.last().first)
        assertEquals("The heart does not play", emptyList<Pair<String, String>>(), m.client.plays)
    }

    @Test
    fun theHeartUnmarksAMarkedStation() {
        val m = polandShown(listOf(station("a", favorite = true)))

        m.model.toggleFavorite("a")

        assertFalse((m.rows().single() as RowState.Station).isFavorite)
        assertEquals(OpenRadioService.CMD_UNMARK_FAVORITE, m.client.commands.last().first)
    }

    @Test
    fun aFreshAnswerWinsOverTheHeartsOwnFlip() {
        val m = polandShown(listOf(station("a", favorite = false)))
        m.model.toggleFavorite("a")

        m.client.answer(POLAND, listOf(station("a", favorite = false)))

        assertFalse((m.rows().single() as RowState.Station).isFavorite)
    }

    @Test
    fun longPressActsOnlyOnFavAndMine() {
        val m = ModelUnderTest().startAndConnect()
        m.model.selectTab(Tab.MINE)
        m.client.answer(MINE, listOf(station("s1"), station("s2")))

        val target = m.model.longPressTarget("s1")

        assertEquals("s1", target?.item?.mediaId)
        assertEquals(MINE, target?.parentId)
        assertEquals(2, target?.itemsCount)
        assertTrue((m.rows().first() as RowState.Station).canLongPress)

        m.model.selectTab(Tab.POPULAR)
        m.client.answer(POPULAR, listOf(station("p1")))
        assertNull(m.model.longPressTarget("p1"))
        assertFalse((m.rows().first() as RowState.Station).canLongPress)
    }

    @Test
    fun addIsOfferedOnMineOnly() {
        val m = ModelUnderTest().startAndConnect()
        assertFalse(m.state.showAddFab)

        m.model.selectTab(Tab.MINE)
        assertTrue(m.state.showAddFab)

        m.model.search("rock")
        assertFalse("The FAB follows the node on top, not the tab", m.state.showAddFab)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun countriesShown(rootHasCountry: Boolean = false): ModelUnderTest {
        val m = ModelUnderTest().startAndConnect()
        val root = if (rootHasCountry) listOf(category(MediaId.MEDIA_ID_COUNTRY_STATIONS, "Poland")) else emptyList()
        m.client.answerFetch(MediaId.MEDIA_ID_ROOT, root)
        m.model.selectTab(Tab.COUNTRY)
        m.client.answer(COUNTRIES, listOf(category(POLAND, "Poland")))
        return m
    }

    private fun polandShown(rows: List<androidx.media3.common.MediaItem>): ModelUnderTest {
        val m = countriesShown()
        m.model.open(POLAND)
        m.client.answer(POLAND, rows)
        return m
    }

    private companion object {
        const val FAV = MediaId.MEDIA_ID_FAVORITES_LIST
        const val MINE = MediaId.MEDIA_ID_LOCAL_RADIO_STATIONS_LIST
        const val POPULAR = MediaId.MEDIA_ID_POPULAR_STATIONS
        const val COUNTRIES = MediaId.MEDIA_ID_COUNTRIES_LIST
        const val GENRES = MediaId.MEDIA_ID_ALL_CATEGORIES
        const val POLAND = MediaId.MEDIA_ID_COUNTRIES_LIST + "PL"
        const val AUSTRIA = MediaId.MEDIA_ID_COUNTRIES_LIST + "AT"
        const val JAZZ = MediaId.MEDIA_ID_CHILD_CATEGORIES + "jazz"

        /** What a request carries when its bundle holds no search query. */
        const val NO_QUERY = AppUtils.USE_CUR_SEARCH_QUERY
    }
}
