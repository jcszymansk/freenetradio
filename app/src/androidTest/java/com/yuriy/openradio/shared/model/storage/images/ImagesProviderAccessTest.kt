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

package com.yuriy.openradio.shared.model.storage.images

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins who can read the artwork store through [ImagesProvider].
 *
 * The store holds directory artwork and any image the user picked for a station of their own, so
 * the provider is not exported: this application reads it, and another one only after the service
 * granted it the uri, which it does for Android Auto alone.
 *
 * The other application is the shell, the one foreign uid a test can act as. Its `content`
 * command is refused the provider outright, grant or not, because it asks for it from outside any
 * application process; so whether a grant opens the uri is asked of the platform's uri permission
 * check instead, which is what the provider consults when Android Auto opens the uri. The
 * instrumentation shares this application's uid, so a read from here says only that the phone UI
 * still gets its artwork.
 */
@RunWith(AndroidJUnit4::class)
class ImagesProviderAccessTest {

    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()

    private val mContext = mInstrumentation.targetContext

    private val mDao = ImagesDatabase.getInstance(mContext).rsImageDao()

    /**
     * The url is never fetched, because the image is already stored; the provider only refuses a
     * uri without one.
     */
    private val mUri = ImagesStore.buildImageUri(IMAGE_ID, "/unused/artwork.png")

    @Before
    fun setUp() {
        mDao.insertImage(Image(IMAGE_ID, IMAGE_BYTES))
    }

    @After
    fun tearDown() {
        mContext.revokeUriPermission(mUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        mDao.delete(IMAGE_ID)
    }

    @Test
    fun theProviderIsNotExportedAndGrantsUris() {
        val provider = mContext.packageManager.resolveContentProvider(ImagesStore.AUTHORITY_URI.authority!!, 0)

        assertNotNull("The artwork provider is installed", provider)
        assertFalse("Exported", provider!!.exported)
        assertTrue("Grants uri permissions", provider.grantUriPermissions)
    }

    @Test
    fun theApplicationReadsStoredArtwork() {
        val bytes = mContext.contentResolver.openInputStream(mUri)!!.use { it.readBytes() }

        assertArrayEquals(IMAGE_BYTES, bytes)
    }

    @Test
    fun anotherApplicationCannotReadArtwork() {
        assertEquals("", readAsShell(mUri))
    }

    @Test
    fun anotherApplicationMayReadOnlyTheArtworkItWasGranted() {
        val other = ImagesStore.buildImageUri("$IMAGE_ID-other", "/unused/other.png")
        assertEquals("Before the grant", PackageManager.PERMISSION_DENIED, shellMayRead(mUri))

        mContext.grantUriPermission(SHELL_PACKAGE, mUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        assertEquals("Granted image", PackageManager.PERMISSION_GRANTED, shellMayRead(mUri))
        assertEquals("Another image", PackageManager.PERMISSION_DENIED, shellMayRead(other))
    }

    private fun shellMayRead(uri: Uri): Int {
        val shellUid = mContext.packageManager.getApplicationInfo(SHELL_PACKAGE, 0).uid
        return mContext.checkUriPermission(uri, NO_PID, shellUid, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * What the shell's `content read` prints for [uri]. A refused read prints its error on stderr,
     * which this does not capture, so it comes back empty.
     */
    private fun readAsShell(uri: Uri): String {
        val output = mInstrumentation.uiAutomation.executeShellCommand("content read --uri '$uri'")
        return ParcelFileDescriptor.AutoCloseInputStream(output).use { String(it.readBytes()) }
    }

    private companion object {

        const val IMAGE_ID = "images-provider-access-test"

        const val SHELL_PACKAGE = "com.android.shell"

        /** The check is about the uid; no process of the shell's is running it. */
        const val NO_PID = 0

        /** Not an image; the provider hands back whatever bytes are stored. */
        val IMAGE_BYTES = "artwork bytes".toByteArray()
    }
}
