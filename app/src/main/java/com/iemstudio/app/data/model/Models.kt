package com.iemstudio.app.data.model

import java.util.Locale
import kotlin.math.roundToInt

/** Number of EQ bands in the 10-band equalizer. */
const val EQ_BAND_COUNT = 10

/** Center frequencies (Hz) of the 10 EQ bands. */
val EQ_BAND_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

/** Short labels for the band x-axis. */
val EQ_BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

/** EQ gain range and resolution in dB. */
const val EQ_MIN_DB = -12f
const val EQ_MAX_DB = 12f
const val EQ_STEP_DB = 0.5f

/** Supported DAC sample rates (Hz) and bit depths. */
val SAMPLE_RATES = listOf(44100, 48000, 88200, 96000, 176400, 192000)
val BIT_DEPTHS = listOf(16, 24, 32)
const val DEFAULT_SAMPLE_RATE = 48000
const val DEFAULT_BIT_DEPTH = 24

/** How the app is currently talking to the IEM. */
enum class ConnectionMode { NONE, USB, BLUETOOTH, DEMO }

/** Snapshot of everything the UI needs. */
data class DeviceUiState(
    val mode: ConnectionMode = ConnectionMode.NONE,
    val connected: Boolean = false,
    val deviceName: String? = null,
    val vendorName: String? = null,
    val dacChip: String? = null,
    val firmwareVersion: String? = null,
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val bitDepth: Int = DEFAULT_BIT_DEPTH,
    val volume: Int = 60,
    val muted: Boolean = false,
    val gainDb: Float = 0f,
    val ledOn: Boolean = true,
    val eqGains: List<Float> = List(EQ_BAND_COUNT) { 0f },
    val batteryPercent: Int? = null,
    val btScanning: Boolean = false,
    val btFoundDevices: List<BtDevice> = emptyList(),
    val btConnectedName: String? = null,
    val pendingPermission: Boolean = false,
    /** A compatible USB device is physically plugged in (permission may be pending). */
    val usbDevicePlugged: Boolean = false,
    val errorMessage: String? = null,
) {
    val formatLabel: String
        get() = "${formatRate(sampleRate)} • ${bitDepth}-bit"
}

/** A Bluetooth device found while scanning. */
data class BtDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

/** "48000" -> "48 kHz", "44100" -> "44.1 kHz". */
fun formatRate(rate: Int): String = if (rate % 1000 == 0) {
    "${rate / 1000} kHz"
} else {
    String.format(Locale.US, "%.1f kHz", rate / 1000f)
}

/** Snap a gain value to the 0.5 dB grid and clamp to [-12, +12]. */
fun snapGain(gainDb: Float): Float =
    ((gainDb / EQ_STEP_DB).roundToInt() * EQ_STEP_DB).coerceIn(EQ_MIN_DB, EQ_MAX_DB)

/** Format a gain for display, e.g. +3.0 / 0.0 / -4.5. */
fun formatGain(gainDb: Float): String = String.format(Locale.US, "%+.1f", gainDb)
