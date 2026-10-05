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

package wseemann.media.jplaylistparser.parser

import com.yuriy.openradio.shared.extentions.equalsIgnoreCase
import com.yuriy.openradio.shared.utils.AppLogger
import com.yuriy.openradio.shared.utils.AppUtils
import wseemann.media.jplaylistparser.exception.JPlaylistParserException
import wseemann.media.jplaylistparser.mime.MediaType
import wseemann.media.jplaylistparser.parser.asx.ASXPlaylistParser
import wseemann.media.jplaylistparser.parser.m3u.M3UPlaylistParser
import wseemann.media.jplaylistparser.parser.m3u8.M3U8PlaylistParser
import wseemann.media.jplaylistparser.parser.pls.PLSPlaylistParser
import wseemann.media.jplaylistparser.parser.xspf.XSPFPlaylistParser
import wseemann.media.jplaylistparser.playlist.Playlist
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Picks the parser for a playlist, and follows the playlists that playlist names.
 *
 * One instance answers one resolution. It holds the urls already followed and the depth of the
 * reference chain, and every parser it dispatches to reports back to it, so the limits hold for the
 * whole tree rather than for one parser. Construct a new instance for every playlist.
 *
 * ```
 *   parse(url, mimeType, stream)                       depth 0, url counts as followed
 *     │ dispatch on MIME type and extension, then content
 *     ▼
 *   parser ── entry with a playlist extension, or an ASX ENTRYREF
 *     │ except an address whose path ends in .m3u8, which is kept as a stream
 *     ▼
 *   follow(url) ── already followed, or depth limit ──► skipped
 *     │ fetcher.fetch(url)
 *     ▼
 *   child session, depth + 1 ── dispatch on extension, then content ──► parser ── …
 * ```
 *
 * Nothing here opens a connection: every read beyond the stream handed to [parse] goes through
 * [PlaylistFetcher].
 */
