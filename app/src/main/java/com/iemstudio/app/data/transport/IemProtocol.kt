package com.iemstudio.app.data.transport

import android.hardware.usb.UsbDeviceConnection
import com.iemstudio.app.data.model.EQ_BAND_COUNT
import com.iemstudio.app.data.model.EQ_MAX_DB
import com.iemstudio.app.data.model.EQ_MIN_DB
import com.iemstudio.app.data.model.EQ_STEP_DB
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Vendor-specific USB command protocol used to talk to the IEM's DAC.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * IMPORTANT — MAPPING REQUIRED
 * ─────────────────────────────────────────────────────────────────────────────
 * This file encodes a *documented, self-consistent* example protocol. Real IEMs
 * use their own vendor command set (HID feature reports, vendor control
 * transfers, or bulk payloads). Before using the app with real hardware, adapt
 * the request codes / payload layout below to your device's firmware spec —
 * see README.md ("Adapting the protocol to your IEM").
 *
 * All commands are control transfers on the device's control endpoint:
 *   bmRequestType 0x40 (host→device, vendor, device)   → commands
 *   bmRequestType 0xC0 (device→host,  vendor, device)  → status reads
 * ─────────────────────────────────────────────────────────────────────────────
 */
object IemProtocol {

    // Vendor request codes (wRequest). Adjust to match the device firmware.
    const val REQ_SET_VOLUME = 0x01       // wValue = 0..100
    const val REQ_SET_MUTE = 0x02         // wValue = 0|1
    const val REQ_SET_EQ_BAND = 0x03      // wValue = gain step (see encodeGainStep), wIndex = band 0..9
    const val REQ_SET_GAIN = 0x04         // wValue = gain step (0..48 → 0..24 dB … see below)
    const val REQ_SET_LED = 0x05          // wValue = 0|1
    const val REQ_SET_SAMPLE_RATE = 0x06  // wValue = sample-rate code (see SAMPLE_RATE_CODES)
    const val REQ_SET_BIT_DEPTH = 0x07    // wValue = 16|24|32
    const val REQ_GET_STATUS = 0x80       // IN: status struct (see parseStatus)

    const val REQUEST_TYPE_VENDOR_OUT = 0x40
    const val REQUEST_TYPE_VENDOR_IN = 0xC0
    const val TIMEOUT_MS = 500

    /** Codes used by REQ_SET_SAMPLE_RATE (index into SAMPLE_RATES). */
    private val SAMPLE_RATE_CODES = listOf(44100, 48000, 88200, 96000, 176400, 192000)

    /**
     * Gains are encoded as an integer "step": step = gain/0.5 + 24.
     * So 0 dB → 24, +6 dB → 36, -6 dB → 12. 48 covers +12 dB.
     */
    fun encodeGainStep(gainDb: Float): Int =
        ((gainDb / EQ_STEP_DB).toInt() + 24).coerceIn(0, 48)

    fun decodeGainStep(step: Int): Float =
        ((step - 24) * EQ_STEP_DB).toFloat().coerceIn(EQ_MIN_DB, EQ_MAX_DB)

    /** Send a vendor control command; throws IOException on failure. */
    fun sendVendor(
        connection: UsbDeviceConnection,
        request: Int,
        value: Int,
        index: Int = 0,
        data: ByteArray? = null,
    ) {
        val payload = data ?: ByteArray(0)
        val result = connection.controlTransfer(
            REQUEST_TYPE_VENDOR_OUT, request, value, index,
            payload, payload.size, TIMEOUT_MS
        )
        if (result < 0) {
            throw IOException("USB vendor command 0x%02X failed (err=%d)".format(request, result))
        }
    }

    /** Read the device status struct; returns null when the device doesn't support it. */
    fun readStatus(connection: UsbDeviceConnection, size: Int = 32): ByteArray? {
        val buffer = ByteArray(size)
        val read = connection.controlTransfer(
            REQUEST_TYPE_VENDOR_IN, REQ_GET_STATUS, 0, 0, buffer, size, TIMEOUT_MS
        )
        return if (read > 0) buffer.copyOf(read) else null
    }

    /**
     * Parse the status struct returned by REQ_GET_STATUS. Example layout
     * (16 bytes — adjust to your firmware):
     *
     *   byte 0            volume 0..100
     *   byte 1            flags: bit0 muted, bit1 LED
     *   byte 2            gain step (0..48, 24 = 0 dB)
     *   bytes 3..12       EQ gains, 10 bands, one step byte each
     *   byte 13           sample-rate code (index into SAMPLE_RATE_CODES)
     *   byte 14           bit depth (16|24|32)
     *   byte 15           battery % (0xFF = unknown)
     */
    fun parseStatus(bytes: ByteArray): DeviceStatus {
        if (bytes.size < 16) return DeviceStatus.EMPTY
        val flags = bytes[1].toInt() and 0xFF
        val rateCode = bytes[13].toInt() and 0xFF
        val battery = bytes[15].toInt() and 0xFF
        return DeviceStatus(
            volume = bytes[0].toInt().coerceIn(0, 100),
            muted = (flags and 0x01) != 0,
            ledOn = (flags and 0x02) != 0,
            gainDb = decodeGainStep(bytes[2].toInt() and 0xFF),
            eqGains = (0 until EQ_BAND_COUNT).map { decodeGainStep(bytes[3 + it].toInt() and 0xFF) },
            sampleRate = SAMPLE_RATE_CODES.getOrNull(rateCode),
            bitDepth = bytes[14].toInt().takeIf { it == 16 || it == 24 || it == 32 },
            battery = if (battery == 0xFF) null else battery.coerceIn(0, 100),
        )
    }
}

/**
 * USB Audio Class helpers. On Android, a USB DAC is normally driven by the
 * platform USB audio stack; some stacks honor an explicit SET_CUR request for
 * the sampling frequency control. This is attempted before the vendor fallback.
 */
object UsbAudioClass {

    const val SET_CUR = 0x01
    const val CS_SAMPLING_FREQ_CONTROL = 0x01

    /**
     * UAC 2.0 SET_CUR for the sampling frequency control:
     *   bmRequestType = 0x22 (host→device, class, interface recipient)
     *   bRequest      = 0x01 (SET_CUR)
     *   wValue        = (CS_SAMPLING_FREQ_CONTROL << 8) | 0
     *   wIndex        = bEndpointAddress of the isochronous OUT endpoint
     *   data          = 4-byte little-endian frequency
     *
     * UAC 1.0 devices address the *interface* instead of the endpoint in wIndex —
     * if your device is UAC1, pass the interface number here.
     */
    fun setSamplingFrequency(
        connection: UsbDeviceConnection,
        endpointOrInterfaceAddress: Int,
        rate: Int,
        timeoutMs: Int = 1000,
    ): Boolean {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(rate).array()
        val result = connection.controlTransfer(
            0x22,
            SET_CUR,
            (CS_SAMPLING_FREQ_CONTROL shl 8),
            endpointOrInterfaceAddress and 0xFF,
            buffer, buffer.size, timeoutMs
        )
        return result == buffer.size
    }
}
