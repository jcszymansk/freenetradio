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

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [MediaItemHelper]: the fields it stores in, and reads back from, the extras of a station's
 * [MediaMetadata], what each reader answers when the item, the metadata or the extras are
 * missing, and the fallback chain of [MediaItemHelper.getDisplayDescription].
 *
 * Media3's [MediaItem] and [MediaMetadata] are plain Java and run as they do on a device; the
 * extras are the working [Bundle] from `:android-jvm-stubs`, so a value only comes back here if it
 * was really stored.
 */
class MediaItemHelperTest {

    @Test
    fun aDrawableIdThatWasSetIsReadBack() {
        val bundle = Bundle()

        MediaItemHelper.setDrawableId(bundle, DRAWABLE)

        assertEquals(DRAWABLE, MediaItemHelper.getDrawableId(bundle))
    }

    @Test
    fun aZeroDrawableIdIsReadBackAndCountsAsValid() {
        val bundle = Bundle()

        MediaItemHelper.setDrawableId(bundle, 0)

        assertEquals(0, MediaItemHelper.getDrawableId(bundle))
        assertTrue(MediaItemHelper.isDrawableIdValid(0))
    }

    @Test
    fun settingADrawableIdOnNoBundleIsIgnored() {
        MediaItemHelper.setDrawableId(null, DRAWABLE)
    }

    @Test
    fun aBundleWithoutADrawableIdAnswersTheUndefinedIdWhichIsNotValid() {
        val drawableId = MediaItemHelper.getDrawableId(Bundle())

        assertEquals(UNDEFINED_ID, drawableId)
        assertFalse(MediaItemHelper.isDrawableIdValid(drawableId))
    }

    @Test
    fun noBundleAnswersTheUndefinedDrawableId() {
        assertEquals(UNDEFINED_ID, MediaItemHelper.getDrawableId(null))
    }

    @Test
    fun anyDrawableIdOtherThanTheUndefinedOneIsValid() {
        assertTrue(MediaItemHelper.isDrawableIdValid(DRAWABLE))
        assertTrue(MediaItemHelper.isDrawableIdValid(-2))
        assertFalse(MediaItemHelper.isDrawableIdValid(UNDEFINED_ID))
    }

    @Test
    fun aBitrateStoredInTheExtrasIsReadFromTheItem() {
        val extras = Bundle()

        MediaItemHelper.updateBitrateField(extras, 128)

        assertEquals(128, MediaItemHelper.getBitrateField(item(extras)))
    }

    @Test
    fun aMissingBitrateReadsAsZero() {
        assertEquals(0, MediaItemHelper.getBitrateField(null))
        assertEquals(0, MediaItemHelper.getBitrateField(item(extras = null)))
        assertEquals(0, MediaItemHelper.getBitrateField(item(Bundle())))
    }

    @Test
    fun theFavoriteFlagSetThroughTheMetadataIsReadBackAndCanBeCleared() {
        val metadata = metadata(extras = Bundle())

        MediaItemHelper.updateFavoriteField(metadata, true)
        assertTrue(MediaItemHelper.isFavoriteField(metadata))

        MediaItemHelper.updateFavoriteField(metadata, false)
        assertFalse(MediaItemHelper.isFavoriteField(metadata))
    }

    @Test
    fun theFavoriteFlagSetOnTheBundleIsSeenThroughTheMetadataCarryingIt() {
        val extras = Bundle()
        val metadata = metadata(extras = extras)

        MediaItemHelper.updateFavoriteField(extras, true)

        assertTrue(MediaItemHelper.isFavoriteField(metadata))
    }

    @Test
    fun settingTheFavoriteFlagOnMetadataWithoutExtrasIsIgnored() {
        val metadata = metadata(extras = null)

        MediaItemHelper.updateFavoriteField(metadata, true)

        assertNull(metadata.extras)
        assertFalse(MediaItemHelper.isFavoriteField(metadata))
    }

    @Test
    fun settingTheFavoriteFlagOnNoMetadataIsIgnored() {
        MediaItemHelper.updateFavoriteField(null as MediaMetadata?, true)
    }

    @Test
    fun aMissingFavoriteFlagReadsAsNotFavorite() {
        assertFalse(MediaItemHelper.isFavoriteField(null))
        assertFalse(MediaItemHelper.isFavoriteField(metadata(extras = null)))
        assertFalse(MediaItemHelper.isFavoriteField(metadata(extras = Bundle())))
    }

