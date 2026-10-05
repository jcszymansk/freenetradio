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

package com.yuriy.openradio.shared.model.storage

import android.content.Context
import java.lang.ref.WeakReference

/**
 * The reference to hand an [AbstractStorage] in a JVM test, in place of a bare [WeakReference].
 *
 * A storage keeps its context only weakly, so a test whose context is otherwise unreachable loses
 * it to the first collection: every write then does nothing and every read answers its default,
 * and an assertion about a default passes no matter what the code under test did. Holding the
 * context here ties its lifetime to the storage that holds this reference.
 *
 * @param mContext the context the storage reads and writes through.
 */
internal class StrongContextReference(private val mContext: Context) : WeakReference<Context>(mContext) {

    override fun get(): Context = mContext
}
