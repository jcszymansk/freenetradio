package com.yuriy.openradio.shared.model.media.item

import android.content.ContextWrapper
import android.os.Bundle
import androidx.media3.common.MediaItem
import com.yuriy.openradio.shared.model.media.RadioStation
import com.yuriy.openradio.shared.model.net.UrlLayer
import com.yuriy.openradio.shared.service.OpenRadioService
import com.yuriy.openradio.shared.service.OpenRadioServicePresenter
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class IndexableMediaItemCommandTest {

    @Test
    fun pageAdvancesAndResetsForNewCatalogue() {
        val command = object : IndexableMediaItemCommand() {}

        assertEquals(UrlLayer.FIRST_PAGE_INDEX, command.pageNumber)
        assertFalse(command.doLoadNoDataReceived())

        command.nextPageNumber()

        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, command.pageNumber)
        assertTrue(command.doLoadNoDataReceived())
        command.execute(MediaItemCommand.IUpdatePlaybackState { }, dependencies(true))
        assertEquals(UrlLayer.FIRST_PAGE_INDEX + 1, command.pageNumber)

        command.execute(MediaItemCommand.IUpdatePlaybackState { }, dependencies(false))

        assertEquals(UrlLayer.FIRST_PAGE_INDEX, command.pageNumber)
        assertFalse(command.doLoadNoDataReceived())
    }

    private fun dependencies(isSameCatalogue: Boolean): MediaItemCommandDependencies {
        return MediaItemCommandDependencies(
            ContextWrapper(null),
            presenterProxy(),
            "",
            "parent",
            isSameCatalogue,
            false,
            Bundle(),
            CoroutineScope(EmptyCoroutineContext),
            object : OpenRadioService.ResultListener {
                override fun onResult(
                    items: List<MediaItem>,
                    radioStations: Set<RadioStation>,
                    pageNumber: Int
                ) = Unit
            }
        )
    }

    private fun presenterProxy(): OpenRadioServicePresenter {
        val handler = InvocationHandler { _, _, _ -> null }
        return Proxy.newProxyInstance(
            OpenRadioServicePresenter::class.java.classLoader,
            arrayOf(OpenRadioServicePresenter::class.java),
            handler
        ) as OpenRadioServicePresenter
    }
}
