/*
 * Copyright 2014 William Seemann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wseemann.media.jplaylistparser.parser.asx

import com.yuriy.openradio.shared.utils.AppLogger
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.JDOMException
import wseemann.media.jplaylistparser.mime.MediaType
import wseemann.media.jplaylistparser.mime.MediaType.Companion.video
import wseemann.media.jplaylistparser.parser.AbstractParser
import wseemann.media.jplaylistparser.parser.AutoDetectParser
import wseemann.media.jplaylistparser.playlist.Playlist
import wseemann.media.jplaylistparser.playlist.PlaylistEntry
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.util.Locale

/**
 * Reads an ASX playlist: each `ENTRY` becomes a stream named by its first `REF`, and each
 * `ENTRYREF` is followed as another playlist.
 *
 * ASX is XML only in shape. Element names are case insensitive and real files mix the cases of an
 * element's start and end tags, and they carry bare ampersands in urls. Both are mended before the
 * document is read, see [readDocument].
 */
class ASXPlaylistParser(session: AutoDetectParser) : AbstractParser(session) {

    private var mNumberOfFiles = 0

    override val supportedTypes: Set<MediaType?>
        get() = setOf(video("x-ms-asf"))

    @Throws(IOException::class)
    override fun parse(uri: String, stream: InputStream, playlist: Playlist) {
        val document = readDocument(stream.bufferedReader().readText().trimStart(*LEADING_NOISE)) ?: return
        for (child in document.rootElement.children) {
            when (child.name.uppercase(Locale.ROOT)) {
                ENTRY_ELEMENT -> buildPlaylistEntry(child, playlist)
                ENTRYREF_ELEMENT -> follow(hrefOf(child), playlist)
                else -> AppLogger.d("$TAG skipping element '${child.name}' of $uri")
            }
        }
    }

    /**
     * Parses [xml] as it is, and once more with every tag name upper cased when that fails.
     *
     * The second attempt is what mends an element whose end tag differs from its start tag only
     * in case. It is not the first, so a well formed document keeps its text exactly, CDATA
     * included. Input still malformed after it, such as an element that is never closed, yields
     * no document.
     *
     * @return The document, or null when [xml] cannot be read as one.
     */
    private fun readDocument(xml: String): Document? {
        val escaped = escapeBareAmpersands(xml)
        val builder = newXmlBuilder()
        val firstFailure = try {
            return builder.build(StringReader(escaped))
        } catch (e: JDOMException) {
            e
        }
        AppLogger.d("$TAG retrying with upper cased tag names after: ${firstFailure.message}")
        return try {
            builder.build(StringReader(upperCaseTagNames(escaped)))
        } catch (e: JDOMException) {
            AppLogger.e("$TAG can not parse playlist", e)
            null
        }
    }

    /**
     * Adds the stream an `ENTRY` names. Later `REF`s are the fallbacks ASX defines for a player
     * that cannot open the first, and only the first is kept. An entry with no `REF` names nothing
     * and is dropped.
     */
    private fun buildPlaylistEntry(entry: Element, playlist: Playlist) {
        val playlistEntry = PlaylistEntry()
        var hasRef = false
        for (child in entry.children) {
            when (val name = child.name.uppercase(Locale.ROOT)) {
                REF_ELEMENT -> if (!hasRef) {
                    playlistEntry[PlaylistEntry.URI] = hrefOf(child)
                    hasRef = true
                }
                TITLE_ELEMENT -> playlistEntry[PlaylistEntry.PLAYLIST_METADATA] = child.value.trim()
                else -> AppLogger.d("$TAG skipping entry element '$name'")
            }
        }
        if (!hasRef) {
            AppLogger.w("$TAG dropping an entry without a REF")
            return
        }
        mNumberOfFiles += 1
        playlistEntry[PlaylistEntry.TRACK] = mNumberOfFiles.toString()
        parseEntry(playlistEntry, playlist)
    }

    /**
     * The address a `REF` or `ENTRYREF` names: its `href` attribute in any case, or failing that
     * the element's text.
     */
    private fun hrefOf(element: Element): String {
        val attribute = element.attributes.firstOrNull {
            it.name.equals(HREF_ATTRIBUTE, ignoreCase = true)
        }
        return (attribute?.value ?: element.value).trim()
    }

    companion object {
        const val EXTENSION = ".asx"
        private const val TAG = "ASXPlaylistParser"
        private const val ENTRY_ELEMENT = "ENTRY"
        private const val ENTRYREF_ELEMENT = "ENTRYREF"
        private const val REF_ELEMENT = "REF"
        private const val TITLE_ELEMENT = "TITLE"
        private const val HREF_ATTRIBUTE = "href"

        /**
         * What real files put before the root element and XML allows nowhere before a declaration:
         * a byte order mark, which a reader decodes as a character, and whitespace.
         */
        private val LEADING_NOISE = charArrayOf('\uFEFF', ' ', '\t', '\r', '\n')

        /**
         * An ampersand that does not start one of the references XML predefines. Custom entity
         * references are escaped too, so no entity a downloaded playlist declares is expanded.
         */
        private val BARE_AMPERSAND = Regex("&(?!(?:amp|lt|gt|quot|apos|#[0-9]+|#x[0-9a-fA-F]+);)")

        /**
         * CDATA is text to the XML parser already, so an ampersand inside it is left alone.
         */
        private val CDATA_SECTION = Regex("<!\\[CDATA\\[.*?]]>", RegexOption.DOT_MATCHES_ALL)

        private fun escapeBareAmpersands(xml: String): String {
            val escaped = StringBuilder(xml.length)
            var start = 0
            for (section in CDATA_SECTION.findAll(xml)) {
                escaped.append(xml.substring(start, section.range.first).replace(BARE_AMPERSAND, "&amp;"))
                escaped.append(section.value)
                start = section.range.last + 1
            }
            escaped.append(xml.substring(start).replace(BARE_AMPERSAND, "&amp;"))
            return escaped.toString()
        }

        private val TAG_NAME = Regex("<(/?)([A-Za-z][\\w.:-]*)")

        private fun upperCaseTagNames(xml: String): String {
            return TAG_NAME.replace(xml) {
                "<" + it.groupValues[1] + it.groupValues[2].uppercase(Locale.ROOT)
            }
        }
    }
}
