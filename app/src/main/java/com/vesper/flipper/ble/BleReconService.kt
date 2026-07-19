package com.vesper.flipper.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE reconnaissance against arbitrary target devices via the phone's own Bluetooth adapter.
 *
 * This is intentionally decoupled from [FlipperBleService]: recon scans arbitrary devices, opens
 * short-lived GATT connections, and disconnects — sharing state with the Flipper's persistent
 * connection would create confusing failure modes. The two services happen to talk to the same
 * system-level [BluetoothAdapter] but manage entirely separate connection pools and callbacks.
 *
 * Runtime permission checks live here rather than at the executor: if a caller lacks
 * BLUETOOTH_SCAN or BLUETOOTH_CONNECT, every method returns [Result.failure] with a
 * user-actionable message rather than throwing. MainActivity handles the request UX at app
 * start; recon assumes the user has already been prompted.
 */
@Singleton
class BleReconService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /**
     * Serializes GATT operations to a single target at a time — the Android BLE stack does not
     * take kindly to overlapping connect/read/write against the same device from the same client.
     */
    private val gattMutex = Mutex()

    data class ScannedDevice(
        val address: String,
        val name: String?,
        val rssi: Int,
        val serviceUuids: List<String>,
        val manufacturerData: Map<Int, ByteArray>,
    )

    data class GattProfile(
        val services: List<GattService>,
    )

    data class GattService(
        val uuid: String,
        val characteristics: List<GattCharacteristic>,
    )

    data class GattCharacteristic(
        val uuid: String,
        val properties: List<String>,
    )

    // ─── Permission gate ──────────────────────────────────────────────────────

    private fun missingBlePermissions(): List<String> {
        val required = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private suspend inline fun <T> guarded(block: suspend () -> Result<T>): Result<T> {
        val missing = missingBlePermissions()
        if (missing.isNotEmpty()) {
            return Result.failure(
                SecurityException(
                    "Missing Bluetooth permission(s): ${missing.joinToString(", ")}. " +
                        "Grant them in Settings → Apps → Vesper → Permissions."
                )
            )
        }
        val a = adapter
            ?: return Result.failure(IllegalStateException("Bluetooth is unavailable on this device."))
        if (!a.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is turned off."))
        }
        return block()
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission") // gated by guarded()
    suspend fun scan(
        durationMs: Long,
        nameFilter: String?,
        rssiThreshold: Int,
    ): Result<List<ScannedDevice>> = guarded {
        val scanner = adapter?.bluetoothLeScanner
            ?: return@guarded Result.failure(IllegalStateException("BLE scanner unavailable."))
        val results = ConcurrentHashMap<String, ScannedDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                mergeResult(results, result, nameFilter, rssiThreshold)
            }

            override fun onBatchScanResults(scanResults: MutableList<ScanResult>) {
                scanResults.forEach { mergeResult(results, it, nameFilter, rssiThreshold) }
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        withContext(Dispatchers.IO) {
            scanner.startScan(null, settings, callback)
            try {
                delay(durationMs.coerceIn(500L, 60_000L))
            } finally {
                scanner.stopScan(callback)
            }
        }
        Result.success(results.values.sortedByDescending { it.rssi })
    }

    private fun mergeResult(
        into: ConcurrentHashMap<String, ScannedDevice>,
        result: ScanResult,
        nameFilter: String?,
        rssiThreshold: Int,
    ) {
        if (result.rssi < rssiThreshold) return
        val record = result.scanRecord
        // Prefer scan-record name; some peripherals only expose it there.
        @SuppressLint("MissingPermission")
        val name = record?.deviceName ?: try {
            result.device.name
        } catch (_: SecurityException) {
            null
        }
        if (!nameFilter.isNullOrEmpty()) {
            val n = name ?: return
            if (!n.contains(nameFilter, ignoreCase = true)) return
        }
        val serviceUuids = record?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        val mfg = mutableMapOf<Int, ByteArray>()
        record?.manufacturerSpecificData?.let { arr ->
            for (i in 0 until arr.size()) {
                mfg[arr.keyAt(i)] = arr.valueAt(i)
            }
        }
        into[result.device.address] = ScannedDevice(
            address = result.device.address,
            name = name,
            rssi = result.rssi,
            serviceUuids = serviceUuids,
            manufacturerData = mfg,
        )
    }

    // ─── GATT ops (one connection per call) ───────────────────────────────────

    suspend fun enumerate(address: String, timeoutMs: Long): Result<GattProfile> =
        withGatt(address, timeoutMs) { gatt ->
            GattProfile(
                services = gatt.services.map { svc ->
                    GattService(
                        uuid = svc.uuid.toString(),
                        characteristics = svc.characteristics.map { ch ->
                            GattCharacteristic(
                                uuid = ch.uuid.toString(),
                                properties = propertyNames(ch.properties),
                            )
                        },
                    )
                },
            )
        }

    suspend fun readCharacteristic(
        address: String,
        uuid: UUID,
        timeoutMs: Long,
    ): Result<ByteArray> = withGatt(address, timeoutMs) { gatt ->
        val (svc, ch) = findCharacteristic(gatt, uuid)
            ?: throw IllegalStateException("Characteristic $uuid not found on $address")
        val deferred = CompletableDeferred<ByteArray>()
        pendingReads[address to ch.uuid] = deferred
        @SuppressLint("MissingPermission")
        val started = gatt.readCharacteristic(ch)
        if (!started) {
            pendingReads.remove(address to ch.uuid)
            throw IllegalStateException("readCharacteristic($uuid) refused by stack (service=$svc)")
        }
        withTimeout(timeoutMs) { deferred.await() }
    }

    suspend fun writeCharacteristic(
        address: String,
        uuid: UUID,
        bytes: ByteArray,
        withResponse: Boolean,
        timeoutMs: Long,
    ): Result<Unit> = withGatt(address, timeoutMs) { gatt ->
        val (_, ch) = findCharacteristic(gatt, uuid)
            ?: throw IllegalStateException("Characteristic $uuid not found on $address")
        val deferred = CompletableDeferred<Boolean>()
        pendingWrites[address to ch.uuid] = deferred
        @SuppressLint("MissingPermission")
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = if (withResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            gatt.writeCharacteristic(ch, bytes, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            ch.writeType = if (withResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
        if (!started) {
            pendingWrites.remove(address to ch.uuid)
            throw IllegalStateException("writeCharacteristic($uuid) refused by stack")
        }
        val ok = withTimeout(timeoutMs) { deferred.await() }
        if (!ok) throw IllegalStateException("writeCharacteristic($uuid) failed")
    }

    suspend fun subscribe(
        address: String,
        uuid: UUID,
        listenMs: Long,
        connectTimeoutMs: Long,
    ): Result<List<ByteArray>> = withGatt(address, connectTimeoutMs) { gatt ->
        val (_, ch) = findCharacteristic(gatt, uuid)
            ?: throw IllegalStateException("Characteristic $uuid not found on $address")
        val collected = mutableListOf<ByteArray>()
        notificationSinks[address to ch.uuid] = { collected += it }
        @SuppressLint("MissingPermission")
        val enabled = gatt.setCharacteristicNotification(ch, true)
        if (!enabled) {
            notificationSinks.remove(address to ch.uuid)
            throw IllegalStateException("setCharacteristicNotification($uuid) refused")
        }
        // Write the CCCD to actually enable notifications on the peripheral.
        val cccd = ch.getDescriptor(CCCD_UUID)
        if (cccd != null) {
            @SuppressLint("MissingPermission")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }
        try {
            delay(listenMs.coerceIn(500L, 120_000L))
        } finally {
            notificationSinks.remove(address to ch.uuid)
            @SuppressLint("MissingPermission")
            gatt.setCharacteristicNotification(ch, false)
        }
        collected.toList()
    }

    // ─── GATT plumbing ────────────────────────────────────────────────────────

    private val pendingReads =
        ConcurrentHashMap<Pair<String, UUID>, CompletableDeferred<ByteArray>>()
    private val pendingWrites =
        ConcurrentHashMap<Pair<String, UUID>, CompletableDeferred<Boolean>>()
    private val notificationSinks =
        ConcurrentHashMap<Pair<String, UUID>, (ByteArray) -> Unit>()

    private suspend fun <T> withGatt(
        address: String,
        timeoutMs: Long,
        block: suspend (BluetoothGatt) -> T,
    ): Result<T> = guarded {
        val a = adapter!!
        val device: BluetoothDevice = try {
            a.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return@guarded Result.failure(IllegalArgumentException("Invalid BLE address '$address'"))
        }
        gattMutex.withLock {
            val connected = CompletableDeferred<BluetoothGatt>()
            val servicesReady = CompletableDeferred<BluetoothGatt>()
            val disconnected = CompletableDeferred<Unit>()
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            @SuppressLint("MissingPermission")
                            gatt.discoverServices()
                            if (!connected.isCompleted) connected.complete(gatt)
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (!disconnected.isCompleted) disconnected.complete(Unit)
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS && !servicesReady.isCompleted) {
                        servicesReady.complete(gatt)
                    } else if (status != BluetoothGatt.GATT_SUCCESS && !servicesReady.isCompleted) {
                        servicesReady.completeExceptionally(
                            IllegalStateException("discoverServices failed: status=$status")
                        )
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    status: Int,
                ) {
                    val key = gatt.device.address to characteristic.uuid
                    val d = pendingReads.remove(key) ?: return
                    if (status == BluetoothGatt.GATT_SUCCESS) d.complete(value)
                    else d.completeExceptionally(IllegalStateException("read status=$status"))
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                    onCharacteristicRead(gatt, characteristic, characteristic.value ?: ByteArray(0), status)
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int,
                ) {
                    val key = gatt.device.address to characteristic.uuid
                    val d = pendingWrites.remove(key) ?: return
                    d.complete(status == BluetoothGatt.GATT_SUCCESS)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    val key = gatt.device.address to characteristic.uuid
                    notificationSinks[key]?.invoke(value)
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                    onCharacteristicChanged(gatt, characteristic, characteristic.value ?: ByteArray(0))
                }
            }

            @SuppressLint("MissingPermission")
            val gatt = device.connectGatt(context, false, callback)
                ?: return@withLock Result.failure(IllegalStateException("connectGatt returned null"))
            try {
                withTimeout(timeoutMs) {
                    connected.await()
                    servicesReady.await()
                }
                val out = block(gatt)
                Result.success(out)
            } catch (e: TimeoutCancellationException) {
                Result.failure(IllegalStateException("BLE op to $address timed out after ${timeoutMs}ms"))
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                @SuppressLint("MissingPermission")
                gatt.disconnect()
                @SuppressLint("MissingPermission")
                gatt.close()
                // Give the stack a moment to release the resource before the next call.
                delay(150)
            }
        }
    }

    private fun findCharacteristic(
        gatt: BluetoothGatt,
        uuid: UUID,
    ): Pair<UUID, BluetoothGattCharacteristic>? {
        for (svc in gatt.services) {
            svc.getCharacteristic(uuid)?.let { return svc.uuid to it }
        }
        return null
    }

    private fun propertyNames(props: Int): List<String> = buildList {
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("read")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("write")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("write-no-response")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("notify")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("indicate")
        if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("signed-write")
        if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("broadcast")
        if (props and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("extended")
    }

    companion object {
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
