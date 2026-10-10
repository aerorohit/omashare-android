package org.omashare.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONArray
import org.json.JSONObject
import org.omashare.app.OmashareApp
import org.omashare.app.ui.IncomingOfferActivity
import org.omashare.app.ui.MainActivity
import uniffi.omashare.EventSink
import uniffi.omashare.OmashareNode
import java.util.ArrayDeque
import java.util.concurrent.Executors

class ShareService : Service(), EventSink {
    private var node: OmashareNode? = null
    private var nsd: NsdDiscovery? = null
    private var ble: BleDiscovery? = null
    private var cmCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var starting = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private var lastWifi = false
    private var lastLanKick = 0L
    private val io = Executors.newCachedThreadPool { r -> Thread(r, "omashare-io") }
    private val pendingSend = ArrayDeque<String>()
    private var sendPeer: String? = null
    private val outgoing = mutableSetOf<String>()
    private val outgoingPaths = mutableMapOf<String, List<String>>()
    private val incomingFiles = mutableMapOf<String, List<ShareStore.OfferedFile>>()
    private val incomingFrom = mutableMapOf<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // startForegroundService() requires this even when the next step is to stop.
            promoteForeground()
            try {
                teardown()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        promoteForeground()
        acquireMulticast()
        if (node == null && !starting) {
            starting = true
            io.execute { startNode() }
        }
        when (intent?.action) {
            ACTION_ACCEPT -> {
                val sid = intent.getStringExtra(EXTRA_SESSION)
                if (sid != null) {
                    ShareStore.clearIncomingIf(sid)
                    cancelOfferNotification(sid)
                    io.execute {
                        waitForNode()
                        runCatching { node?.acceptOffer(sid) }
                    }
                }
            }
            ACTION_DECLINE -> {
                val sid = intent.getStringExtra(EXTRA_SESSION)
                if (sid != null) {
                    ShareStore.clearIncomingIf(sid)
                    cancelOfferNotification(sid)
                    io.execute {
                        waitForNode()
                        runCatching { node?.declineOffer(sid) }
                    }
                }
            }
            ACTION_CANCEL -> {
                val sid = intent.getStringExtra(EXTRA_SESSION)
                pendingSend.clear()
                sendPeer = null
                ShareStore.setStatus("Cancelling…")
                io.execute {
                    val n = waitForNode()
                    runCatching { n?.cancelTransfer(sid) }
                        .onFailure { ShareStore.setStatus("Cancel failed: ${it.message}") }
                }
            }
            ACTION_SEND -> {
                val peer = intent.getStringExtra(EXTRA_PEER) ?: return START_STICKY
                val paths = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty()
                io.execute { startSend(peer, paths) }
            }
        }
        return START_STICKY
    }