    /**
     * Nothing in the code base reads this flag back through [MediaItemHelper], so the stored key
     * is the only observable behaviour.
     */
    @Test
    fun theLocalStationFlagIsStoredUnderItsKey() {
        val bundle = Bundle()

        MediaItemHelper.updateLocalRadioStationField(bundle, true)
        assertTrue(bundle.getBoolean(KEY_IS_LOCAL))

        MediaItemHelper.updateLocalRadioStationField(bundle, false)
        assertTrue(bundle.containsKey(KEY_IS_LOCAL))
        assertFalse(bundle.getBoolean(KEY_IS_LOCAL))
    }

    @Test
    fun aSortIdStoredInTheExtrasIsReadFromTheItem() {
        val extras = Bundle()

        MediaItemHelper.updateSortIdField(extras, 7)

        assertEquals(7, MediaItemHelper.getSortIdField(item(extras)))
    }

    @Test
    fun aZeroSortIdIsReadBackRatherThanTreatedAsMissing() {
        val extras = Bundle()

        MediaItemHelper.updateSortIdField(extras, 0)

        assertEquals(0, MediaItemHelper.getSortIdField(item(extras)))
    }

    @Test
    fun aMissingSortIdReadsAsTheUnknownId() {
        assertEquals(UNDEFINED_ID, MediaItemHelper.getSortIdField(null))
        assertEquals(UNDEFINED_ID, MediaItemHelper.getSortIdField(item(extras = null)))
        assertEquals(UNDEFINED_ID, MediaItemHelper.getSortIdField(item(Bundle())))
    }

    @Test
    fun aNonEmptySubtitleIsTheDisplayDescription() {
        val metadata = metadata(subtitle = "subtitle", description = "description", extras = artist("artist"))

        assertEquals("subtitle", MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    @Test
    fun anEmptySubtitleFallsBackToTheDescription() {
        val metadata = metadata(subtitle = "", description = "description", extras = artist("artist"))

        assertEquals("description", MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    @Test
    fun anEmptySubtitleAndDescriptionFallBackToTheArtistExtra() {
        val metadata = metadata(subtitle = "", description = "", extras = artist("artist"))

        assertEquals("artist", MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    @Test
    fun anEmptyArtistExtraFallsBackToTheDefault() {
        val metadata = metadata(subtitle = "", description = "", extras = artist(""))

        assertEquals(DEFAULT, MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    @Test
    fun extrasWithoutAnArtistFallBackToTheDefault() {
        val metadata = metadata(subtitle = "", description = "", extras = Bundle())

        assertEquals(DEFAULT, MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    @Test
    fun noExtrasAfterAnEmptySubtitleAndDescriptionFallBackToTheDefault() {
        val metadata = metadata(subtitle = "", description = "", extras = null)

        assertEquals(DEFAULT, MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    /**
     * Current behaviour, and it looks unintended: an absent subtitle ends the chain at the
     * default, while an empty one goes on to the description and the artist extra.
     */
    @Test
    fun aMissingSubtitleAnswersTheDefaultWithoutConsultingDescriptionOrArtist() {
        val metadata = metadata(subtitle = null, description = "description", extras = artist("artist"))

        assertEquals(DEFAULT, MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    /**
     * Current behaviour, and it looks unintended: an absent description ends the chain at the
     * default, while an empty one goes on to the artist extra.
     */
    @Test
    fun aMissingDescriptionAnswersTheDefaultWithoutConsultingTheArtist() {
        val metadata = metadata(subtitle = "", description = null, extras = artist("artist"))

        assertEquals(DEFAULT, MediaItemHelper.getDisplayDescription(metadata, DEFAULT))
    }

    @Test
    fun metadataWithNothingToShowAnswersTheDefault() {
        assertEquals(DEFAULT, MediaItemHelper.getDisplayDescription(MediaMetadata.EMPTY, DEFAULT))
    }

    private fun artist(name: String): Bundle {
        val bundle = Bundle()
        bundle.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, name)
        return bundle
    }

    private fun metadata(
        subtitle: String? = null,
        description: String? = null,
        extras: Bundle?
    ): MediaMetadata {
        return MediaMetadata.Builder()
            .setSubtitle(subtitle)
            .setDescription(description)
            .setExtras(extras)
            .build()
    }

    private fun item(extras: Bundle?): MediaItem {
        return MediaItem.Builder()
            .setMediaId("station")
            .setMediaMetadata(metadata(extras = extras))
            .build()
    }

    companion object {
        private const val DEFAULT = "default"
        private const val DRAWABLE = 42
        private const val KEY_IS_LOCAL = "KEY_IS_LOCAL"

        /** MediaSessionCompat.QueueItem.UNKNOWN_ID, which the helper answers for every missing id. */
        private const val UNDEFINED_ID = -1
    }
}
