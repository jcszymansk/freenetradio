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

package com.yuriy.openradio.shared.utils

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Permission for one read made of several requests to use the network, which ends for good the
 * first time [mayUseNetwork] refuses it.
 *
 * Every request and every transfer of the read asks [mayContinue], from whichever thread it runs
 * on. A refusal is remembered rather than asked again, so the read as a whole knows it was cut
 * short even when the network becomes usable again before it looks, and a part of it that was
 * stopped is never mistaken for one that found nothing.
 *
 * @param mayUseNetwork Whether the network may be used now. Not asked again after it refused.
 */
class NetworkPermit(private val mayUseNetwork: () -> Boolean) {

    private val mIsRevoked = AtomicBoolean(false)

    /**
     * Whether the network refused this read at any point.
     */
    val isRevoked: Boolean
        get() = mIsRevoked.get()

    /**
     * Whether the read may go on using the network.
     */
    fun mayContinue(): Boolean {
        if (mIsRevoked.get()) {
            return false
        }
        if (mayUseNetwork()) {
            return true
        }
        mIsRevoked.set(true)
        return false
    }
}
