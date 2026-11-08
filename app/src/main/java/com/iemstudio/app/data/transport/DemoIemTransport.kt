package com.iemstudio.app.data.transport

import com.iemstudio.app.data.model.EQ_BAND_COUNT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simulated device: every command "succeeds", status is tracked locally and the
 * battery slowly drains. Lets the whole UI be explored without hardware.
 * Enable/disable from Settings → Demo mode.
 */
class DemoIemTransport : IemTransport {

    override val isConnected: Boolean get() = true

    val battery: Int get() = batteryPercent

    private var batteryPercent = 86
    private var volume = 60
    private var muted = false
    private var gainDb = 0f
    private var ledOn = true
    private var eqGains = List(EQ_BAND_COUNT) { 0f }
    private var sampleRate = 48000
    private var bitDepth = 24

    private var simJob: Job? = null

    /** Starts the battery-drain simulation; safe to call repeatedly. */
    fun startSimulation(onTick: () -> Unit) {
        if (simJob?.isActive == true) return
        simJob = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (isActive) {
                delay(30_000)
                batteryPercent = (batteryPercent - 1).coerceAtLeast(8)
                onTick()
            }
        }
    }

    override fun connect(): Boolean = true

    override fun disconnect() {
        // a simulated device is always "connected"
    }

    override fun setVolume(volume: Int) {
        this.volume = volume.coerceIn(0, 100)
        muted = false
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    override fun setGainDb(gainDb: Float) {
        this.gainDb = gainDb.coerceIn(0f, 12f)
    }

    override fun setLed(enabled: Boolean) {
        ledOn = enabled
    }

    override fun setBandGain(band: Int, gainDb: Float) {
        eqGains = eqGains.toMutableList().also { it[band.coerceIn(0, 9)] = gainDb }
    }

    override fun setSampleRate(rate: Int) {
        sampleRate = rate
    }

    override fun setBitDepth(depth: Int) {
        bitDepth = depth
    }

    override fun fetchStatus(): DeviceStatus = DeviceStatus(
        volume = volume,
        muted = muted,
        gainDb = gainDb,
        ledOn = ledOn,
        eqGains = eqGains,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        firmware = "1.0.0-demo",
        battery = batteryPercent,
        dacChip = "Demo DAC",
    )

    override fun deviceLabel(): String = "Demo IEM"
}
