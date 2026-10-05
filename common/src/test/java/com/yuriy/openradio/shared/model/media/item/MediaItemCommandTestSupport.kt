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

package com.yuriy.openradio.shared.model.media.item

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Bundle
import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.eq.EqualizerLayer
import com.yuriy.openradio.shared.model.media.Category
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.media.setVariant
import com.yuriy.openradio.shared.model.net.NetworkMonitorListener
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.model.timer.SleepTimerModel
import com.yuriy.openradio.shared.model.translation.MediaIdBuilder
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioServicePresenter
import com.yuriy.openradio.shared.service.location.Country
import com.yuriy.openradio.shared.utils.AppUtils
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.TreeSet
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

/**
 * Shared scaffolding for the [MediaItemCommand] tests: a context that answers resource lookups,
 * a recording presenter, a listener that owns the command's coroutine scope and can be awaited,
 * and station fixtures.
 */

internal const val DEFAULT_COUNTRY_CODE = "PL"

internal const val FLAG_DRAWABLE_ID = 4711

/**
 * What every string resource resolves to here. The generated test R class numbers all resources
 * zero, so one value is all a test context can tell apart.
 */
internal const val STRING_RESOURCE = "string resource"

@Suppress("DEPRECATION")
internal fun testContext(): Context {
    val resources = object : Resources(null, null, null) {

        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            return FLAG_DRAWABLE_ID
        }

        override fun getString(id: Int): String = STRING_RESOURCE

        override fun getString(id: Int, vararg formatArgs: Any?): String = STRING_RESOURCE

        override fun getText(id: Int): CharSequence = STRING_RESOURCE
    }
    return object : ContextWrapper(null) {

        override fun getResources(): Resources = resources

        override fun getPackageName(): String = "com.github.jcszymansk.freenetradio"
    }
}

internal fun station(
    id: String,
    name: String = "Station $id",
    country: String = "Poland",
    genre: String = "Jazz",
    bitrate: Int = 128,
    sortId: Int = 0,
    imageUrl: String = "https://radio.example/$id.png"
): RadioStation {
    return RadioStation.makeDefaultInstance(id).apply {
        this.name = name
        this.country = country
        this.genre = genre
        this.sortId = sortId
        this.imageUrl = imageUrl
        setVariant(bitrate, "https://radio.example/$id.mp3")
    }
}

internal fun stations(vararg ids: String): Set<RadioStation> {
    val result = TreeSet<RadioStation>()
    for ((index, id) in ids.withIndex()) {
        result.add(station(id, sortId = index))
    }
    return result
}

internal fun dependencies(
    presenter: OpenRadioServicePresenter,
    listener: RecordingCommandListener,
    parentId: String = AppUtils.EMPTY_STRING,
    countryCode: String = DEFAULT_COUNTRY_CODE,
    isSameCatalogue: Boolean = false,
    isSavedInstance: Boolean = false,
    options: Bundle = Bundle()
): MediaItemCommandDependencies {
    return MediaItemCommandDependencies(
        testContext(),
        presenter,
        countryCode,
        parentId,
        isSameCatalogue,
        isSavedInstance,
        options,
        listener.scope,
        listener
    )
}

/**
 * Records what a command delivered, and owns the scope the command launches its work in.
 *
 * Commands answer from a background coroutine, so every assertion is preceded by awaiting the
 * signal the command under test is expected to send and then [settle]-ing the scope. Settling is
 * what makes the counts exact: once every coroutine the command launched has finished, nothing is
 * left that could still deliver a second result, report an error or ask the presenter for more.
 */
internal class RecordingCommandListener : OpenRadioService.ResultListener {

    /**
     * Failures of the coroutines launched in [scope]. Without the handler they would reach the
     * thread's uncaught exception handler on an IO thread, where the test that caused them never
     * sees them.
     */
    private val mCoroutineFailures = ConcurrentLinkedQueue<Throwable>()

    private val mScopeJob = SupervisorJob()

