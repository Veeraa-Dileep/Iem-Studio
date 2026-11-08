package com.iemstudio.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.iemstudio.app.data.model.BtDevice
import com.iemstudio.app.data.model.ConnectionMode
import com.iemstudio.app.data.model.DeviceUiState
import com.iemstudio.app.data.model.EqPreset
import com.iemstudio.app.data.model.EqPresets
import com.iemstudio.app.data.model.snapGain
import com.iemstudio.app.data.prefs.SettingsRepository
import com.iemstudio.app.data.transport.BleIemTransport
import com.iemstudio.app.data.transport.DemoIemTransport
import com.iemstudio.app.data.transport.DeviceStatus
import com.iemstudio.app.data.transport.IemTransport
import com.iemstudio.app.data.transport.UsbIemTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single source of truth for the app. Owns the active transport
 * (USB Host / Bluetooth LE / Demo) and exposes a [DeviceUiState] flow
 * that the Compose screens observe.
 */
class IemController(private val appContext: Context) {

    val settingsRepository = SettingsRepository(appContext)

    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbTransport = UsbIemTransport(usbManager)
    private val bleTransport = BleIemTransport(appContext)
    private val demoTransport = DemoIemTransport()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    // Cached copies of preferences (the flow above is the source of truth)
    private var demoMode = true
    private var autoConnect = true
    private var volumeSteps = 20
    private var btPrefix = "IEM-"

    private var active: IemTransport? = null
    private var pendingDevice: UsbDevice? = null
    private var pollJob: Job? = null
    private var persistVolumeJob: Job? = null
    private var persistCurveJob: Job? = null

    init {
        // React to preference changes
        scope.launch {
            settingsRepository.settings.collect { s ->
                demoMode = s.demoMode
                autoConnect = s.autoConnect
                volumeSteps = s.volumeSteps
                btPrefix = s.btNamePrefix
                bleTransport.serviceUuid = s.btServiceUuid
                bleTransport.charUuid = s.btCharUuid
                _state.update {
                    it.copy(
                        ledOn = s.ledEnabled,
                        volume = s.lastVolume,
                        eqGains = s.lastCurve ?: it.eqGains,
                    )
                }
                refreshTransport()
            }
        }

        // Demo device battery simulation
        demoTransport.startSimulation {
            _state.update { st ->
                if (st.mode == ConnectionMode.DEMO && st.connected) {
                    st.copy(batteryPercent = demoTransport.battery)
                } else st
            }
        }

        // Bluetooth transport callbacks
        bleTransport.onScanResults = { results ->
            _state.update { it.copy(btFoundDevices = results, btScanning = false) }
        }
        bleTransport.onStatus = { status -> applyStatus(status) }
        bleTransport.onConnectionChange = { connected, name ->
            _state.update {
                it.copy(btConnectedName = if (connected) name ?: it.btConnectedName else null)
            }
            refreshTransport()
        }

        // Remember a device that is already plugged in at launch
        usbManager.deviceList.values.firstOrNull { usbTransport.isCandidate(it) }?.let { candidate ->
            pendingDevice = candidate
            _state.update { it.copy(usbDevicePlugged = true) }
        }
        refreshTransport()
    }

    // ── Transport selection ─────────────────────────────────────────────────

    /**
     * USB takes precedence over Bluetooth, which takes precedence over the
     * demo device (when demo mode is enabled). Called on every connect,
     * disconnect, permission result and preference change.
     */
    private fun refreshTransport() {
        val target: IemTransport? = when {
            usbTransport.isConnected -> usbTransport
            bleTransport.isConnected -> bleTransport
            demoMode -> demoTransport
            else -> null
        }
        if (target === active) return
        active?.disconnect()
        active = target
        pollJob?.cancel()

        when (target) {
            null -> _state.update {
                it.copy(
                    mode = ConnectionMode.NONE, connected = false,
                    deviceName = null, vendorName = null, dacChip = null,
                    firmwareVersion = null, batteryPercent = null,
                )
            }
            is UsbIemTransport -> {
                _state.update {
                    it.copy(
                        mode = ConnectionMode.USB, connected = true,
                        deviceName = usbTransport.deviceName ?: it.deviceName,
                        vendorName = usbTransport.vendorName,
                        dacChip = usbTransport.dacChip ?: it.dacChip,
                        usbDevicePlugged = true,
                    )
                }
                startPolling()
            }
            is BleIemTransport -> {
                _state.update {
                    it.copy(
                        mode = ConnectionMode.BLUETOOTH, connected = true,
                        deviceName = it.btConnectedName ?: "Bluetooth IEM",
                        vendorName = "Bluetooth",
                    )
                }
            }
            is DemoIemTransport -> {
                _state.update {
                    it.copy(
                        mode = ConnectionMode.DEMO, connected = true,
                        deviceName = "Demo IEM", vendorName = "Simulated",
                        dacChip = "Demo DAC", firmwareVersion = "1.0.0-demo",
                        batteryPercent = demoTransport.battery,
                    )
                }
            }
            else -> {}
        }
        try {
            pushCurrentConfig()
        } catch (_: Exception) {
            // never fail a transport switch because a config write bounced
        }
    }

