package com.aira.assistant.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors real-time online/offline network state so AIRA displays a clear Offline/Online indicator
 * and seamlessly routes requests to local offline capabilities when disconnected.
 */
class NetworkConnectivityObserver(context: Context? = null) {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        if (context != null) {
            runCatching {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNet = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNet)
                _isOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isOnline.value = true
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = false
                    }
                })
            }
        }
    }

    fun setOnlineForTesting(online: Boolean) {
        _isOnline.value = online
    }
}