    /**
     * The scope handed to the command through [dependencies]. A supervisor, so one failed
     * coroutine is reported rather than silently cancelling the others.
     */
    val scope = CoroutineScope(
        mScopeJob + Dispatchers.IO + CoroutineExceptionHandler { _, failure -> mCoroutineFailures.add(failure) }
    )

    private val mResultLatch = CountDownLatch(1)

    private val mErrorLatch = CountDownLatch(1)

    @Volatile
    var results = 0
        private set

    @Volatile
    var items = emptyList<MediaItem>()
        private set

    @Volatile
    var radioStations = emptySet<RadioStation>()
        private set

    @Volatile
    var pageNumber = UNSET_PAGE_NUMBER
        private set

    @Volatile
    var errors = 0
        private set

    @Volatile
    var error: String? = null
        private set

    /**
     * The thread the result was delivered on, or null while nothing has been delivered. Every
     * command that does its work launches it on [Dispatchers.IO], which always dispatches, so this
     * is the caller's own thread only when the command answered inline.
     */
    @Volatile
    var resultThread: Thread? = null
        private set

    val mediaIds: List<String>
        get() = items.map { it.mediaId }

    /**
     * An empty catalogue is reported with the "no data" string resource, which resolves to null off
     * a device. A Kotlin implementation of the listener would reject that null before recording it,
     * so the call is taken through a proxy instead.
     */
    val playbackStateListener: MediaItemCommand.IUpdatePlaybackState = Proxy.newProxyInstance(
        MediaItemCommand.IUpdatePlaybackState::class.java.classLoader,
        arrayOf(MediaItemCommand.IUpdatePlaybackState::class.java),
        InvocationHandler { _, method, arguments ->
            if (method.name == UPDATE_PLAYBACK_STATE) {
                error = arguments?.firstOrNull() as String?
                errors++
                mErrorLatch.countDown()
            }
            null
        }
    ) as MediaItemCommand.IUpdatePlaybackState

    override fun onResult(items: List<MediaItem>, radioStations: Set<RadioStation>, pageNumber: Int) {
        this.items = items
        this.radioStations = radioStations
        this.pageNumber = pageNumber
        resultThread = Thread.currentThread()
        results++
        mResultLatch.countDown()
    }

    /**
     * Waits for the first result, then [settle]s, so what the listener holds afterwards is final.
     *
     * @return This listener, for chaining.
     */
    fun awaitResult(): RecordingCommandListener {
        awaitSignal(mResultLatch, RESULT_MISSING)
        return this
    }

    /**
     * Waits for the first error report, then [settle]s, so what the listener holds afterwards is
     * final.
     *
     * @return This listener, for chaining.
     */
    fun awaitError(): RecordingCommandListener {
        awaitSignal(mErrorLatch, ERROR_MISSING)
        return this
    }

    /**
     * Asserts that no error was reported. The result and the error come from the same coroutine,
     * so once [settle] has joined it, a count of zero is the final word rather than a guess about
     * whether an error was still on its way.
     */
    fun assertNoError() {
        settle()
        assertEquals("Command reported an error it was not expected to", 0, errors)
    }

    /**
     * Waits until every coroutine launched in [scope] has finished, and fails the test with the
     * first failure any of them raised. A coroutine may launch another before it finishes, so the
     * children are read again until none is left running.
     *
     * A coroutine still running after [AWAIT_MILLIS] fails the test by name, and the scope is
     * cancelled first so that it does not outlive the test that started it.
     */
    fun settle() {
        val finished = runBlocking {
            withTimeoutOrNull(AWAIT_MILLIS) {
                while (true) {
                    val running = mScopeJob.children.filterNot { it.isCompleted }.toList()
                    if (running.isEmpty()) {
                        break
                    }
                    running.joinAll()
                }
            }
        }
        if (finished == null) {
            scope.cancel()
            throw withCoroutineFailures(AssertionError(COROUTINE_STILL_RUNNING))
        }
        if (mCoroutineFailures.isNotEmpty()) {
            throw withCoroutineFailures(AssertionError(COROUTINE_FAILED))
        }
    }