    /** Re-apply volume / EQ / format after (re)connecting. */
    private fun pushCurrentConfig() {
        val t = active ?: return
        val s = _state.value
        t.setVolume(s.volume)
        t.setMuted(s.muted)
        t.setGainDb(s.gainDb)
        t.setLed(s.ledOn)
        s.eqGains.forEachIndexed { i, g -> t.setBandGain(i, g) }
        t.setSampleRate(s.sampleRate)
        t.setBitDepth(s.bitDepth)
    }

    /** Poll USB status every few seconds while a USB device is active. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(3000)
                usbTransport.fetchStatus()?.let { applyStatus(it) }
            }
        }
    }

    private fun applyStatus(status: DeviceStatus) {
        _state.update { cur ->
            cur.copy(
                volume = status.volume ?: cur.volume,
                muted = status.muted ?: cur.muted,
                gainDb = status.gainDb ?: cur.gainDb,
                ledOn = status.ledOn ?: cur.ledOn,
                eqGains = status.eqGains ?: cur.eqGains,
                sampleRate = status.sampleRate ?: cur.sampleRate,
                bitDepth = status.bitDepth ?: cur.bitDepth,
                firmwareVersion = status.firmware ?: cur.firmwareVersion,
                batteryPercent = status.battery ?: cur.batteryPercent,
                dacChip = status.dacChip ?: cur.dacChip,
            )
        }
    }

    /** Run a command against the active transport; surface failures as errors. */
    private fun exec(command: (IemTransport) -> Unit) {
        val t = active
        if (t == null) {
            _state.update { it.copy(errorMessage = "No IEM connected") }
            return
        }
        try {
            command(t)
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = e.message ?: "Command failed") }
        }
    }

    // ── Volume & mute ───────────────────────────────────────────────────────

    fun setVolume(volume: Int) {
        val v = volume.coerceIn(0, 100)
        exec { t ->
            t.setVolume(v)
            _state.update { it.copy(volume = v, muted = false) }
            persistVolume(v)
        }
    }

    fun stepVolume(delta: Int) = setVolume(_state.value.volume + delta)

    fun toggleMute() {
        val target = !_state.value.muted
        exec { t ->
            t.setMuted(target)
            _state.update { it.copy(muted = target) }
        }
    }

    // ── EQ ──────────────────────────────────────────────────────────────────

    fun setBandGain(band: Int, gainDb: Float) {
        val snapped = snapGain(gainDb)
        exec { t ->
            t.setBandGain(band, snapped)
            _state.update { it.copy(eqGains = it.eqGains.toMutableList().also { g -> g[band] = snapped }) }
            persistCurve()
        }
    }

    fun applyPreset(preset: EqPreset) {
        exec { t ->
            preset.gains.forEachIndexed { i, g -> t.setBandGain(i, g) }
            _state.update { it.copy(eqGains = preset.gains) }
            persistCurve()
        }
    }

    fun resetEq() = applyPreset(EqPresets.BUILT_INS.first())

    fun saveCustomPreset(name: String) {
        if (name.isBlank()) return
        val gains = _state.value.eqGains
        scope.launch {
            settingsRepository.saveCustomPreset(
                EqPreset(UUID.randomUUID().toString(), name.trim(), gains, isCustom = true)
            )
        }
    }

    fun deleteCustomPreset(id: String) {
        scope.launch { settingsRepository.deleteCustomPreset(id) }
    }

    // ── Output format ───────────────────────────────────────────────────────

    fun setSampleRate(rate: Int) {
        exec { t ->
            t.setSampleRate(rate)
            _state.update { it.copy(sampleRate = rate) }
        }
    }

    fun setBitDepth(depth: Int) {
        exec { t ->
            t.setBitDepth(depth)
            _state.update { it.copy(bitDepth = depth) }
        }
    }

    fun setGainDb(gainDb: Float) {
        val g = gainDb.coerceIn(0f, 12f)
        exec { t ->
            t.setGainDb(g)
            _state.update { it.copy(gainDb = g) }
        }
    }

    fun setLed(on: Boolean) {
        exec { t ->
            t.setLed(on)
            _state.update { it.copy(ledOn = on) }
        }
        scope.launch { settingsRepository.setLedEnabled(on) }
    }

    // ── USB host ────────────────────────────────────────────────────────────

    fun onUsbAttached(device: UsbDevice?) {
        if (device == null || !usbTransport.isCandidate(device)) return
        pendingDevice = device
        _state.update { it.copy(usbDevicePlugged = true) }
        if (usbManager.hasPermission(device)) {
            usbTransport.open(device)
            refreshTransport()
        } else if (autoConnect) {
            requestUsbPermission()
        }
    }

    /** Ask the user to grant USB access (triggers the system permission dialog). */
    fun requestUsbPermission() {
        val device = pendingDevice
            ?: usbManager.deviceList.values.firstOrNull { usbTransport.isCandidate(it) }
            ?: return
        if (usbManager.hasPermission(device)) {
            pendingDevice = null
            usbTransport.open(device)
            _state.update { it.copy(pendingPermission = false) }
            refreshTransport()
            return
        }
        pendingDevice = device
        _state.update { it.copy(pendingPermission = true) }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, 0,
            Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    fun onUsbPermissionResult(granted: Boolean, device: UsbDevice?) {
        pendingDevice = null
        _state.update { it.copy(pendingPermission = false) }
        if (granted && device != null) {
            usbTransport.open(device)
            refreshTransport()
        }
    }

    fun onUsbDetached(device: UsbDevice?) {
        _state.update {
            it.copy(usbDevicePlugged = usbManager.deviceList.values.any { d -> usbTransport.isCandidate(d) })
        }
        if (usbTransport.isConnected && (device == null || usbTransport.matches(device))) {
            usbTransport.disconnect()
            refreshTransport()
        }
    }

    fun disconnect() {
        val t = active
        if (t is UsbIemTransport || t is BleIemTransport) {
            t.disconnect()
            refreshTransport()
        }
    }

    // ── Bluetooth companion ─────────────────────────────────────────────────

    fun isBluetoothSupported(): Boolean = bleTransport.isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = bleTransport.isBluetoothEnabled()

    fun hasBtPermissions(): Boolean = bleTransport.hasPermissions()

    fun scanForBt() {
        if (!bleTransport.hasPermissions()) {
            _state.update { it.copy(errorMessage = "Bluetooth permissions not granted") }
            return
        }
        if (!bleTransport.isBluetoothEnabled()) {
            _state.update { it.copy(errorMessage = "Bluetooth is turned off") }
            return
        }
        _state.update { it.copy(btScanning = true, btFoundDevices = emptyList()) }
        bleTransport.startScan(btPrefix)
    }

    fun stopBtScan() {
        bleTransport.stopScan()
        _state.update { it.copy(btScanning = false) }
    }

    fun connectBt(device: BtDevice) {
        if (!bleTransport.hasPermissions()) {
            _state.update { it.copy(errorMessage = "Bluetooth permissions not granted") }
            return
        }
        _state.update { it.copy(btScanning = false) }
        bleTransport.connect(device.address, device.name)
    }

    fun disconnectBt() {
        if (bleTransport.isConnected) {
            bleTransport.disconnect()
            _state.update { it.copy(btConnectedName = null, btFoundDevices = emptyList()) }
            refreshTransport()
        }
    }

    // ── Settings ────────────────────────────────────────────────────────────

    fun setDemoMode(enabled: Boolean) = scope.launch { settingsRepository.setDemoMode(enabled) }

    fun setAutoConnect(enabled: Boolean) = scope.launch { settingsRepository.setAutoConnect(enabled) }

    fun setVolumeSteps(steps: Int) = scope.launch { settingsRepository.setVolumeSteps(steps) }

    fun setBtPrefix(prefix: String) = scope.launch { settingsRepository.setBtNamePrefix(prefix) }

    fun setBtUuids(service: String, characteristic: String) =
        scope.launch { settingsRepository.setBtUuids(service, characteristic) }

    fun resetAllSettings() = scope.launch { settingsRepository.resetAll() }

    // ── Errors ──────────────────────────────────────────────────────────────

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun showError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    private fun persistVolume(v: Int) {
        persistVolumeJob?.cancel()
        persistVolumeJob = scope.launch {
            delay(400)
            settingsRepository.saveLastVolume(v)
        }
    }

    private fun persistCurve() {
        persistCurveJob?.cancel()
        persistCurveJob = scope.launch {
            delay(800)
            settingsRepository.saveLastCurve(_state.value.eqGains)
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.iemstudio.app.USB_PERMISSION"
    }
}
