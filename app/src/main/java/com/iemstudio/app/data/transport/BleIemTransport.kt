package com.iemstudio.app.data.transport

import android.Manifest
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
import com.iemstudio.app.data.model.BtDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth LE transport for the IEM's companion link (used when the IEM also
 * exposes a BLE control channel, or as a fallback on phones without USB host).
 *
 * Protocol (JSON over GATT, example):
 *   write  → {"cmd":"set_volume","value":60}
 *   notify ← {"battery":87,"fw":"1.0.0","volume":60,"muted":false, ...}
 *
 * The service/characteristic UUIDs and the JSON keys are configurable — see
 * the Settings screen and README.md.
 */
class BleIemTransport(private val context: Context) : IemTransport {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var gatt: BluetoothGatt? = null
    private var connected = false
    private var expectedName: String? = null
    private var scanStopJob: Job? = null
    private var scanPrefix: String = "IEM-"

    // Remote -> device cache for scan results
    private val found = LinkedHashMap<String, BtDevice>()

    /** UUID strings used for the control channel; pushed by the controller. */
    var serviceUuid: String = "0000FEE0-0000-1000-8000-00805f9b34fb"
    var charUuid: String = "0000FEE1-0000-1000-8000-00805f9b34fb"

    // Callbacks wired up by the controller
    var onScanResults: ((List<BtDevice>) -> Unit)? = null
    var onConnectionChange: ((connected: Boolean, name: String?) -> Unit)? = null
    var onStatus: ((DeviceStatus) -> Unit)? = null

    override val isConnected: Boolean get() = connected

    fun isBluetoothSupported(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    // ── Scanning ────────────────────────────────────────────────────────────

    fun startScan(prefix: String) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (!hasPermissions()) return
        scanPrefix = prefix
        found.clear()
        scanStopJob?.cancel()
        scanStopJob = scope.launch {
            delay(12_000)
            stopScan()
        }
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            scanner.startScan(null, settings, scanCallback)
        } catch (_: SecurityException) {
            // permissions were revoked mid-scan
        }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        scanStopJob = null
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (scanPrefix.isNotBlank() && !name.startsWith(scanPrefix)) return
            found[result.device.address] = BtDevice(name, result.device.address, result.rssi)
            if (found.size >= 8) stopScan()
            onScanResults?.invoke(found.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
            onScanResults?.invoke(found.values.toList())
        }
    }

    // ── Connecting ──────────────────────────────────────────────────────────

    fun connect(address: String, name: String): Boolean {
        if (!hasPermissions()) return false
        val device = adapter?.getRemoteDevice(address) ?: return false
        expectedName = name
        connected = false
        gatt?.close()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
        return gatt != null
    }

    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connected = false
                    onConnectionChange?.invoke(false, null)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            connected = true
            onConnectionChange?.invoke(true, expectedName)
            subscribeToNotifications(g)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val payload = characteristic.value?.toString(Charsets.UTF_8) ?: return
            onStatus?.invoke(parsePayload(payload))
        }
    }

    private fun subscribeToNotifications(g: BluetoothGatt) {
        if (!hasPermissions()) return
        try {
            val service = parsedService()?.let(g::getService) ?: return
            val control = service.getCharacteristic(parsedCharacteristic())
                ?: service.characteristics.firstOrNull()
                ?: return
            if (control.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) return
            g.setCharacteristicNotification(control, true)
            val cccd = control.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd != null) {
                cccd.value = byteArrayOf(0x01, 0x00)
                g.writeDescriptor(cccd)
            }
        } catch (_: Exception) {
            // notifications are best-effort
        }
    }

    // ── Commands ────────────────────────────────────────────────────────────

    override fun connect(): Boolean = connected

    override fun disconnect() {
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
        connected = false
    }

    override fun setVolume(volume: Int) = sendCommand("set_volume", volume.coerceIn(0, 100))

    override fun setMuted(muted: Boolean) = sendCommand("set_mute", if (muted) 1 else 0)

    override fun setGainDb(gainDb: Float) = sendCommand("set_gain", gainDb.toDouble())

    override fun setLed(enabled: Boolean) = sendCommand("set_led", if (enabled) 1 else 0)

    override fun setBandGain(band: Int, gainDb: Float) {
        send {
            put("cmd", "set_eq_band")
            put("band", band.coerceIn(0, 9))
            put("gain", gainDb.toDouble())
        }
    }

    override fun setSampleRate(rate: Int) = sendCommand("set_sample_rate", rate)

    override fun setBitDepth(depth: Int) = sendCommand("set_bit_depth", depth)

    override fun fetchStatus(): DeviceStatus? = null // push-based (notifications)

    override fun deviceLabel(): String? = expectedName

    // ── Internals ───────────────────────────────────────────────────────────

    private fun sendCommand(cmd: String, value: Any) {
        send { put("cmd", cmd); put("value", value) }
    }

    @Suppress("DEPRECATION")
    private fun send(build: JSONObject.() -> Unit) {
        if (!connected) throw IOException("Bluetooth IEM is not connected")
        val g = gatt ?: throw IOException("Bluetooth IEM is not connected")
        val service = parsedService()?.let(g::getService)
            ?: throw IOException("GATT service not found (${serviceUuid})")
        val control = service.getCharacteristic(parsedCharacteristic())
            ?: throw IOException("GATT characteristic not found (${charUuid})")
        val json = JSONObject().apply(build)
            .put("ts", System.currentTimeMillis())
            .toString()
        control.value = json.toByteArray(Charsets.UTF_8)
        if (!g.writeCharacteristic(control)) {
            throw IOException("GATT write failed")
        }
    }

    private fun parsedService(): UUID? = try {
        UUID.fromString(serviceUuid)
    } catch (_: Exception) {
        null
    }

    private fun parsedCharacteristic(): UUID = try {
        UUID.fromString(charUuid)
    } catch (_: Exception) {
        UUID.fromString("0000FEE1-0000-1000-8000-00805f9b34fb")
    }

    /** Parse a notification payload into a status snapshot (best effort). */
    private fun parsePayload(payload: String): DeviceStatus {
        val json = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return DeviceStatus.EMPTY
        }
        val battery = json.optInt("battery", -1)
        return DeviceStatus(
            volume = if (json.has("volume")) json.getInt("volume").coerceIn(0, 100) else null,
            muted = if (json.has("muted")) json.getBoolean("muted") else null,
            gainDb = if (json.has("gain")) json.getDouble("gain").toFloat() else null,
            ledOn = if (json.has("led")) json.getBoolean("led") else null,
            sampleRate = if (json.has("sample_rate")) json.getInt("sample_rate") else null,
            bitDepth = if (json.has("bit_depth")) json.getInt("bit_depth") else null,
            firmware = if (json.has("fw")) json.getString("fw") else null,
            battery = if (battery in 0..100) battery else null,
        )
    }
}
