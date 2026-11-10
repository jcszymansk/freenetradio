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

package com.yuriy.openradio.mobile.view.main

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jcszymansk.console.design.Console
import com.github.jcszymansk.console.design.ConsoleDimens
import com.github.jcszymansk.console.design.ConsoleShapes
import com.github.jcszymansk.console.design.InitialsTile
import com.github.jcszymansk.console.design.bottomHairline
import com.yuriy.openradio.mobile.R
import com.yuriy.openradio.shared.presenter.main.RowState
import com.yuriy.openradio.shared.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * A station: tile or artwork, name, `CC · CODEC`, bitrate and heart. The current station's row
 * has the active tile and the raised background. Tapping plays; the heart only toggles the
 * favourite; long-press exists only where [RowState.Station.canLongPress].
 */
@Composable
internal fun StationRow(row: RowState.Station, actions: MainScreenActions) {
    val c = Console.colors
    val base = Modifier
        .fillMaxWidth()
        .height(ConsoleDimens.listRowHeight)
    val tinted = if (row.isCurrent) base.background(c.surface1) else base
    Row(
        tinted
            .bottomHairline(c.dividerRow)
            .combinedClickable(
                onClick = { actions.onRowClick(row.id) },
                onLongClick = if (row.canLongPress) {
                    { actions.onRowLongClick(row.id) }
                } else {
                    null
                },
            )
            .padding(start = 16.dp, end = 6.dp)
            .testTag(MainScreenTags.row(row.id)),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationTile(row)
        Column(Modifier.weight(1f)) {
            Text(
                row.name,
                style = Console.type.bodyStrong,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(MainScreenTags.rowTitle(row.id)),
            )
            if (row.meta.isNotEmpty()) {
                Text(
                    row.meta,
                    style = Console.type.dataSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        row.bitrate?.let { bitrate ->
            Text(
                bitrate,
                style = Console.type.dataSmall,
                color = c.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
        }
        Box(
            Modifier
                .size(ConsoleDimens.touchTarget)
                .toggleable(value = row.isFavorite, role = Role.Checkbox, onValueChange = { actions.onFavoriteClick(row.id) })
                .testTag(MainScreenTags.favorite(row.id)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (row.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(
                    if (row.isFavorite) R.string.main_favorite_remove else R.string.main_favorite_add
                ),
                tint = if (row.isFavorite) c.accent else c.iconMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StationTile(row: RowState.Station) {
    val artwork = rememberArtwork(row.artworkUri, row.artworkRevision)
    if (artwork != null) {
        Image(
            artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(ConsoleDimens.tileSize).clip(ConsoleShapes.small),
        )
    } else {
        InitialsTile(row.initials, active = row.isCurrent)
    }
}

/**
 * Loads the artwork at [uri] from the images provider, again whenever [revision] changes, which
 * is how artwork downloaded after the row appeared reaches it.
 */
@Composable
private fun rememberArtwork(uri: String?, revision: Int): ImageBitmap? {
    val context = LocalContext.current
    val artwork by produceState<ImageBitmap?>(initialValue = null, uri, revision) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { readArtwork(context, uri) }
    }
    return artwork
}

private fun readArtwork(context: Context, uri: String): ImageBitmap? {
    return try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (failure: IOException) {
        AppLogger.w("StationRow artwork $uri not read: $failure")
        null
    } catch (failure: SecurityException) {
        AppLogger.w("StationRow artwork $uri not readable: $failure")
        null
    }
}
