package com.yuriy.openradio.shared.model.net

import android.content.Context

interface NetworkLayer {

    fun startMonitor(context: Context, listener: NetworkMonitorListener)

    fun stopMonitor(context: Context)

    fun checkConnectivityAndNotify(context: Context): Boolean

    /**
     * Whether a network is connected or connecting. Unlike [checkConnectivityAndNotify] it tells
     * the user nothing, which suits work the user did not ask for, such as loading artwork.
     */
    fun isConnected(): Boolean

    fun isMobileNetwork(): Boolean
}
