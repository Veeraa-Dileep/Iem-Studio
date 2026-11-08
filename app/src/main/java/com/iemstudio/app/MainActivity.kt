package com.iemstudio.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.iemstudio.app.ui.navigation.IemApp
import com.iemstudio.app.ui.theme.IemTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as IemApp).container }
    private val controller get() = container.controller

    // Bluetooth runtime permissions (Android 12+): SCAN + CONNECT
    private val btPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.none { it }) {
            controller.showError("Bluetooth permissions denied")
        }
    }

    // Result of the USB permission dialog (system broadcast)
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == IemController.ACTION_USB_PERMISSION) {
                val device = usbDeviceFrom(intent)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                controller.onUsbPermissionResult(granted, device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerUsbReceiver()
        handleIntent(intent)
        setContent {
            IemTheme {
                IemApp(
                    controller = controller,
                    onRequestBtPermissions = { btPermissionLauncher.launch(btPermissions()) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                controller.onUsbAttached(usbDeviceFrom(intent))

            UsbManager.ACTION_USB_DEVICE_DETACHED ->
                controller.onUsbDetached(usbDeviceFrom(intent))
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter(IemController.ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    private fun usbDeviceFrom(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    companion object {
        fun btPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
