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

package wseemann.media.jplaylistparser.parser.asx

import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import wseemann.media.jplaylistparser.parser.AutoDetectParser
import wseemann.media.jplaylistparser.parser.PlaylistFetcher
import wseemann.media.jplaylistparser.playlist.Playlist
import wseemann.media.jplaylistparser.playlist.PlaylistEntry
import java.io.ByteArrayInputStream
import java.io.StringReader

/**
 * Pins how [ASXPlaylistParser] reads an ASX playlist through the public [AutoDetectParser] entry
 * point: which `REF` of an `ENTRY` names the stream and how its address is found, titles and track
 * numbers, case insensitive element names, the repair of real world malformed XML, ampersand and
 * entity handling, that no external DTD is ever read, and how `ENTRYREF` is followed through the
 * [PlaylistFetcher] with its failure modes, cycles and the depth limit.
 *
 * Every read beyond the top level stream goes to a [RecordingFetcher]; the urls are only strings
 * handed to it, and nothing in these tests opens a connection.
 */
class ASXPlaylistParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun refAddressIsFoundInHrefAttributeOfAnyCaseOrInElementText() {
        data class Fixture(val name: String, val ref: String, val expected: String)

        val fixtures = listOf(
            Fixture("lower case href", "<REF href=\"$HOST/lower\"/>", "$HOST/lower"),
            Fixture("upper case HREF", "<REF HREF=\"$HOST/upper\"/>", "$HOST/upper"),
            Fixture("mixed case Href", "<REF Href=\"$HOST/mixed\"/>", "$HOST/mixed"),
            Fixture("element text", "<REF>$HOST/text</REF>", "$HOST/text"),
            Fixture(
                "padded attribute",
                "<REF href=\"  \n\t$HOST/padded-attribute \n \"/>",
                "$HOST/padded-attribute"
            ),
            Fixture("padded text", "<REF>\n    $HOST/padded-text\n  \t</REF>", "$HOST/padded-text"),
            Fixture(
                "attribute and text",
                "<REF href=\"$HOST/attribute\">$HOST/text-loses</REF>",
                "$HOST/attribute"
            )
        )

