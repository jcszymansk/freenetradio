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

import org.jdom2.input.SAXBuilder
import org.xml.sax.InputSource
import wseemann.media.jplaylistparser.playlist.Playlist
import wseemann.media.jplaylistparser.playlist.PlaylistEntry
import java.io.StringReader

/**
 * @param mSession The resolution this parser takes part in; every playlist it names is followed
 * through it.
 */
abstract class AbstractParser(private val mSession: AutoDetectParser) : Parser {

    /**
     * Adds [playlistEntry] to [playlist] as a stream, or, when its url has a playlist extension,
     * adds what that playlist names instead.
     */
    protected fun parseEntry(playlistEntry: PlaylistEntry, playlist: Playlist) {
        val uri = playlistEntry[PlaylistEntry.URI]
        if (mSession.isPlaylistUrl(uri)) {
            mSession.follow(uri, playlist)
        } else {
            playlist.add(playlistEntry)
        }
    }

    /**
     * Adds what the playlist at [url] names to [playlist], whatever its extension. For a reference
     * the playlist format declares to be a playlist, such as an ASX `ENTRYREF`.
     */
    protected fun follow(url: String, playlist: Playlist) {
        mSession.follow(url, playlist)
    }

    /**
     * A builder for playlist XML that never reads an external DTD or entity. The SAX parser would
     * otherwise open the address a downloaded playlist declares, which is a connection
     * [PlaylistFetcher] never sees.
     */
    protected fun newXmlBuilder(): SAXBuilder {
        val builder = SAXBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        return builder
    }
}