    private fun promoteForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFY_SHARE,
            notification("Omashare is sharing"),
            if (Build.VERSION.SDK_INT >= 29) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                if (Build.VERSION.SDK_INT >= 34) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                types
            } else {
                0
            },
        )
    }

    private fun acquireMulticast() {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        if (multicastLock == null) {
            multicastLock = wifi?.createMulticastLock("omashare")?.apply {
                setReferenceCounted(false)
            }
        }
        val lock = multicastLock ?: return
        if (!lock.isHeld) {
            lock.acquire()
        }
    }

    private fun startNode() {
        try {
            val n = OmashareNode()
            val files = getExternalFilesDir(null) ?: filesDir
            n.start(
                alias = Build.MODEL,
                pin = null,
                receiveDir = files.resolve("inbox").apply { mkdirs() }.absolutePath,
                dataDir = files.resolve("data").apply { mkdirs() }.absolutePath,
                deviceKind = "mobile",
                useRelays = true,
                listener = this,
            )
            node = n
            instance = this
            val eid = n.endpointId()
            nsd = NsdDiscovery(this) { alias, peerEid, host, port ->
                io.execute { injectFromLan(n, alias, peerEid, host, port) }
            }.also {
                it.advertise(Build.MODEL, eid, 53318)
                it.browse()
            }
            ble = BleDiscovery(this) { alias, peerEid, addrs, pin, kind ->
                io.execute {
                    n.injectPeer(alias, peerEid, addrs, pin, "bluetooth", kind)
                }
            }.also { disc ->
                disc.setPayload(localPresenceJson(eid))
                disc.start()
            }
            lastWifi = true
            watchNetwork(n)
            val msg = "Omashare is sharing"
            startForeground(NOTIFY_SHARE, notification(msg))
            publishSharing(true)
            ShareStore.setStatus("Listening as ${Build.MODEL}")
        } catch (e: Exception) {
            val msg = "Node failed: ${e.message}"
            startForeground(NOTIFY_SHARE, notification(msg))
            publishSharing(false)
            ShareStore.setStatus(msg)
        } finally {
            starting = false
        }
    }

    private fun waitForNode(): OmashareNode? {
        repeat(50) {
            node?.let { return it }
            Thread.sleep(100)
        }
        return node
    }

    private fun startSend(peerId: String, paths: List<String>) {
        val n = waitForNode()
        if (n == null) {
            ShareStore.setStatus("Node not ready — wait a second and tap again")
            return
        }
        if (paths.isEmpty()) {
            ShareStore.setStatus("Choose files first")
            return
        }
        pendingSend.clear()
        sendPeer = peerId
        val peerAlias = ShareStore.state.value.peers.find { it.endpointId == peerId }?.alias
        ShareStore.trackOutgoing(paths, peerAlias)
        val rest = trySendBatch(n, peerId, paths)
        pendingSend.addAll(rest)
    }

    private fun trySendBatch(n: OmashareNode, peerId: String, paths: List<String>): List<String> {
        ShareStore.setStatus("Hashing…")
        return try {
            val sid = n.sendFiles(peerId, paths, null)
            outgoing.add(sid)
            outgoingPaths[sid] = paths
            val names = paths.map { java.io.File(it).name }
            ShareStore.setTransfer(
                ShareStore.TransferUi(sid, 0, 0, "", inbound = false, fileNames = names),
            )
            ShareStore.setStatus("Hashing…")
            emptyList()
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            ShareStore.setTransfer(null)
            if (msg.contains("cancelled", ignoreCase = true)) {
                ShareStore.setStatus("Cancelled")
            } else {
                ShareStore.setStatus("Send failed: ${e.message}")
                paths.forEach { path ->
                    ShareStore.addHistory(
                        historyEntry(
                            id = "${System.currentTimeMillis()}-${path.hashCode()}",
                            fileName = java.io.File(path).name,
                            direction = HistoryEntry.Direction.Sent,
                            status = HistoryEntry.Status.Failed,
                            openPath = path,
                            peerAlias = ShareStore.lastOutgoingPeerAlias(),
                        ),
                    )
                }
            }
            emptyList()
        }
    }

    private fun watchNetwork(n: OmashareNode) {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network) ?: return
                onCaps(n, caps)
            }
            override fun onLost(network: Network) {
                if (!wifiStillUp()) {
                    lastWifi = false
                    io.execute { onWifiDown(n) }
                }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                onCaps(n, caps)
            }
        }
        cmCallback = cb
        cm.registerDefaultNetworkCallback(cb)
    }

    private fun onCaps(n: OmashareNode, caps: NetworkCapabilities) {
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        n.setNetworkHint(
            when {
                wifi -> "wifi"
                metered -> "metered"
                else -> "unknown"
            },
        )
        if (wifi && !lastWifi) {
            lastWifi = true
            io.execute { onWifiUp(n) }
        } else if (!wifi && lastWifi) {
            lastWifi = false
            io.execute { onWifiDown(n) }
        }
    }

    private fun onWifiUp(n: OmashareNode) {
        val now = System.currentTimeMillis()
        if (now - lastLanKick < 2_000) return
        lastLanKick = now
        acquireMulticast()
        nsd?.restart()
        runCatching { n.refreshLan() }
        if (ShareStore.state.value.transfer == null) {
            ShareStore.setStatus("Looking for devices…")
        }
    }

    private fun wifiStillUp(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun onWifiDown(n: OmashareNode) {
        nsd?.stop()
        n.setNetworkHint("unknown")
        refreshPeers()
    }

    override fun onEventJson(json: String) {
        val obj = JSONObject(json)
        val type = obj.optString("type")
        if (type == "transfer_progress") {
            Log.i(
                TAG,
                "$type session=${obj.optString("session_id")} ${obj.optLong("bytes_done")}/${obj.optLong("bytes_total")} path=${obj.optString("path")}",
            )
        } else {
            Log.i(TAG, "$type $json")
        }
        when (type) {
            "incoming_offer" -> {
                val session = obj.optString("session_id")
                val from = obj.optString("from_alias")
                val files = parseFiles(obj)
                incomingFiles[session] = files
                incomingFrom[session] = from
                ShareStore.setIncoming(ShareStore.IncomingOffer(session, from, files))
                notifyOffer(session, from, files)
            }
            "metered_consent_needed" -> {
                val session = obj.optString("session_id")
                io.execute { runCatching { node?.consentMetered(session, true) } }
            }
            "nearby_updated" -> applyNearby(obj.optJSONArray("peers") ?: JSONArray())
            "transfer_progress" -> {
                val session = obj.optString("session_id")
                val inbound = session !in outgoing
                val fileNames = transferFileNames(session, inbound)
                ShareStore.setTransfer(
                    ShareStore.TransferUi(
                        sessionId = session,
                        bytesDone = obj.optLong("bytes_done"),
                        bytesTotal = obj.optLong("bytes_total"),
                        path = obj.optString("path"),
                        inbound = inbound,
                        fileNames = fileNames,
                    ),
                )
            }
            "offer_accepted" -> {
                val session = obj.optString("session_id")
                if (session.isNotEmpty()) {
                    val inbound = session !in outgoing
                    val fileNames = transferFileNames(session, inbound)
                    val cur = ShareStore.state.value.transfer
                    if (cur == null || cur.sessionId != session || cur.bytesTotal == 0L) {
                        ShareStore.setTransfer(
                            ShareStore.TransferUi(session, 0, 0, "", inbound, fileNames),
                        )
                    }
                    ShareStore.setStatus("Accepted — transferring")
                }
            }
            "transfer_complete" -> {
                val session = obj.optString("session_id")
                val savedRaw = obj.optString("saved_to")
                val inbound = savedRaw.isNotEmpty() && savedRaw != "null"
                val fromAlias = incomingFrom[session]
                    ?: ShareStore.state.value.incoming
                        ?.takeIf { it.sessionId == session }
                        ?.fromAlias
                val sentPaths = outgoingPaths[session]
                ShareStore.clearIncomingIf(session)
                cancelOfferNotification(session)
                val more = synchronized(pendingSend) {
                    outgoing.remove(session)
                    outgoingPaths.remove(session)
                    if (pendingSend.isEmpty()) emptyList() else {
                        val next = pendingSend.removeFirst()
                        listOf(next) + pendingSend.toList().also { pendingSend.clear() }
                    }
                }
                ShareStore.setTransfer(null)
                if (inbound) {
                    val fileName = java.io.File(savedRaw).name
                    io.execute {
                        val result = copyToDownloads(savedRaw)
                        val saved = result?.displayName ?: fileName
                        notifyResult("Saved to Downloads/Omashare", saved)
                        toast("Saved $saved")
                        ShareStore.addHistory(
                            historyEntry(
                                id = session,
                                fileName = saved,
                                direction = HistoryEntry.Direction.Received,
                                status = HistoryEntry.Status.Completed,
                                openUri = result?.uri,
                                openPath = result?.path,
                                peerAlias = fromAlias,
                            ),
                        )
                        ShareStore.setStatus("")
                    }
                } else {
                    val paths = sentPaths ?: ShareStore.lastOutgoingPaths()
                    val peerAlias = ShareStore.lastOutgoingPeerAlias()
                    notifyResult("Sent", "File should appear on the other device")
                    toast("Sent")
                    if (paths.isEmpty()) {
                        ShareStore.addHistory(
                            historyEntry(
                                id = session,
                                fileName = "file",
                                direction = HistoryEntry.Direction.Sent,
                                status = HistoryEntry.Status.Completed,
                                peerAlias = peerAlias,
                            ),
                        )
                    } else {
                        paths.forEach { path ->
                            ShareStore.addHistory(
                                historyEntry(
                                    id = "$session-${path.hashCode()}",
                                    fileName = java.io.File(path).name,
                                    direction = HistoryEntry.Direction.Sent,
                                    status = HistoryEntry.Status.Completed,
                                    openPath = path,
                                    peerAlias = peerAlias,
                                ),
                            )
                        }
                    }
                    ShareStore.clearOutgoingTracking()
                    if (more.isEmpty()) {
                        ShareStore.clearAfterOutboundComplete()
                    }
                }
                incomingFiles.remove(session)
                incomingFrom.remove(session)
                if (more.isNotEmpty()) {
                    val peer = sendPeer
                    if (peer != null) io.execute { startSend(peer, more) }
                }
            }
            "transfer_failed" -> {
                val session = obj.optString("session_id")
                val err = obj.optString("error")
                pendingSend.clear()
                sendPeer = null
                outgoing.remove(session)
                val outboundPaths = outgoingPaths.remove(session) ?: emptyList()
                val inboundOffer = incomingFiles.remove(session)
                val fromAlias = incomingFrom.remove(session)
                ShareStore.setTransfer(null)
                ShareStore.clearIncomingIf(session)
                cancelOfferNotification(session)
                val cancelled = err.contains("cancelled", ignoreCase = true)
                if (!cancelled) {
                    val peerAlias = ShareStore.lastOutgoingPeerAlias() ?: fromAlias
                    val files = when {
                        outboundPaths.isNotEmpty() -> outboundPaths.map { java.io.File(it).name }
                        inboundOffer != null -> inboundOffer.map { it.name }
                        else -> listOf("file")
                    }
                    files.forEach { name ->
                        val path = outboundPaths.find { java.io.File(it).name == name }
                        ShareStore.addHistory(
                            historyEntry(
                                id = "$session-${name.hashCode()}",
                                fileName = name,
                                direction = if (outboundPaths.isNotEmpty()) {
                                    HistoryEntry.Direction.Sent
                                } else {
                                    HistoryEntry.Direction.Received
                                },
                                status = HistoryEntry.Status.Failed,
                                openPath = path,
                                peerAlias = peerAlias,
                            ),
                        )
                    }
                    notifyResult("Transfer failed", err)
                    toast("Failed: $err")
                    ShareStore.setStatus("")
                } else {
                    ShareStore.setStatus("Cancelled")
                }
                ShareStore.clearOutgoingTracking()
            }
            "path_changed" -> {
                val path = obj.optString("path")
                val t = ShareStore.state.value.transfer
                if (t != null) {
                    ShareStore.setTransfer(t.copy(path = path))
                }
            }
        }
        sendBroadcast(Intent(ACTION_EVENT).putExtra("json", obj.toString()).setPackage(packageName))
    }

    private fun applyNearby(arr: JSONArray) {
        val byAlias = linkedMapOf<String, ShareStore.Peer>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val addrs = mutableListOf<String>()
            val aa = p.optJSONArray("addrs")
            if (aa != null) {
                for (j in 0 until aa.length()) addrs.add(aa.optString(j))
            }
            val peer = ShareStore.Peer(
                alias = p.optString("alias"),
                endpointId = p.optString("endpoint_id"),
                deviceKind = p.optString("device_kind").ifEmpty { "unknown" },
                addrs = addrs,
                source = p.optString("source"),
            )
            val key = peer.alias.lowercase()
            val prev = byAlias[key]
            // Same rules as core discovery: keep one row per alias, prefer LAN over BLE.
            if (prev == null ||
                prev.endpointId == peer.endpointId ||
                (prev.addrs.isEmpty() && peer.addrs.isNotEmpty()) ||
                (peer.source != "bluetooth" && prev.source == "bluetooth")
            ) {
                byAlias[key] = peer
            }
        }
        ShareStore.setPeers(byAlias.values.toList())
    }

    fun refreshPeers() {
        io.execute {
            val json = node?.nearbyJson() ?: return@execute
            applyNearby(JSONArray(json))
            val n = node
            val disc = ble
            if (n != null && disc != null) {
                disc.setPayload(localPresenceJson(n.endpointId()))
            }
        }
    }

    private fun injectFromLan(
        n: OmashareNode,
        alias: String,
        fallbackEid: String,
        host: String,
        port: Int,
    ) {
        val info = fetchOmashareInfo(host, port) ?: return
        val eid = info.optString("endpoint_id").ifEmpty { fallbackEid }
        if (eid.isEmpty()) return
        val name = info.optString("alias").ifEmpty { alias }
        val addrs = mutableListOf<String>()
        var irohPort: String? = null
        val arr = info.optJSONArray("addrs")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val raw = arr.optString(i)
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    if (!addrs.contains(raw)) addrs.add(raw)
                    continue
                }
                val idx = raw.lastIndexOf(':')
                if (idx > 0) irohPort = raw.substring(idx + 1)
                rewriteDirectAddr(raw, host)?.let { rewritten ->
                    if (!addrs.contains(rewritten)) addrs.add(rewritten)
                }
            }
        }
        nsdHostPort(host, irohPort)?.let { lan ->
            if (!addrs.contains(lan)) addrs.add(0, lan)
        }
        n.injectPeer(
            name,
            eid,
            addrs,
            info.optBoolean("pin_required"),
            "native_dns_sd",
            info.optString("device_kind").ifEmpty { "unknown" },
        )
    }

    private fun nsdHostPort(host: String, irohPort: String?): String? {
        if (host.isEmpty() || irohPort.isNullOrEmpty()) return null
        if (host == "127.0.0.1" || host == "::1") return null
        return if (host.contains(':') && !host.startsWith("[")) {
            "[$host]:$irohPort"
        } else {
            "$host:$irohPort"
        }
    }

    private fun fetchOmashareInfo(host: String, port: Int, attempts: Int = 5): JSONObject? {
        repeat(attempts) { attempt ->
            try {
                val url = java.net.URL("http://$host:$port/omashare/info")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 800
                conn.readTimeout = 800
                conn.requestMethod = "GET"
                return conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            } catch (_: Exception) {
                if (attempt < attempts - 1) Thread.sleep(400)
            }
        }
        return null
    }

    private fun rewriteDirectAddr(addr: String, host: String): String? {
        val idx = addr.lastIndexOf(':')
        if (idx <= 0) return null
        var ip = addr.substring(0, idx)
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length - 1)
        }
        val p = addr.substring(idx + 1)
        val useHost = ip == "0.0.0.0" || ip == "::" || ip == "127.0.0.1" || ip == "::1"
        val outIp = if (useHost) host else ip
        if (outIp == "127.0.0.1" || outIp == "::1") return null
        return "$outIp:$p"
    }

    private fun localPresenceJson(eid: String): JSONObject {
        val info = fetchOmashareInfo("127.0.0.1", 53318, attempts = 1)
        if (info != null && info.optString("endpoint_id").isNotEmpty()) {
            return info
        }
        return JSONObject()
            .put("alias", Build.MODEL)
            .put("device_kind", "mobile")
            .put("endpoint_id", eid)
            .put("pin_required", false)
            .put("addrs", org.json.JSONArray())
    }

    private fun parseFiles(obj: JSONObject): List<ShareStore.OfferedFile> {
        val files = obj.optJSONArray("files") ?: return emptyList()
        val out = mutableListOf<ShareStore.OfferedFile>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            out.add(ShareStore.OfferedFile(f.optString("name", "file"), f.optLong("size")))
        }
        return out
    }

    private fun notifyOffer(session: String, from: String, files: List<ShareStore.OfferedFile>) {
        val names = files.joinToString(", ") { it.name }.ifEmpty { "a file" }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val inForeground = ShareStore.isUiForeground()
        val openIntent = if (inForeground) {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("incoming_session", session)
            }
        } else {
            offerPopupIntent(session, from, files)
        }
        val open = PendingIntent.getActivity(this, session.hashCode(), openIntent, piFlags)
        val accept = PendingIntent.getBroadcast(
            this,
            session.hashCode() + 1,
            Intent(this, OfferActionReceiver::class.java).apply {
                action = OfferActionReceiver.ACTION_ACCEPT
                putExtra(EXTRA_SESSION, session)
            },
            piFlags,
        )
        val decline = PendingIntent.getBroadcast(
            this,
            session.hashCode() + 2,
            Intent(this, OfferActionReceiver::class.java).apply {
                action = OfferActionReceiver.ACTION_DECLINE
                putExtra(EXTRA_SESSION, session)
            },
            piFlags,
        )
        val builder = NotificationCompat.Builder(this, OmashareApp.CHANNEL_OFFERS)
            .setContentTitle("Receive files?")
            .setContentText("$from wants to send $names")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(false)
            .addAction(0, "Accept", accept)
            .addAction(0, "Decline", decline)
        if (!inForeground && canPostFullScreenIntent()) {
            builder.setFullScreenIntent(open, true)
        }
        getSystemService(NotificationManager::class.java).notify(session.hashCode(), builder.build())
        if (!inForeground) {
            val launch = offerPopupIntent(session, from, files)
            Handler(Looper.getMainLooper()).post {
                if (ShareStore.isUiForeground()) return@post
                runCatching { startActivity(launch) }
            }
        }
    }

    private fun offerPopupIntent(
        session: String,
        from: String,
        files: List<ShareStore.OfferedFile>,
    ): Intent {
        val names = ArrayList<String>(files.size)
        val sizes = LongArray(files.size)
        files.forEachIndexed { index, file ->
            names.add(file.name)
            sizes[index] = file.size
        }
        return Intent(this, IncomingOfferActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SESSION, session)
            putExtra(EXTRA_ALIAS, from)
            putStringArrayListExtra(EXTRA_FILE_NAMES, names)
            putExtra(EXTRA_FILE_SIZES, sizes)
        }
    }

    private fun canPostFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    }

    private fun cancelOfferNotification(session: String) {
        getSystemService(NotificationManager::class.java).cancel(session.hashCode())
    }

    private fun notifyResult(title: String, text: String) {
        val n = NotificationCompat.Builder(this, OmashareApp.CHANNEL_RESULTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFY_RESULT, n)
    }

    private fun toast(text: String) {
        android.os.Handler(mainLooper).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun transferFileNames(session: String, inbound: Boolean): List<String> {
        if (inbound) {
            return incomingFiles[session]?.map { it.name }
                ?: ShareStore.state.value.incoming?.files?.map { it.name }
                ?: emptyList()
        }
        return outgoingPaths[session]?.map { java.io.File(it).name }
            ?: ShareStore.state.value.staged.map { it.name }
    }

    private fun historyEntry(
        id: String,
        fileName: String,
        direction: HistoryEntry.Direction,
        status: HistoryEntry.Status,
        openUri: Uri? = null,
        openPath: String? = null,
        peerAlias: String? = null,
    ): HistoryEntry = HistoryEntry(
        id = id,
        fileName = fileName,
        direction = direction,
        status = status,
        timestamp = System.currentTimeMillis(),
        openUri = openUri,
        openPath = openPath,
        peerAlias = peerAlias,
    )

    private data class DownloadResult(
        val displayName: String,
        val uri: Uri?,
        val path: String?,
    )

    private fun copyToDownloads(path: String): DownloadResult? {
        val src = java.io.File(path)
        if (!src.isFile) return null
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, src.name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Omashare")
                }
                val uri = contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return null
                contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                DownloadResult(displayName = src.name, uri = uri, path = null)
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                ).resolve("Omashare")
                dir.mkdirs()
                val dest = dir.resolve(src.name)
                src.copyTo(dest, overwrite = true)
                DownloadResult(displayName = dest.name, uri = null, path = dest.absolutePath)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroy() {
        teardown()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun teardown() {
        nsd?.stop()
        nsd = null
        ble?.stop()
        ble = null
        cmCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        cmCallback = null
        multicastLock?.release()
        multicastLock = null
        runCatching { node?.destroy() }
        node = null
        instance = null
        publishSharing(false)
        ShareStore.setStatus("Sharing stopped")
    }

    private fun publishSharing(on: Boolean) {
        ShareStore.setSharing(on)
        val app = applicationContext
        val refresh = Runnable {
            runCatching {
                TileService.requestListeningState(
                    app,
                    ComponentName(app, ShareTileService::class.java),
                )
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refresh.run()
        } else {
            Handler(Looper.getMainLooper()).post(refresh)
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, OmashareApp.CHANNEL_TRANSFER)
            .setContentTitle("Omashare")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val ACTION_EVENT = "org.omashare.app.EVENT"
        const val ACTION_ACCEPT = "org.omashare.app.service.ACCEPT"
        const val ACTION_DECLINE = "org.omashare.app.service.DECLINE"
        const val ACTION_CANCEL = "org.omashare.app.service.CANCEL"
        const val ACTION_SEND = "org.omashare.app.service.SEND"
        const val ACTION_STOP = "org.omashare.app.service.STOP"
        const val EXTRA_SESSION = "session"
        const val EXTRA_ALIAS = "from_alias"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_FILE_SIZES = "file_sizes"
        const val EXTRA_PEER = "peer"
        const val EXTRA_PATHS = "paths"
        private const val NOTIFY_SHARE = 1
        private const val NOTIFY_RESULT = 2
        private const val TAG = "omashare"

        var instance: ShareService? = null
            private set

        fun node(): OmashareNode? = instance?.node

        fun start(context: android.content.Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, ShareService::class.java),
            )
        }

        fun stop(context: android.content.Context) {
            // A Quick Settings click is a background context. startService() is rejected there,
            // so the stop never arrives and discovery keeps running.
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, ShareService::class.java).setAction(ACTION_STOP),
            )
        }

        fun send(context: android.content.Context, peerId: String, paths: List<String>) {
            val i = Intent(context, ShareService::class.java).apply {
                action = ACTION_SEND
                putExtra(EXTRA_PEER, peerId)
                putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths))
            }
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun accept(context: android.content.Context, session: String) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, ShareService::class.java)
                    .setAction(ACTION_ACCEPT)
                    .putExtra(EXTRA_SESSION, session),
            )
        }

        fun decline(context: android.content.Context, session: String) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, ShareService::class.java)
                    .setAction(ACTION_DECLINE)
                    .putExtra(EXTRA_SESSION, session),
            )
        }

        fun cancel(context: android.content.Context, session: String?) {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, ShareService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_SESSION, session),
            )
        }
    }
}
