package org.omashare.app.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import java.util.ArrayDeque

/** Native DNS-SD (nearby) feeding Iroh EndpointIds into the core. */
class NsdDiscovery(
    context: Context,
    private val onPeer: (alias: String, endpointId: String, host: String, port: Int) -> Unit,
) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val pendingResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var lastAlias: String? = null
    private var lastEid: String? = null
    private var lastPort: Int = 53318

    fun advertise(alias: String, endpointId: String, port: Int) {
        lastAlias = alias
        lastEid = endpointId
        lastPort = port
        val info = NsdServiceInfo().apply {
            serviceName = "${alias.take(24)}-${endpointId.take(8)}"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute("eid", endpointId.take(63))
            setAttribute("alias", alias.take(63))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "nsd registered ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "nsd register failed $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "nsd register: ${e.message}")
        }
    }

    fun browse() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "nsd browse start failed $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                enqueueResolve(serviceInfo)
            }
        }
        discovery = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "nsd browse: ${e.message}")
        }
    }

    fun restart(alias: String? = lastAlias, endpointId: String? = lastEid, port: Int = lastPort) {
        stop()
        val a = alias ?: return
        val e = endpointId ?: return
        advertise(a, e, port)
        browse()
    }

    fun stop() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        synchronized(pendingResolve) {
            pendingResolve.clear()
            resolving = false
        }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(pendingResolve) {
            pendingResolve.addLast(info)
        }
        pumpResolve()
    }

    private fun pumpResolve() {
        val next: NsdServiceInfo
        synchronized(pendingResolve) {
            if (resolving || pendingResolve.isEmpty()) return
            next = pendingResolve.removeFirst()
            resolving = true
        }
        try {
            nsd.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "nsd resolve failed $errorCode")
                        doneResolve()
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val attrs = if (Build.VERSION.SDK_INT >= 21) {
                            serviceInfo.attributes
                        } else {
                            emptyMap()
                        }
                        val eid = attrs["eid"]?.toString(Charsets.UTF_8)
                            ?: serviceInfo.serviceName
                        val alias = attrs["alias"]?.toString(Charsets.UTF_8)
                            ?: serviceInfo.serviceName
                        val host = serviceInfo.host?.hostAddress
                        if (host != null) {
                            onPeer(alias, eid, host, serviceInfo.port)
                        }
                        doneResolve()
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "nsd resolve: ${e.message}")
            doneResolve()
        }
    }

    private fun doneResolve() {
        synchronized(pendingResolve) {
            resolving = false
        }
        pumpResolve()
    }

    companion object {
        private const val TAG = "OmashareNsd"
        const val SERVICE_TYPE = "_omashare._udp."
    }
}
