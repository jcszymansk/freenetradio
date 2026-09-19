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

package com.yuriy.openradio.shared.broadcast

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers which broadcasts [BecomingNoisyReceiver] turns into a pause request and which it lets
 * pass.
 *
 * The broadcast is handed to [BecomingNoisyReceiver.onReceive] directly instead of being sent:
 * [AudioManager.ACTION_AUDIO_BECOMING_NOISY] is a protected broadcast, so only the system may send
 * it and an application that tries gets a [SecurityException]. The filter is therefore asserted on
 * its own and the dispatch is simulated.
 *
 * The test is instrumented even though it needs no device state, because [android.content.Intent]
 * and [android.content.IntentFilter] are not among the types implemented in `:android-jvm-stubs`.
 * On the JVM they would answer with the defaults of `unitTests.returnDefaultValues`, and a filter
 * that matches nothing and an action that reads back as null prove nothing.
 */
@RunWith(AndroidJUnit4::class)
class BecomingNoisyReceiverTest {

    private lateinit var mContext: Context

    private lateinit var mListener: RecordingListener

    private lateinit var mReceiver: BecomingNoisyReceiver

    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mListener = RecordingListener()
        mReceiver = BecomingNoisyReceiver(mListener)
    }

    @Test
    fun theAudioBecomingNoisyBroadcastReachesTheListener() {
        mReceiver.onReceive(mContext, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        assertEquals(1, mListener.noisyEvents)
    }

    @Test
    fun anUnrelatedBroadcastIsIgnored() {
        mReceiver.onReceive(mContext, Intent(AudioManager.ACTION_HEADSET_PLUG))

        assertEquals(0, mListener.noisyEvents)
    }

    @Test
    fun aBroadcastWithoutAnActionIsIgnored() {
        mReceiver.onReceive(mContext, Intent())

        assertEquals(0, mListener.noisyEvents)
    }

    @Test
    fun everyDeliveryOfTheBroadcastIsReported() {
        mReceiver.onReceive(mContext, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        mReceiver.onReceive(mContext, Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        assertEquals(2, mListener.noisyEvents)
    }

    @Test
    fun theIntentFilterSubscribesToTheNoisyActionAndNothingElse() {
        val filter = mReceiver.makeIntentFilter()

        assertEquals(1, filter.countActions())
        assertTrue(filter.hasAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    private class RecordingListener : BecomingNoisyReceiver.Listener {

        private var mNoisyEvents = 0

        val noisyEvents: Int
            get() = mNoisyEvents

        override fun onAudioBecomingNoisy() {
            mNoisyEvents++
        }
    }
}
