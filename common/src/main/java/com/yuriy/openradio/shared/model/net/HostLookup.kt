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

package com.yuriy.openradio.shared.model.net

import java.net.UnknownHostException

/**
 * Finds the hosts published under a name.
 *
 * [DnsMirrorUrlResolver] takes one so that the name server stays out of it: [DnsHostLookup] is
 * the only implementation that asks one, and the registry is the only place that builds it.
 */
fun interface HostLookup {

    /**
     * @param name Name to look up.
     * @return The host names behind [name], possibly none.
     * @throws UnknownHostException When the name cannot be resolved.
     */
    @Throws(UnknownHostException::class)
    fun hostsOf(name: String): List<String>
}
