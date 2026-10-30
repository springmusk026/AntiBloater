package com.spring.musk.antibloater.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Finds the ports wireless debugging advertises over mDNS, the same way Shizuku does:
 *   _adb-tls-pairing._tcp – while "Pair device with pairing code" is open
 *   _adb-tls-connect._tcp – while wireless debugging is on
 * Only services on this phone's own addresses count, so another phone on the
 * same Wi-Fi is never picked up.
 */
object Discovery {
    const val PAIRING = "_adb-tls-pairing._tcp"
    const val CONNECT = "_adb-tls-connect._tcp"

    /** Emits the port while the service is advertised, and null when it goes away. */
    fun ports(context: Context, type: String): Flow<Int?> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val found = mutableMapOf<String, Int>()
        val executor = Executors.newSingleThreadExecutor()
        val callbacks = mutableListOf<NsdManager.ServiceInfoCallback>()

        fun publish() { trySend(found.values.lastOrNull()) }

        fun resolved(service: NsdServiceInfo) {
            val host = hostOf(service) ?: return
            if (!isThisDevice(host)) return
            synchronized(found) { found[service.serviceName] = service.port; publish() }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= 34) {
                    val callback = object : NsdManager.ServiceInfoCallback {
                        override fun onServiceUpdated(info: NsdServiceInfo) = resolved(info)
                        override fun onServiceLost() {}
                        override fun onServiceInfoCallbackUnregistered() {}
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                    }
                    synchronized(callbacks) { callbacks += callback }
                    nsd.registerServiceInfoCallback(service, executor, callback)
                } else {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(info: NsdServiceInfo) = resolved(info)
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                synchronized(found) { found.remove(service.serviceName); publish() }
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { trySend(null) }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        trySend(null)
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            if (Build.VERSION.SDK_INT >= 34) synchronized(callbacks) {
                callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
            }
            executor.shutdown()
        }
    }.distinctUntilChanged()

    private fun hostOf(info: NsdServiceInfo): InetAddress? =
        if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull()
        else @Suppress("DEPRECATION") info.host

    private fun isThisDevice(address: InetAddress): Boolean =
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .any { it.hostAddress == address.hostAddress }
        }.getOrDefault(false)
}