    private fun awaitSignal(latch: CountDownLatch, missing: String) {
        if (latch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS).not()) {
            scope.cancel()
            throw withCoroutineFailures(AssertionError(missing))
        }
        settle()
    }

    /**
     * Attaches the recorded coroutine failures to [failure], the first as its cause, so a signal
     * that never came because the coroutine meant to send it threw says why.
     */
    private fun withCoroutineFailures(failure: AssertionError): AssertionError {
        val recorded = mCoroutineFailures.toList()
        recorded.firstOrNull()?.let { failure.initCause(it) }
        recorded.drop(1).forEach { failure.addSuppressed(it) }
        return failure
    }

    /**
     * Names every media id that arrived, in order. The first one is a parameter of its own so that
     * `assertMediaIds()` does not compile: an empty expectation degenerates into comparing two
     * empty lists, which is bit for bit what a command that never ran also delivers. A test that
     * expects no items asserts what the command did instead - the page it asked the presenter for,
     * or the error it reported.
     */
    fun assertMediaIds(first: String, vararg rest: String) {
        assertEquals(listOf(first) + rest, mediaIds)
    }

    /**
     * Asserts the contract of a browse command restored onto a catalogue that is already in
     * [com.yuriy.openradio.shared.model.media.BrowseTree]: it answers inline, before `execute`
     * returns and before it reaches its coroutine, and the empty result it leaves behind is what
     * makes `OpenRadioService.callWhenSourceReady` serve the node from the tree rather than from
     * the provider.
     *
     * That the result was delivered inline is the whole claim. An empty result on its own is bit
     * for bit what a command that never ran delivers, and so is one a coroutine delivers later.
     *
     * The inline delivery is read off the thread, which cannot race: every command that does its
     * work launches it on [Dispatchers.IO], which never runs a block on the thread that launched
     * it, so the caller's own thread delivering the result is something only the branch that
     * returns before the launch can produce. Call this from the thread that called `execute`.
     *
     * The scope is [settle]d before anything is counted, so a command that answers inline and then
     * launches its fetch anyway has finished that fetch by the time the single result is asserted,
     * and the caller's presenter counters, read after this returns, are final too. The second
     * answer is checked for before the thread, because it overwrites the thread the first one
     * was delivered on.
     */
    fun assertAnsweredFromCacheBeforeReturning() {
        settle()
        assertTrue("A restored instance answered more than once", results <= 1)
        assertSame(
            "A restored instance did not answer on the thread that called execute, so it did not " +
                "answer before execute returned",
            Thread.currentThread(),
            resultThread
        )
        assertEquals("A restored instance built media items of its own", emptyList<String>(), mediaIds)
        assertEquals("A restored instance carried radio stations of its own", emptySet<RadioStation>(), radioStations)
        assertEquals("A restored instance did not answer for the first page", UrlLayer.FIRST_PAGE_INDEX, pageNumber)
    }

    companion object {

        const val UNSET_PAGE_NUMBER = -1

        /**
         * Shorter than [MediaItemCommand.CMD_TIMEOUT_MS] on purpose. A command that exhausts its
         * own timeout answers with no items, the first page and no error, which is exactly the
         * state several of these tests expect of a command that ran and found nothing. Expiring
         * first turns that into a named failure instead of a pass (TASK-069).
         */
        const val AWAIT_MILLIS = MediaItemCommand.CMD_TIMEOUT_MS / 2

        const val RESULT_MISSING = "Command did not deliver a result"

        private const val ERROR_MISSING = "Command did not report an error"

        const val COROUTINE_STILL_RUNNING = "A coroutine the command launched was still running after $AWAIT_MILLIS ms"

        const val COROUTINE_FAILED = "A coroutine the command launched failed"

        private const val UPDATE_PLAYBACK_STATE = "updatePlaybackState"
    }
}

/**
 * Canned answers plus a record of what the command asked for. Anything a browse command is not
 * meant to touch throws, and [RecordingCommandListener.settle] hands a throw from the command's
 * coroutine back to the test that made the call.
 */