        fixtures.forEach { fixture ->
            val fetcher = RecordingFetcher()
            val playlist = parse("<ASX><ENTRY>${fixture.ref}</ENTRY></ASX>", fetcher)

            assertEquals(fixture.name, listOf(fixture.expected), uris(playlist))
            assertEquals(fixture.name, emptyList<String>(), fetcher.reads)
        }
    }

    @Test
    fun onlyTheFirstRefOfAnEntryIsKept() {
        val fetcher = RecordingFetcher()

        val playlist = parse(
            "<ASX><ENTRY>" +
                    "<REF href=\"$HOST/first\"/>" +
                    "<REF href=\"$HOST/second\"/>" +
                    "<REF href=\"$HOST/third.asx\"/>" +
                    "</ENTRY></ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/first"), uris(playlist))
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun aRefWithoutAnAddressGivesWayToTheNextOne() {
        data class Fixture(val name: String, val blankRef: String)

        val fixtures = listOf(
            Fixture("empty element", "<REF/>"),
            Fixture("empty href", "<REF href=\"\"/>"),
            Fixture("blank href and blank text", "<REF href=\"  \"> \n </REF>")
        )

        fixtures.forEach { fixture ->
            val playlist = parse(
                "<ASX><ENTRY>${fixture.blankRef}<REF href=\"$HOST/fallback\"/></ENTRY></ASX>",
                RecordingFetcher()
            )

            assertEquals(fixture.name, listOf("$HOST/fallback"), uris(playlist))
        }
    }

    @Test
    fun anEmptyHrefFallsBackToTheElementText() {
        val playlist = parse("<ASX><ENTRY><REF href=\"\">$HOST/text</REF></ENTRY></ASX>", RecordingFetcher())

        assertEquals(listOf("$HOST/text"), uris(playlist))
    }

    @Test
    fun anEntryWhoseRefsAllLackAnAddressIsDropped() {
        val playlist = parse(
            "<ASX><ENTRY><REF/><REF href=\" \"/><TITLE>Nothing</TITLE></ENTRY></ASX>",
            RecordingFetcher()
        )

        assertTrue(playlist.playlistEntries.isEmpty())
    }

    @Test
    fun entryWithoutRefIsDroppedAndTakesNoTrackNumber() {
        val fetcher = RecordingFetcher()

        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><TITLE>Nothing to play</TITLE></ENTRY>" +
                    "<ENTRY></ENTRY>" +
                    "<ENTRY><TITLE>Kept</TITLE><REF href=\"$HOST/kept\"/></ENTRY>" +
                    "</ASX>",
            fetcher
        )

        val entry = playlist.playlistEntries.single()
        assertEquals("$HOST/kept", entry[PlaylistEntry.URI])
        assertEquals("1", entry[PlaylistEntry.TRACK])
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun onlyEntryWithoutRefYieldsNoEntries() {
        val fetcher = RecordingFetcher()

        val playlist = parse("<ASX><ENTRY><TITLE>Nothing</TITLE></ENTRY></ASX>", fetcher)

        assertTrue(playlist.playlistEntries.isEmpty())
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun titleIsTrimmedAndReadBeforeOrAfterTheRef() {
        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><TITLE>\n   Before  \t</TITLE><REF href=\"$HOST/before\"/></ENTRY>" +
                    "<ENTRY><REF href=\"$HOST/after\"/><TITLE> After </TITLE></ENTRY>" +
                    "</ASX>",
            RecordingFetcher()
        )

        assertEquals(listOf("$HOST/before", "$HOST/after"), uris(playlist))
        assertEquals(listOf("Before", "After"), titles(playlist))
    }

    @Test
    fun entryWithoutTitleHasEmptyMetadata() {
        val playlist = parse("<ASX><ENTRY><REF href=\"$HOST/untitled\"/></ENTRY></ASX>", RecordingFetcher())

        assertEquals("", playlist.playlistEntries.single()[PlaylistEntry.PLAYLIST_METADATA])
    }

    @Test
    fun entriesKeepDocumentOrderAndAreNumberedFromOne() {
        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><TITLE>One</TITLE><REF href=\"$HOST/one\"/></ENTRY>" +
                    "<ENTRY><TITLE>Two</TITLE><REF href=\"$HOST/two\"/></ENTRY>" +
                    "<ENTRY><TITLE>Three</TITLE><REF href=\"$HOST/three\"/></ENTRY>" +
                    "</ASX>",
            RecordingFetcher()
        )

        assertEquals(listOf("$HOST/one", "$HOST/two", "$HOST/three"), uris(playlist))
        assertEquals(listOf("One", "Two", "Three"), titles(playlist))
        assertEquals(listOf("1", "2", "3"), tracks(playlist))
    }

    @Test
    fun everyParseNumbersItsEntriesFromOne() {
        val xml = "<ASX>" +
                "<ENTRY><REF href=\"$HOST/one\"/></ENTRY>" +
                "<ENTRY><REF href=\"$HOST/two\"/></ENTRY>" +
                "</ASX>"

        val first = parse(xml, RecordingFetcher())
        val second = parse(xml, RecordingFetcher())

        assertEquals(listOf("1", "2"), tracks(first))
        // Track numbers once came from a counter shared by every parse, so the second playlist
        // of a process started where the first had stopped.
        assertEquals(listOf("1", "2"), tracks(second))
    }

    @Test
    fun elementNamesAreCaseInsensitive() {
        data class Fixture(val name: String, val xml: String)

        val fixtures = listOf(
            Fixture(
                "lower case",
                "<asx><entry><ref href=\"$HOST/stream\"/><title>Station</title></entry></asx>"
            ),
            Fixture(
                "mixed case",
                "<Asx><Entry><Ref Href=\"$HOST/stream\"/><Title>Station</Title></Entry></Asx>"
            ),
            Fixture(
                "alternating case",
                "<aSx><EnTrY><rEf hReF=\"$HOST/stream\"/><TiTlE>Station</TiTlE></EnTrY></aSx>"
            )
        )

        fixtures.forEach { fixture ->
            val playlist = parse(fixture.xml, RecordingFetcher())

            assertEquals(fixture.name, listOf("$HOST/stream"), uris(playlist))
            assertEquals(fixture.name, listOf("Station"), titles(playlist))
            assertEquals(fixture.name, listOf("1"), tracks(playlist))
        }
    }

    @Test
    fun endTagsThatDifferFromTheirStartTagsOnlyInCaseAreRepaired() {
        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><Title>Repaired</TITLE><REF href=\"$HOST/one\"/></entry>" +
                    "<entry><REF href=\"$HOST/two\"></ref></ENTRY>" +
                    "</asx>",
            RecordingFetcher()
        )

        assertEquals(listOf("$HOST/one", "$HOST/two"), uris(playlist))
        assertEquals(listOf("Repaired", ""), titles(playlist))
        assertEquals(listOf("1", "2"), tracks(playlist))
    }

    @Test
    fun entryThatIsNeverClosedYieldsNoEntries() {
        val fetcher = RecordingFetcher()

        val playlist = parse("<ASX><ENTRY><REF href=\"$HOST/stream\"/></ASX>", fetcher)

        assertTrue(playlist.playlistEntries.isEmpty())
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun contentThatIsNotXmlYieldsNoEntries() {
        listOf("this is not a playlist", "", "<ASX", "</ASX>", "<<>>&&").forEach { content ->
            val fetcher = RecordingFetcher()

            val playlist = parse(content, fetcher)

            assertTrue("'$content'", playlist.playlistEntries.isEmpty())
            assertEquals("'$content'", emptyList<String>(), fetcher.reads)
        }
    }

    @Test
    fun cdataTitleWithoutAmpersandIsKeptExactly() {
        val playlist = parse(
            "<ASX><ENTRY><TITLE><![CDATA[Rock <b>and</b> Roll]]></TITLE>" +
                    "<REF href=\"$HOST/stream\"/></ENTRY></ASX>",
            RecordingFetcher()
        )

        assertEquals("Rock <b>and</b> Roll", playlist.playlistEntries.single()[PlaylistEntry.PLAYLIST_METADATA])
    }

    @Test
    fun cdataTitleWithBareAmpersandIsKeptExactly() {
        val playlist = parse(
            "<ASX><ENTRY><TITLE><![CDATA[Rock <b>&</b> Roll]]></TITLE>" +
                    "<REF href=\"$HOST/stream\"/></ENTRY></ASX>",
            RecordingFetcher()
        )

        assertEquals("Rock <b>&</b> Roll", playlist.playlistEntries.single()[PlaylistEntry.PLAYLIST_METADATA])
    }

    @Test
    fun ampersandsOutsideCdataAreEscapedWhileCdataKeepsItsText() {
        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><TITLE>A & <![CDATA[B & &amp; C]]> & <![CDATA[&D]]>&</TITLE>" +
                    "<REF href=\"$HOST/stream?a=1&b=2\"/></ENTRY>" +
                    "</ASX>",
            RecordingFetcher()
        )

        val entry = playlist.playlistEntries.single()
        assertEquals("A & B & &amp; C & &D&", entry[PlaylistEntry.PLAYLIST_METADATA])
        assertEquals("$HOST/stream?a=1&b=2", entry[PlaylistEntry.URI])
    }

    @Test
    fun ampersandsInAddressesAreDecodedOnce() {
        data class Fixture(val name: String, val href: String, val expected: String)

        val fixtures = listOf(
            Fixture("bare ampersand", "$HOST/stream?a=1&b=2", "$HOST/stream?a=1&b=2"),
            Fixture("escaped ampersand", "$HOST/stream?a=1&amp;b=2", "$HOST/stream?a=1&b=2"),
            Fixture("decimal reference", "$HOST/stream?a=1&#38;b=2", "$HOST/stream?a=1&b=2"),
            Fixture("hexadecimal reference", "$HOST/stream?a=1&#x26;b=2", "$HOST/stream?a=1&b=2"),
            Fixture(
                "parameter named like an entity",
                "$HOST/stream?a=1&lt=2&amp=3",
                "$HOST/stream?a=1&lt=2&amp=3"
            ),
            Fixture("trailing ampersand", "$HOST/stream?a=1&", "$HOST/stream?a=1&")
        )

        fixtures.forEach { fixture ->
            val attribute = parse("<ASX><ENTRY><REF href=\"${fixture.href}\"/></ENTRY></ASX>", RecordingFetcher())
            val text = parse("<ASX><ENTRY><REF>${fixture.href}</REF></ENTRY></ASX>", RecordingFetcher())

            assertEquals("${fixture.name} in attribute", listOf(fixture.expected), uris(attribute))
            assertEquals("${fixture.name} in text", listOf(fixture.expected), uris(text))
        }
    }

    @Test
    fun entityDeclaredInInternalSubsetIsNotExpanded() {
        val playlist = parse(
            "<?xml version=\"1.0\"?>" +
                    "<!DOCTYPE ASX [<!ENTITY foo \"expanded\">]>" +
                    "<ASX><ENTRY><TITLE>&foo;</TITLE><REF href=\"$HOST/stream?x=&foo;\"/></ENTRY></ASX>",
            RecordingFetcher()
        )

        val entry = playlist.playlistEntries.single()
        assertEquals("$HOST/stream?x=&foo;", entry[PlaylistEntry.URI])
        assertEquals("&foo;", entry[PlaylistEntry.PLAYLIST_METADATA])
    }

    @Test
    fun externalDtdIsNeverRead() {
        val dtd = temporaryFolder.newFile("broken.dtd")
        dtd.writeText("<!ELEMENT broken")
        val xml = "<?xml version=\"1.0\"?>" +
                "<!DOCTYPE ASX SYSTEM \"${dtd.toURI()}\">" +
                "<ASX><ENTRY><TITLE>Station</TITLE><REF href=\"$HOST/stream\"/></ENTRY></ASX>"
        // Proves the fixture: a builder that does read the DTD fails on it, so the parse below
        // succeeding means the DTD was not read.
        assertThrows(JDOMException::class.java) { SAXBuilder().build(StringReader(xml)) }
        val fetcher = RecordingFetcher()

        val playlist = parse(xml, fetcher)

        assertEquals(listOf("$HOST/stream"), uris(playlist))
        assertEquals(listOf("Station"), titles(playlist))
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun entryRefAddressIsFoundInHrefAttributeOfAnyCaseOrInElementText() {
        data class Fixture(val name: String, val entryRef: String)

        val target = "$HOST/target.asx"
        val fixtures = listOf(
            Fixture("lower case href", "<ENTRYREF href=\"$target\"/>"),
            Fixture("upper case HREF", "<ENTRYREF HREF=\"$target\"/>"),
            Fixture("mixed case Href", "<EntryRef Href=\"$target\"/>"),
            Fixture("element text", "<ENTRYREF>$target</ENTRYREF>"),
            Fixture("padded attribute", "<ENTRYREF href=\" \n $target \t\"/>"),
            Fixture("padded text", "<ENTRYREF>\n  $target\n</ENTRYREF>")
        )

        fixtures.forEach { fixture ->
            val fetcher = RecordingFetcher(
                target to "<ASX><ENTRY><REF href=\"$HOST/referenced\"/></ENTRY></ASX>"
            )

            val playlist = parse("<ASX>${fixture.entryRef}</ASX>", fetcher)

            assertEquals(fixture.name, listOf(target), fetcher.reads)
            assertEquals(fixture.name, listOf("$HOST/referenced"), uris(playlist))
        }
    }

    @Test
    fun entryRefToUrlWithoutExtensionIsRecognisedByContent() {
        data class Fixture(val name: String, val content: String, val expected: String)

        val fixtures = listOf(
            Fixture(
                "PLS",
                "[playlist]\nFile1=$HOST/from-pls\nTitle1=PLS\nLength1=-1\n",
                "$HOST/from-pls"
            ),
            Fixture("M3U", "#EXTM3U\n#EXTINF:-1,M3U\n$HOST/from-m3u\n", "$HOST/from-m3u"),
            Fixture(
                "ASX",
                "<asx version=\"3.0\"><entry><ref href=\"$HOST/from-asx\"/></entry></asx>",
                "$HOST/from-asx"
            )
        )

        fixtures.forEach { fixture ->
            val reference = "$HOST/listing"
            val fetcher = RecordingFetcher(reference to fixture.content)

            val playlist = parse("<ASX><ENTRYREF href=\"$reference\"/></ASX>", fetcher)

            assertEquals(fixture.name, listOf(reference), fetcher.reads)
            assertEquals(fixture.name, listOf(fixture.expected), uris(playlist))
        }
    }

    @Test
    fun entriesAndEntryRefsInterleaveInDocumentOrder() {
        val fetcher = RecordingFetcher(
            "$HOST/second.asx" to "<ASX><ENTRY><TITLE>Two</TITLE><REF href=\"$HOST/s2\"/></ENTRY></ASX>",
            "$HOST/fourth" to "[playlist]\nFile1=$HOST/s4\nTitle1=Four\nLength1=-1\n",
            "$HOST/fifth" to "#EXTM3U\n#EXTINF:-1,Five\n$HOST/s5\n",
            "$HOST/sixth" to "<ASX><ENTRY><TITLE>Six</TITLE><REF href=\"$HOST/s6\"/></ENTRY></ASX>"
        )

        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><TITLE>One</TITLE><REF href=\"$HOST/s1\"/></ENTRY>" +
                    "<ENTRYREF href=\"$HOST/second.asx\"/>" +
                    "<ENTRY><TITLE>Three</TITLE><REF href=\"$HOST/s3\"/></ENTRY>" +
                    "<ENTRYREF href=\"$HOST/fourth\"/>" +
                    "<ENTRYREF href=\"$HOST/fifth\"/>" +
                    "<ENTRYREF href=\"$HOST/sixth\"/>" +
                    "<ENTRY><TITLE>Seven</TITLE><REF href=\"$HOST/s7\"/></ENTRY>" +
                    "</ASX>",
            fetcher
        )

        assertEquals((1..7).map { "$HOST/s$it" }, uris(playlist))
        assertEquals(listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven"), titles(playlist))
        assertEquals(
            listOf("$HOST/second.asx", "$HOST/fourth", "$HOST/fifth", "$HOST/sixth"),
            fetcher.reads
        )
    }

    @Test
    fun entryRefThatCannotBeReadAddsNothing() {
        val fetcher = RecordingFetcher()

        val playlist = parse(
            "<ASX>" +
                    "<ENTRY><REF href=\"$HOST/before\"/></ENTRY>" +
                    "<ENTRYREF href=\"$HOST/missing.asx\"/>" +
                    "<ENTRY><REF href=\"$HOST/after\"/></ENTRY>" +
                    "</ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/before", "$HOST/after"), uris(playlist))
        assertEquals(listOf("$HOST/missing.asx"), fetcher.reads)
    }

    @Test
    fun entryRefToContentInNoKnownFormatAddsNothing() {
        val fetcher = RecordingFetcher(
            "$HOST/audio" to "ID3 this is not a playlist",
            "$HOST/plain-m3u" to "$HOST/unsigned-m3u-entry\n"
        )

        val playlist = parse(
            "<ASX>" +
                    "<ENTRYREF href=\"$HOST/audio\"/>" +
                    "<ENTRYREF href=\"$HOST/plain-m3u\"/>" +
                    "<ENTRY><REF href=\"$HOST/kept\"/></ENTRY>" +
                    "</ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/kept"), uris(playlist))
        assertEquals(listOf("$HOST/audio", "$HOST/plain-m3u"), fetcher.reads)
    }

    @Test
    fun entryRefToMalformedAsxAddsNothing() {
        val fetcher = RecordingFetcher(
            "$HOST/broken.asx" to "<ASX><ENTRY><REF href=\"$HOST/lost\"/></ASX>"
        )

        val playlist = parse(
            "<ASX><ENTRYREF href=\"$HOST/broken.asx\"/><ENTRY><REF href=\"$HOST/kept\"/></ENTRY></ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/kept"), uris(playlist))
        assertEquals(listOf("$HOST/broken.asx"), fetcher.reads)
    }

    @Test
    fun entryRefWithBlankAddressAddsNothingAndReadsNothing() {
        listOf(
            "<ENTRYREF/>",
            "<ENTRYREF href=\"\"/>",
            "<ENTRYREF href=\"  \n \"/>",
            "<ENTRYREF>   </ENTRYREF>"
        ).forEach { entryRef ->
            val fetcher = RecordingFetcher()

            val playlist = parse("<ASX>$entryRef<ENTRY><REF href=\"$HOST/kept\"/></ENTRY></ASX>", fetcher)

            assertEquals(entryRef, listOf("$HOST/kept"), uris(playlist))
            assertEquals(entryRef, emptyList<String>(), fetcher.reads)
        }
    }

    @Test
    fun entryRefToItselfReadsNothing() {
        val fetcher = RecordingFetcher(TOP to "<ASX><ENTRY><REF href=\"$HOST/again\"/></ENTRY></ASX>")

        val playlist = parse(
            "<ASX><ENTRY><REF href=\"$HOST/once\"/></ENTRY><ENTRYREF href=\"$TOP\"/></ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/once"), uris(playlist))
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun twoPlaylistCycleReadsEachPlaylistOnce() {
        val first = "$HOST/a.asx"
        val second = "$HOST/b.asx"
        val fetcher = RecordingFetcher(
            first to "<ASX><ENTRY><REF href=\"$HOST/a-again\"/></ENTRY></ASX>",
            second to "<ASX><ENTRY><REF href=\"$HOST/b-stream\"/></ENTRY><ENTRYREF href=\"$first\"/></ASX>"
        )

        val playlist = parse(
            "<ASX><ENTRY><REF href=\"$HOST/a-stream\"/></ENTRY><ENTRYREF href=\"$second\"/></ASX>",
            fetcher,
            first
        )

        assertEquals(listOf("$HOST/a-stream", "$HOST/b-stream"), uris(playlist))
        assertEquals(listOf(second), fetcher.reads)
    }

    @Test
    fun entryRefToPlaylistAlreadyFollowedIsNotReadAgain() {
        val referenced = "$HOST/shared.asx"
        val fetcher = RecordingFetcher(
            referenced to "<ASX><ENTRY><REF href=\"$HOST/shared-stream\"/></ENTRY></ASX>"
        )

        val playlist = parse(
            "<ASX>" +
                    "<ENTRYREF href=\"$referenced\"/>" +
                    "<ENTRYREF href=\"$referenced\"/>" +
                    "<ENTRYREF href=\"$referenced\"/>" +
                    "</ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/shared-stream"), uris(playlist))
        assertEquals(listOf(referenced), fetcher.reads)
    }

    @Test
    fun entryRefThatFailedToReadIsNotRetried() {
        val fetcher = RecordingFetcher()

        parse(
            "<ASX><ENTRYREF href=\"$HOST/missing.asx\"/><ENTRYREF href=\"$HOST/missing.asx\"/></ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/missing.asx"), fetcher.reads)
    }

    @Test
    fun entryWhoseRefIsAPlaylistIsFollowedOnlyOnce() {
        val referenced = "$HOST/nested.asx"
        val fetcher = RecordingFetcher(
            referenced to "<ASX><ENTRY><REF href=\"$HOST/nested-stream\"/></ENTRY></ASX>"
        )

        val playlist = parse(
            "<ASX>" +
                    "<ENTRYREF href=\"$referenced\"/>" +
                    "<ENTRY><REF href=\"$referenced\"/></ENTRY>" +
                    "<ENTRY><REF href=\"$TOP\"/></ENTRY>" +
                    "<ENTRY><REF href=\"$HOST/stream\"/></ENTRY>" +
                    "</ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/nested-stream", "$HOST/stream"), uris(playlist))
        assertEquals(listOf(referenced), fetcher.reads)
    }

    @Test
    fun entryWhoseRefIsAPlaylistAddsWhatThatPlaylistNames() {
        val referenced = "$HOST/nested.asx"
        val fetcher = RecordingFetcher(
            referenced to "<ASX><ENTRY><REF href=\"$HOST/nested-stream\"/></ENTRY></ASX>"
        )

        val playlist = parse(
            "<ASX><ENTRY><TITLE>Outer</TITLE><REF href=\"$referenced\"/></ENTRY></ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/nested-stream"), uris(playlist))
        assertEquals(listOf(referenced), fetcher.reads)
    }

    @Test
    fun chainOfEntryRefsStopsAtTheDepthLimit() {
        val chainLength = AutoDetectParser.MAX_DEPTH + 3
        val contents = (1..chainLength).map { level -> level(level) to chainLink(level) }
        val fetcher = RecordingFetcher(*contents.toTypedArray())

        val playlist = parse(chainLink(0), fetcher, level(0))

        // The top level is parsed at depth 0 and every followed playlist one deeper, so a
        // session at MAX_DEPTH, the one parsing the MAX_DEPTH-th read, follows nothing more.
        assertEquals((1..AutoDetectParser.MAX_DEPTH).map { level(it) }, fetcher.reads)
        assertEquals((0..AutoDetectParser.MAX_DEPTH).map { "$HOST/stream-$it" }, uris(playlist))
    }

    @Test
    fun unsupportedElementsAreIgnored() {
        val fetcher = RecordingFetcher()

        val playlist = parse(
            "<ASX version=\"3.0\">" +
                    "<TITLE>Playlist title</TITLE>" +
                    "<ABSTRACT>About</ABSTRACT>" +
                    "<MOREINFO href=\"$HOST/more.asx\"/>" +
                    "<BANNER href=\"$HOST/banner.asx\"><ABSTRACT>Banner</ABSTRACT></BANNER>" +
                    "<REF href=\"$HOST/root-ref.asx\"/>" +
                    "<ENTRY>" +
                    "<PARAM name=\"a\" value=\"b\"/>" +
                    "<AUTHOR>Someone</AUTHOR>" +
                    "<MOREINFO href=\"$HOST/entry-more.asx\"/>" +
                    "<ENTRYREF href=\"$HOST/entry-entryref.asx\"/>" +
                    "<DURATION value=\"00:00:10\"/>" +
                    "<REF href=\"$HOST/one\"/>" +
                    "<TITLE>One</TITLE>" +
                    "</ENTRY>" +
                    "<UNKNOWN><ENTRY><REF href=\"$HOST/nested-in-unknown\"/></ENTRY></UNKNOWN>" +
                    "<ENTRY><REF href=\"$HOST/two\"/><COPYRIGHT>c</COPYRIGHT></ENTRY>" +
                    "</ASX>",
            fetcher
        )

        assertEquals(listOf("$HOST/one", "$HOST/two"), uris(playlist))
        assertEquals(listOf("One", ""), titles(playlist))
        assertEquals(listOf("1", "2"), tracks(playlist))
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun refToStreamIsAddedWithoutRead() {
        val streams = listOf(
            "$HOST/stream",
            "$HOST/stream.mp3",
            "$HOST/live.aac?token=1",
            "mms://127.0.0.1/stream"
        )
        val fetcher = RecordingFetcher()

        val playlist = parse(
            "<ASX>" + streams.joinToString("") { "<ENTRY><REF href=\"$it\"/></ENTRY>" } + "</ASX>",
            fetcher
        )

        assertEquals(streams, uris(playlist))
        assertEquals(emptyList<String>(), fetcher.reads)
    }

    @Test
    fun leadingByteOrderMarkAndWhitespaceAreSkipped() {
        val xml = "﻿ \r\n\t<?xml version=\"1.0\"?><ASX><ENTRY><REF href=\"$HOST/stream\"/></ENTRY></ASX>"
        val fetcher = RecordingFetcher("$HOST/listing" to xml)

        val topLevel = parse(xml, RecordingFetcher())
        val referenced = parse("<ASX><ENTRYREF href=\"$HOST/listing\"/></ASX>", fetcher)

        assertEquals(listOf("$HOST/stream"), uris(topLevel))
        assertEquals(listOf("$HOST/stream"), uris(referenced))
        assertEquals(listOf("$HOST/listing"), fetcher.reads)
    }

    @Test
    fun parsesWithoutDeclaredMimeType() {
        val playlist = Playlist()

        AutoDetectParser(RecordingFetcher()).parse(
            TOP,
            null,
            ByteArrayInputStream("<ASX><ENTRY><REF href=\"$HOST/stream\"/></ENTRY></ASX>".toByteArray()),
            playlist
        )

        assertEquals(listOf("$HOST/stream"), uris(playlist))
    }

    /**
     * Answers every read from [contents] and records each url asked for, in order. A url not in
     * [contents] gets the empty array that means it could not be read.
     */
    private class RecordingFetcher(vararg contents: Pair<String, String>) : PlaylistFetcher {

        private val mContents = contents.toMap()
        private val mReads = mutableListOf<String>()

        val reads: List<String>
            get() = mReads.toList()

        override fun fetch(url: String): ByteArray {
            mReads.add(url)
            return mContents[url]?.toByteArray() ?: ByteArray(0)
        }
    }

    private companion object {

        const val HOST = "http://127.0.0.1"
        const val TOP = "$HOST/top.asx"

        fun parse(xml: String, fetcher: PlaylistFetcher, url: String = TOP): Playlist {
            val playlist = Playlist()
            AutoDetectParser(fetcher).parse(
                url,
                "video/x-ms-asf",
                ByteArrayInputStream(xml.toByteArray()),
                playlist
            )
            return playlist
        }

        fun level(level: Int): String = "$HOST/l$level.asx"

        fun chainLink(level: Int): String {
            return "<ASX>" +
                    "<ENTRY><REF href=\"$HOST/stream-$level\"/></ENTRY>" +
                    "<ENTRYREF href=\"${level(level + 1)}\"/>" +
                    "</ASX>"
        }

        fun uris(playlist: Playlist): List<String> = playlist.playlistEntries.map { it[PlaylistEntry.URI] }

        fun titles(playlist: Playlist): List<String> =
            playlist.playlistEntries.map { it[PlaylistEntry.PLAYLIST_METADATA] }

        fun tracks(playlist: Playlist): List<String> = playlist.playlistEntries.map { it[PlaylistEntry.TRACK] }
    }
}
