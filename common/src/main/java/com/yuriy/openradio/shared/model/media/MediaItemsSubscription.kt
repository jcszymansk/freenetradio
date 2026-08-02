package com.yuriy.openradio.shared.model.media

import androidx.media3.common.MediaItem

interface MediaItemsSubscription {

    fun onChildrenLoaded(parentId: String, children: List<MediaItem>, replace: Boolean)

    fun onError(parentId: String)
}
