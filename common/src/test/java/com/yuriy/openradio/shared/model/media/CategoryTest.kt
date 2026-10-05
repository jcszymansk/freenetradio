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

package com.yuriy.openradio.shared.model.media

import java.util.TreeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTest {

    @Test
    fun categoriesWithMoreStationsComeFirst() {
        assertBrowseOrder(
            listOf("pop", "rock", "jazz"),
            Category("jazz", "Jazz", 1),
            Category("pop", "Pop", 30),
            Category("rock", "Rock", 2)
        )
    }

    @Test
    fun equalCountsAreOrderedByTitleIgnoringCase() {
        assertBrowseOrder(
            listOf("ambient", "Blues", "chill"),
            Category("chill", "chill", 5),
            Category("Blues", "Blues", 5),
            Category("ambient", "ambient", 5)
        )
    }

    /**
     * Radio Browser titles are its tag names with the first letter raised, so the tags `rock` and
     * `Rock` reach the list under the same title, and only the id can still tell them apart.
     */
    @Test
    fun equalCountsAndTitlesAreOrderedById() {
        assertBrowseOrder(
            listOf("Rock", "rock"),
            Category("rock", "Rock", 3),
            Category("Rock", "Rock", 3)
        )
    }

    @Test
    fun titleDecidesBeforeId() {
        assertBrowseOrder(
            listOf("z", "a"),
            Category("a", "Zed", 1),
            Category("z", "Alpha", 1)
        )
    }

    /**
     * Case-insensitive yet not locale aware: the comparison is per character, so an accented title
     * sorts after the whole unaccented alphabet whatever language the device is set to.
     */
    @Test
    fun titlesAreComparedByCharacterNotByLocaleCollation() {
        assertBrowseOrder(
            listOf("zouk", "électro"),
            Category("électro", "Électro", 4),
            Category("zouk", "Zouk", 4)
        )
    }

    @Test
    fun aSortedSetKeepsEveryDistinctCategoryWithTheSameCount() {
        val categories = TreeSet(Category.BROWSE_ORDER)

        assertTrue(categories.add(Category("rock", "Rock", 3)))
        assertTrue(categories.add(Category("jazz", "Jazz", 3)))
        assertTrue(categories.add(Category("Rock", "Rock", 3)))

        assertEquals(listOf("jazz", "Rock", "rock"), categories.map { it.id })
    }

    @Test
    fun onlyTheSameCountIdAndCaseFoldedTitleCompareEqual() {
        val rock = Category("rock", "Rock", 3)

        assertEquals(0, Category.BROWSE_ORDER.compare(rock, Category("rock", "Rock", 3)))
        assertEquals(0, Category.BROWSE_ORDER.compare(rock, Category("rock", "ROCK", 3)))
        assertEquals(1, Integer.signum(Category.BROWSE_ORDER.compare(rock, Category("rock", "Rock", 4))))
        assertEquals(-1, Integer.signum(Category.BROWSE_ORDER.compare(rock, Category("rock", "Rocks", 3))))
        assertEquals(1, Integer.signum(Category.BROWSE_ORDER.compare(rock, Category("Rock", "Rock", 3))))
    }

    private fun assertBrowseOrder(expectedIds: List<String>, vararg categories: Category) {
        assertEquals(expectedIds, categories.sortedWith(Category.BROWSE_ORDER).map { it.id })
        assertEquals(expectedIds, categories.reversed().sortedWith(Category.BROWSE_ORDER).map { it.id })
    }
}