class AutoDetectParser private constructor(
    private val mFetcher: PlaylistFetcher,
    private val mDepth: Int,
    private val mFollowed: MutableSet<String>
) {

    /**
     * @param fetcher Reads every playlist that the parsed one names.
     */
    constructor(fetcher: PlaylistFetcher) : this(fetcher, 0, HashSet())

    /**
     * Parses a playlist that the caller has already opened.
     *
     * @param url Address [stream] was read from. It counts as followed, so a playlist that names
     * itself is not read twice.
     * @param mimeType Content type the server declared for [stream], with or without parameters.
     * @param stream Content of the playlist.
     * @param playlist Receives every stream the playlist resolves to.
     * @throws JPlaylistParserException When neither the MIME type, the extension nor the content
     * identifies a supported format.
     */
    @Throws(IOException::class, JPlaylistParserException::class)
    fun parse(url: String, mimeType: String?, stream: InputStream, playlist: Playlist) {
        mFollowed.add(url)
        val declaredType = MediaType.parse(mimeType.orEmpty().substringBefore(';'))
        val content = BufferedInputStream(stream)
        val parser = parserFor(getFileExtension(url), declaredType)
            ?: parserForContent(peek(content))
            ?: throw JPlaylistParserException("Unsupported format:$url")
        AppLogger.d("$TAG parsing $url (type '$mimeType') with ${parser.javaClass.simpleName}")
        parser.parse(url, content, playlist)
    }

    /**
     * @return Whether an entry naming [url] is read as a playlist rather than kept as a stream:
     * its extension is one this class dispatches on, and it is not HLS, see [isHlsUrl].
     */
    internal fun isFollowedEntryUrl(url: String): Boolean {
        val extension = getFileExtension(url)
        return !isHlsUrl(url) && PLAYLIST_EXTENSIONS.any { extension.equalsIgnoreCase(it) }
    }

    /**
     * @return Whether [url] names an HLS playlist, which a playlist referring to it hands to the
     * player as a stream instead of reading it. The player reads HLS itself, while reading it here
     * would turn a master playlist into its variants and a media playlist into its segments, and
     * the station would then play one segment instead of the live stream.
     */
    internal fun isHlsUrl(url: String): Boolean {
        return getFileExtension(url).equalsIgnoreCase(M3U8PlaylistParser.EXTENSION)
    }

    /**
     * Reads the playlist at [url] through the fetcher and adds what it names to [playlist].
     *
     * A url already followed in this resolution, a chain deeper than [MAX_DEPTH], content that
     * cannot be read and content in no supported format all add nothing. Each is logged, because
     * the caller has no way to tell them apart from an empty playlist.
     */
    internal fun follow(url: String, playlist: Playlist) {
        if (url.isBlank()) {
            AppLogger.w("$TAG not following an empty reference")
            return
        }
        if (mDepth >= MAX_DEPTH) {
            AppLogger.w("$TAG not following $url, the reference chain is already $mDepth deep")
            return
        }
        if (!mFollowed.add(url)) {
            AppLogger.w("$TAG not following $url again, this playlist has already read it")
            return
        }
        val content = mFetcher.fetch(url)
        if (content.isEmpty()) {
            AppLogger.w("$TAG nothing could be read from $url")
            return
        }
        val child = AutoDetectParser(mFetcher, mDepth + 1, mFollowed)
        val parser = child.parserFor(getFileExtension(url), null)
            ?: child.parserForContent(head(content))
        if (parser == null) {
            AppLogger.w("$TAG $url is in no supported playlist format")
            return
        }
        AppLogger.d("$TAG following $url at depth ${mDepth + 1} with ${parser.javaClass.simpleName}")
        try {
            parser.parse(url, ByteArrayInputStream(content), playlist)
        } catch (e: IOException) {
            AppLogger.e("$TAG can not parse $url", e)
        } catch (e: JPlaylistParserException) {
            AppLogger.e("$TAG can not parse $url", e)
        }
    }

    /**
     * Keeps the precedence the parser has always had: the M3U family first, then PLS, XSPF and
     * ASX, each matched on either its extension or its MIME type.
     */
    private fun parserFor(extension: String, mimeType: MediaType?): Parser? {
        val m3u = M3UPlaylistParser(this)
        val m3u8 = M3U8PlaylistParser(this)
        val pls = PLSPlaylistParser(this)
        val xspf = XSPFPlaylistParser(this)
        val asx = ASXPlaylistParser(this)
        return when {
            extension.equalsIgnoreCase(M3UPlaylistParser.EXTENSION)
                    || m3u.accepts(mimeType)
                    && !extension.equalsIgnoreCase(M3U8PlaylistParser.EXTENSION) -> m3u
            extension.equalsIgnoreCase(M3U8PlaylistParser.EXTENSION)
                    || m3u8.accepts(mimeType) -> m3u8
            extension.equalsIgnoreCase(PLSPlaylistParser.EXTENSION)
                    || pls.accepts(mimeType) -> pls
            extension.equalsIgnoreCase(XSPFPlaylistParser.EXTENSION)
                    || xspf.accepts(mimeType) -> xspf
            extension.equalsIgnoreCase(ASXPlaylistParser.EXTENSION)
                    || asx.accepts(mimeType) -> asx
            else -> null
        }
    }

    /**
     * Recognises a playlist by how it starts, for a url that has no playlist extension and a
     * response whose type is unknown. A plain M3U without its `#EXTM3U` header has no signature
     * and is not recognised.
     */
    private fun parserForContent(head: String): Parser? {
        val text = head.trimStart('﻿', ' ', '\t', '\r', '\n')
        return when {
            text.startsWith(M3U_SIGNATURE, ignoreCase = true) ->
                if (text.contains(HLS_TAG_SIGNATURE, ignoreCase = true)) {
                    M3U8PlaylistParser(this)
                } else {
                    M3UPlaylistParser(this)
                }
            text.startsWith(PLS_SIGNATURE, ignoreCase = true) -> PLSPlaylistParser(this)
            else -> when (rootElementName(text)) {
                ASX_ROOT_ELEMENT -> ASXPlaylistParser(this)
                XSPF_ROOT_ELEMENT -> XSPFPlaylistParser(this)
                else -> null
            }
        }
    }

    /**
     * The extension of the last path segment of [uri], from its last dot, in the case it was
     * written in. The query and the fragment are not part of it, so a dot in either cannot hide
     * the extension. A known playlist extension followed by more text, such as
     * `.pls;jsessionid=abc`, is cut back to that extension, so a path parameter does not hide it.
     *
     * @return The extension with its dot, or an empty string when the last segment has no dot.
     */
    fun getFileExtension(uri: String): String {
        val lastSegment = uri.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val dot = lastSegment.lastIndexOf('.')
        if (dot < 0) {
            return AppUtils.EMPTY_STRING
        }
        val extension = lastSegment.substring(dot)
        // PLAYLIST_EXTENSIONS lists .m3u8 before .m3u, which is a prefix of it.
        val known = PLAYLIST_EXTENSIONS.firstOrNull { extension.startsWith(it, ignoreCase = true) }
        return if (known == null) extension else extension.substring(0, known.length)
    }

    companion object {

        private const val TAG = "AutoDetectParser"

        /**
         * Longest chain of playlists naming playlists that one resolution follows. The followed
         * set already stops every cycle; this stops a server that answers each read with a
         * reference to a new address.
         */
        const val MAX_DEPTH = 5

        /**
         * How much of a playlist is read to recognise its format. It is also what is read from a
         * top level stream that turns out to be audio before it is rejected.
         */
        private const val SNIFF_LENGTH = 1024

        private const val M3U_SIGNATURE = "#EXTM3U"
        private const val HLS_TAG_SIGNATURE = "#EXT-X-"
        private const val PLS_SIGNATURE = "[playlist]"
        private const val ASX_ROOT_ELEMENT = "ASX"
        private const val XSPF_ROOT_ELEMENT = "PLAYLIST"

        private val PLAYLIST_EXTENSIONS = listOf(
            M3U8PlaylistParser.EXTENSION,
            M3UPlaylistParser.EXTENSION,
            PLSPlaylistParser.EXTENSION,
            XSPFPlaylistParser.EXTENSION,
            ASXPlaylistParser.EXTENSION
        )

        /**
         * The name of the first element, past an XML declaration, comments, whitespace and a
         * doctype, whose internal subset carries the `>` of its own declarations.
         */
        private val ROOT_ELEMENT = Regex(
            "^(?:<\\?.*?\\?>|<!--.*?-->|<!DOCTYPE(?:[^>\\[]|\\[[^\\]]*\\])*>|\\s)*<([A-Za-z][\\w.:-]*)",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        /**
         * A missing or unparseable type matches nothing. A parser whose own type failed to parse
         * would otherwise claim every response that declared none.
         */
        private fun Parser.accepts(mimeType: MediaType?): Boolean {
            return mimeType != null && supportedTypes.contains(mimeType)
        }

        private fun rootElementName(text: String): String? {
            return ROOT_ELEMENT.find(text)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        }

        private fun head(content: ByteArray): String {
            return String(content, 0, minOf(content.size, SNIFF_LENGTH), Charsets.UTF_8)
        }

        private fun peek(stream: BufferedInputStream): String {
            stream.mark(SNIFF_LENGTH)
            val buffer = ByteArray(SNIFF_LENGTH)
            var length = 0
            while (length < SNIFF_LENGTH) {
                val read = stream.read(buffer, length, SNIFF_LENGTH - length)
                if (read < 0) {
                    break
                }
                length += read
            }
            stream.reset()
            return String(buffer, 0, length, Charsets.UTF_8)
        }
    }
}
