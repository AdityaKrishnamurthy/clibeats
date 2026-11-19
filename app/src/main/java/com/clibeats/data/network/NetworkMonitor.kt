package com.clibeats.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private val _isOnline = MutableStateFlow(checkInitialOnline())
        val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

        init {
            // Follow the default network: "online" means the system has verified that the internet
            // actually works (VALIDATED), so captive portals and Wi-Fi without internet count as offline.
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        _isOnline.value = capabilities.hasWorkingInternet()
                    }

                    override fun onLost(network: Network) {
                        _isOnline.value = false
                    }
                },
            )
        }

        private fun checkInitialOnline(): Boolean {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            return capabilities?.hasWorkingInternet() == true
        }

        private fun NetworkCapabilities.hasWorkingInternet(): Boolean =
            hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
