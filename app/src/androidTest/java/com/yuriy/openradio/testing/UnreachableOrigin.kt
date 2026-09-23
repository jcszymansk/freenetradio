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

package com.yuriy.openradio.testing

/**
 * The origin of every url a fixture needs that no test means to serve.
 *
 * A station url the service holds can end up opened even when the test never plays it, since the
 * service builds a playlist from what it remembers. A host name would then be sent to a resolver,
 * and reserved names such as `.invalid` or `.test` are not short-circuited on Android: the query
 * leaves the device. An address literal needs no lookup, the loopback address never leaves the
 * device, and port 9 is privileged, so no app process can listen on it and a connection is refused
 * at once.
 */
const val UNREACHABLE_ORIGIN = "http://127.0.0.1:9"
