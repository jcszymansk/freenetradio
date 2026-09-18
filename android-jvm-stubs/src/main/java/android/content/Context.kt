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

package android.content

import android.content.res.Resources

/**
 * JVM replacement for the part of the Android [Context] that resolves resources. Every string
 * lookup is final on the framework class, so a test double cannot supply one, and the framework
 * stub answers null - which production code rejects the moment it binds the result to a value.
 *
 * Everything else a context does stays with [ContextWrapper], which overrides it; only the members
 * declared here are reachable through a `Context` reference, so a missing one fails loudly with
 * NoSuchMethodError rather than silently doing nothing.
 */
abstract class Context {

    abstract fun getResources(): Resources?

    abstract fun getPackageName(): String?

    abstract fun getApplicationContext(): Context?

    abstract fun getSharedPreferences(name: String?, mode: Int): SharedPreferences?

    abstract fun getContentResolver(): ContentResolver?

    abstract fun getSystemService(name: String?): Any?

    abstract fun getPackageManager(): android.content.pm.PackageManager?

    abstract fun getCacheDir(): java.io.File?

    abstract fun getFilesDir(): java.io.File?

    fun getString(resId: Int): String {
        return resources().getString(resId)
    }

    fun getString(resId: Int, vararg formatArgs: Any?): String {
        return resources().getString(resId, *formatArgs)
    }

    fun getText(resId: Int): CharSequence {
        return resources().getText(resId)
    }

    private fun resources(): Resources {
        return getResources()
            ?: throw UnsupportedOperationException(
                "This context has no resources: give the test a context that overrides getResources()"
            )
    }
}
