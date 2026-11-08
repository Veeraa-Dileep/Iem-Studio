package com.iemstudio.app.data.transport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.IOException

/**
 * USB Host transport: talks to the Type-C IEM DAC over the USB bus using
 * vendor control transfers (see [IemProtocol]) plus a USB-Audio-Class
 * sampling-frequency request. Commands are cheap control transfers, so no
 * background thread is needed here — the controller serializes calls.
 */
class UsbIemTransport(private val usbManager: UsbManager) : IemTransport {

    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null

    override val isConnected: Boolean get() = connection != null

    var deviceName: String? = null
        private set
    var vendorName: String? = null
        private set
    var dacChip: String? = null
        private set

    /** Known DAC chipset vendors, for nicer labels. */
    private val VENDOR_NAMES = mapOf(
        0x20B1 to "XMOS",
        0x152A to "Thesycon",
        0x0D8C to "C-Media",
        0x08BB to "Texas Instruments",
        0x0414 to "Conexant",
        0x2B71 to "ESS",
    )

    fun matches(other: UsbDevice?): Boolean =
        other != null && device?.deviceId == other.deviceId

    /** A USB device is a candidate if it exposes an audio or vendor-specific interface. */
    fun isCandidate(device: UsbDevice): Boolean =
        interfacesOf(device).any {
            it.interfaceClass == UsbConstants.USB_CLASS_AUDIO ||
                it.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
        }

    /** Open + claim the device (call after the user granted USB permission). */
    fun open(device: UsbDevice): Boolean {
        close()
        val conn = usbManager.openDevice(device) ?: return false
        val iface = interfacesOf(device).firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_AUDIO ||
                it.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC
        } ?: interfacesOf(device).firstOrNull() ?: return false

        if (!conn.claimInterface(iface, true)) {
            conn.close()
            return false
        }
        this.device = device
        this.connection = conn
        this.claimedInterface = iface
        this.deviceName = device.productName ?: device.deviceName
        this.vendorName = VENDOR_NAMES[device.vendorId] ?: "Vendor 0x%04X".format(device.vendorId)
        this.dacChip = null
        return true
    }

    private fun close() {
        try {
            connection?.let { c -> claimedInterface?.let { c.releaseInterface(it) } }
        } catch (_: Exception) {
        }
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
        claimedInterface = null
        device = null
    }

    override fun connect(): Boolean = connection != null

    override fun disconnect() = close()

    override fun setVolume(volume: Int) {
        requireConnection()
        IemProtocol.sendVendor(connection!!, IemProtocol.REQ_SET_VOLUME, volume.coerceIn(0, 100))
    }

    override fun setMuted(muted: Boolean) {
        requireConnection()
        IemProtocol.sendVendor(connection!!, IemProtocol.REQ_SET_MUTE, if (muted) 1 else 0)
    }

    override fun setGainDb(gainDb: Float) {
        requireConnection()
        IemProtocol.sendVendor(connection!!, IemProtocol.REQ_SET_GAIN, IemProtocol.encodeGainStep(gainDb))
    }

    override fun setLed(enabled: Boolean) {
        requireConnection()
        IemProtocol.sendVendor(connection!!, IemProtocol.REQ_SET_LED, if (enabled) 1 else 0)
    }

    override fun setBandGain(band: Int, gainDb: Float) {
        requireConnection()
        IemProtocol.sendVendor(
            connection!!, IemProtocol.REQ_SET_EQ_BAND,
            IemProtocol.encodeGainStep(gainDb), band.coerceIn(0, 9)
        )
    }

    override fun setBitDepth(depth: Int) {
        requireConnection()
        IemProtocol.sendVendor(connection!!, IemProtocol.REQ_SET_BIT_DEPTH, depth)
    }

    override fun setSampleRate(rate: Int) {
        requireConnection()
        // 1) Prefer the USB Audio Class sampling-frequency control on the
        //    isochronous OUT endpoint; 2) fall back to the vendor command.
        val streamEp = audioStreamOutEndpoint()
        val uacOk = streamEp != null &&
            UsbAudioClass.setSamplingFrequency(connection!!, streamEp.address, rate)
        if (!uacOk) {
            IemProtocol.sendVendor(connection!!, IemProtocol.REQ_SET_SAMPLE_RATE, rate)
        }
    }

    override fun fetchStatus(): DeviceStatus? {
        val conn = connection ?: return null
        return try {
            IemProtocol.readStatus(conn)?.let { IemProtocol.parseStatus(it) }
        } catch (_: Exception) {
            null // polling must never crash the loop
        }
    }

    override fun deviceLabel(): String? = deviceName

    private fun requireConnection() {
        if (connection == null) throw IOException("USB device is not connected")
    }

    /** UsbDevice only exposes indexed access to its interfaces. */
    private fun interfacesOf(device: UsbDevice): List<UsbInterface> =
        (0 until device.interfaceCount).map { device.getInterface(it) }

    private fun audioStreamOutEndpoint(): UsbEndpoint? {
        val iface = claimedInterface ?: return null
        return (0 until iface.endpointCount).map { iface.getEndpoint(it) }.firstOrNull { ep ->
            ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC &&
                ep.direction == UsbConstants.USB_DIR_OUT
        }
    }
}