internal class RecordingPresenter(
    private val mCategories: Set<Category> = emptySet(),
    private val mCountries: Set<Country> = emptySet(),
    private val mFavorites: Set<RadioStation> = emptySet(),
    private val mDeviceLocals: Set<RadioStation> = emptySet(),
    private val mNewStations: Set<RadioStation> = emptySet(),
    private val mPopularStations: Set<RadioStation> = emptySet(),
    private val mCategoryStations: Set<RadioStation> = emptySet(),
    private val mCountryStations: Set<RadioStation> = emptySet(),
    private val mSearchStations: Set<RadioStation> = emptySet(),
    private val mFavoriteIds: Set<String> = emptySet()
) : OpenRadioServicePresenter {

    val categoryRequests = mutableListOf<Pair<String, Int>>()

    val countryRequests = mutableListOf<Pair<String, Int>>()

    val searchRequests = mutableListOf<String>()

    val favoriteChecks = mutableListOf<String>()

    var categoriesRequests = 0
        private set

    var countriesRequests = 0
        private set

    var favoritesRequests = 0
        private set

    var deviceLocalsRequests = 0
        private set

    var newStationsRequests = 0
        private set

    var popularStationsRequests = 0
        private set

    override fun getStationsInCategory(categoryId: String, pageNumber: Int): Set<RadioStation> {
        categoryRequests.add(categoryId to pageNumber)
        return pageOf(mCategoryStations, pageNumber)
    }

    override fun getStationsByCountry(countryCode: String, pageNumber: Int): Set<RadioStation> {
        countryRequests.add(countryCode to pageNumber)
        return pageOf(mCountryStations, pageNumber)
    }

    override fun getNewStations(): Set<RadioStation> {
        newStationsRequests++
        return mNewStations
    }

    override fun getPopularStations(): Set<RadioStation> {
        popularStationsRequests++
        return mPopularStations
    }

    override fun getSearchStations(query: String, mediaIdBuilder: MediaIdBuilder): Set<RadioStation> {
        searchRequests.add(query)
        val result = TreeSet<RadioStation>()
        for (radioStation in mSearchStations) {
            val built = RadioStation.makeCopyInstance(radioStation)
            built.id = mediaIdBuilder.build(radioStation.id)
            result.add(built)
        }
        return result
    }

    override fun getAllCategories(): Set<Category> {
        categoriesRequests++
        return mCategories
    }

    override fun getAllCountries(): Set<Country> {
        countriesRequests++
        return mCountries
    }

    override fun getAllFavorites(): Set<RadioStation> {
        favoritesRequests++
        return mFavorites
    }

    override fun getAllDeviceLocal(): Set<RadioStation> {
        deviceLocalsRequests++
        return mDeviceLocals
    }

    override fun isRadioStationFavorite(radioStation: RadioStation): Boolean {
        favoriteChecks.add(radioStation.id)
        return mFavoriteIds.contains(radioStation.id)
    }

    private fun pageOf(all: Set<RadioStation>, pageNumber: Int): Set<RadioStation> {
        if (pageNumber >= all.size) {
            return emptySet()
        }
        return setOf(all.toList()[pageNumber])
    }

    override fun getMediaItemCommand(commandId: String) = unexpected()

    override fun startNetworkMonitor(context: Context, listener: NetworkMonitorListener) = unexpected()

    override fun stopNetworkMonitor(context: Context) = unexpected()

    override fun isPlaybackBlockedByMobileNetwork() = unexpected()

    override fun getLastRadioStation() = unexpected()

    override fun getEqualizerLayer(): EqualizerLayer = unexpected()

    override fun setLastRadioStation(radioStation: RadioStation) = unexpected()

    override fun getCountryCode() = unexpected()

    override fun updateRadioStationFavorite(radioStation: RadioStation) = unexpected()

    override fun updateRadioStationFavorite(radioStation: RadioStation, isFavorite: Boolean) = unexpected()

    override fun updateSortIds(mediaId: String, sortId: Int, categoryMediaId: String) = unexpected()

    override fun getSleepTimerModel(): SleepTimerModel = unexpected()

    override fun clear() = unexpected()

    override fun close() = unexpected()

    private fun unexpected(): Nothing {
        throw AssertionError("Browse command reached a presenter call it has no business making")
    }
}
