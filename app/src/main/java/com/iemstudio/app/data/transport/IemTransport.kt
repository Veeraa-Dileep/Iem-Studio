package com.iemstudio.app.data.transport

import com.iemstudio.app.data.model.EQ_BAND_COUNT

/**
 * Common contract for talking to the IEM.
 *
 * Concrete implementations:
 *  - [UsbIemTransport]   – USB Host (UsbManager / UsbDeviceConnection)
 *  - [BleIemTransport]   – Bluetooth LE GATT companion link
 *  - [DemoIemTransport]  – simulated device so the UI can be explored without hardware
 *
 * Implementations must throw [java.io.IOException] (or similar) when a command
 * cannot be delivered; the controller turns that into a user-visible error.
 */
interface IemTransport {

    val isConnected: Boolean

    /** Open the connection. Returns true when the transport is usable. */
    fun connect(): Boolean

    fun disconnect()

    fun setVolume(volume: Int)

    fun setMuted(muted: Boolean)

    /** Headphone amp / DAC gain in dB (0..12). */
    fun setGainDb(gainDb: Float)

    fun setLed(enabled: Boolean)

    /** band is 0..9, gainDb is -12..+12 in 0.5 steps. */
    fun setBandGain(band: Int, gainDb: Float)

    fun setSampleRate(rate: Int)

    fun setBitDepth(depth: Int)

    /**
     * Pull the device's current status, or null when the transport is push-only
     * (Bluetooth) or temporarily unavailable.
     */
    fun fetchStatus(): DeviceStatus?

    /** Human-readable device label (may be null). */
    fun deviceLabel(): String?
}

/**
 * Status snapshot returned by [IemTransport.fetchStatus] (USB polling) or pushed
 * by Bluetooth notifications. Null fields mean "unknown — keep the old value".
 */
data class DeviceStatus(
    val volume: Int? = null,
    val muted: Boolean? = null,
    val gainDb: Float? = null,
    val ledOn: Boolean? = null,
    val eqGains: List<Float>? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val firmware: String? = null,
    val battery: Int? = null,
    val dacChip: String? = null,
) {
    companion object {
        /** All-null status, convenient for "nothing known yet". */
        val EMPTY = DeviceStatus()
    }
}

/** Convenience: 10-band flat EQ. */
val FLAT_GAINS: List<Float> = List(EQ_BAND_COUNT) { 0f }
