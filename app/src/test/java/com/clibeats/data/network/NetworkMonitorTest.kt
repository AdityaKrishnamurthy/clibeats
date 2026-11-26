package com.clibeats.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NetworkMonitorTest {
    private val context: Context = mock()
    private val connectivityManager: ConnectivityManager = mock()

    private fun <T> stubInstance(clazz: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        @Suppress("UNCHECKED_CAST")
        return allocate.invoke(unsafe, clazz) as T
    }

    @Test
    fun `initial state reflects validated active network`() {
        val network = stubInstance(Network::class.java)
        val caps = stubInstance(NetworkCapabilities::class.java)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(network)).thenReturn(caps)

        val monitor = NetworkMonitor(context) { true }

        assertThat(monitor.isOnline.value).isTrue()
    }

    @Test
    fun `initial state is false for unvalidated captive portal and updates via callback`() {
        val network = stubInstance(Network::class.java)
        val validatedCaps = stubInstance(NetworkCapabilities::class.java)
        val captivePortalCaps = stubInstance(NetworkCapabilities::class.java)
        whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
        whenever(connectivityManager.activeNetwork).thenReturn(network)
        whenever(connectivityManager.getNetworkCapabilities(network)).thenReturn(captivePortalCaps)

        val monitor = NetworkMonitor(context) { caps -> caps === validatedCaps }
        assertThat(monitor.isOnline.value).isFalse()

        val callbackCaptor = argumentCaptor<ConnectivityManager.NetworkCallback>()
        verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())

        callbackCaptor.firstValue.onCapabilitiesChanged(network, validatedCaps)
        assertThat(monitor.isOnline.value).isTrue()

        callbackCaptor.firstValue.onCapabilitiesChanged(network, captivePortalCaps)
        assertThat(monitor.isOnline.value).isFalse()

        callbackCaptor.firstValue.onCapabilitiesChanged(network, validatedCaps)
        assertThat(monitor.isOnline.value).isTrue()

        callbackCaptor.firstValue.onLost(network)
        assertThat(monitor.isOnline.value).isFalse()
    }
}
