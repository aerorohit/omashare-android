package org.omashare.app.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID

/**
 * Advertise Omashare Presence over BLE GATT and scan for peers.
 * Handshake only — transfers stay on iroh (LAN / WAN / relay).
 */
class BleDiscovery(
    context: Context,
    private val onPeer: (
        alias: String,
        endpointId: String,
        addrs: List<String>,
        pinRequired: Boolean,
        deviceKind: String,
    ) -> Unit,
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = manager?.adapter
    private var gattServer: BluetoothGattServer? = null
    private var payload = ByteArray(0)
    private var scannerStarted = false
    private var advertising = false
    private val pending = ArrayDeque<BluetoothDevice>()
    private var activeGatt: BluetoothGatt? = null
    private val recently = HashMap<String, Long>()

    @Volatile
    var running = false
        private set

    @SuppressLint("MissingPermission")
    fun setPayload(json: JSONObject) {
        payload = json.toString().toByteArray(Charsets.UTF_8)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val adapter = adapter ?: return
        if (!adapter.isEnabled) {
            Log.w(TAG, "bluetooth off")
            return
        }
        running = true
        startServer()
        startAdvertise()
        startScan()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        running = false
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCb) }
        advertising = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCb) }
        scannerStarted = false
        synchronized(pending) { pending.clear() }
        runCatching { activeGatt?.disconnect() }
        runCatching { activeGatt?.close() }
        activeGatt = null
        runCatching { gattServer?.close() }
        gattServer = null
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (gattServer != null) return
        val mgr = manager ?: return
        gattServer = mgr.openGattServer(app, serverCb) ?: return
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val ch = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(ch)
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertise() {
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        if (advertising) return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        try {
            advertiser.startAdvertising(settings, data, advertiseCb)
        } catch (e: Exception) {
            Log.w(TAG, "advertise: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (scannerStarted) return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, scanCb)
            scannerStarted = true
        } catch (e: Exception) {
            Log.w(TAG, "scan: ${e.message}")
        }
    }

    private val advertiseCb = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
            Log.i(TAG, "advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            Log.w(TAG, "advertise failed $errorCode")
        }
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            enqueue(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed $errorCode")
            scannerStarted = false
        }
    }

    private val serverCb = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != CHAR_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }
            val slice = when {
                offset >= payload.size -> ByteArray(0)
                else -> payload.copyOfRange(offset, payload.size)
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enqueue(device: BluetoothDevice) {
        val id = device.address ?: return
        val now = System.currentTimeMillis()
        synchronized(recently) {
            val prev = recently[id]
            if (prev != null && now - prev < REREAD_MS) return
            recently[id] = now
        }
        synchronized(pending) {
            if (pending.any { it.address == id }) return
            if (activeGatt?.device?.address == id) return
            pending.addLast(device)
        }
        pump()
    }

    @SuppressLint("MissingPermission")
    private fun pump() {
        val next: BluetoothDevice
        synchronized(pending) {
            if (activeGatt != null || pending.isEmpty() || !running) return
            next = pending.removeFirst()
        }
        try {
            activeGatt = next.connectGatt(app, false, clientCb, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.w(TAG, "connect: ${e.message}")
            activeGatt = null
            pump()
        }
    }

    @SuppressLint("MissingPermission")
    private fun finishGatt(gatt: BluetoothGatt) {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
        if (activeGatt === gatt) activeGatt = null
        pump()
    }

    private val clientCb = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (!gatt.discoverServices()) finishGatt(gatt)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                finishGatt(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishGatt(gatt)
                return
            }
            val ch = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
            if (ch == null || !gatt.readCharacteristic(ch)) {
                finishGatt(gatt)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            onRead(gatt, characteristic.value, status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            onRead(gatt, value, status)
        }

        private fun onRead(gatt: BluetoothGatt, value: ByteArray?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && value != null) {
                parsePresence(value)
            }
            finishGatt(gatt)
        }
    }

    private fun parsePresence(bytes: ByteArray) {
        try {
            val obj = JSONObject(String(bytes, Charsets.UTF_8))
            val eid = obj.optString("endpoint_id")
            if (eid.isEmpty()) return
            val addrs = mutableListOf<String>()
            val arr = obj.optJSONArray("addrs")
            if (arr != null) {
                for (i in 0 until arr.length()) addrs.add(arr.optString(i))
            }
            onPeer(
                obj.optString("alias").ifEmpty { eid.take(8) },
                eid,
                addrs,
                obj.optBoolean("pin_required"),
                obj.optString("device_kind").ifEmpty { "unknown" },
            )
        } catch (e: Exception) {
            Log.w(TAG, "presence: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "OmashareBle"
        private const val REREAD_MS = 20_000L
        val SERVICE_UUID: UUID = UUID.fromString("6f6d6173-6861-7265-0000-000000005318")
        val CHAR_UUID: UUID = UUID.fromString("6f6d6173-6861-7265-0000-000000005319")
    }
}
