package com.iemstudio.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iemstudio.app.IemApp
import com.iemstudio.app.IemController
import com.iemstudio.app.data.model.BtDevice
import com.iemstudio.app.data.model.DeviceUiState
import com.iemstudio.app.data.model.EqPreset
import com.iemstudio.app.data.model.EqPresets
import com.iemstudio.app.data.prefs.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** ViewModels are thin adapters over the app-level [IemController]. */

class HomeViewModel(private val controller: IemController) : ViewModel() {

    val uiState: StateFlow<DeviceUiState> = controller.state

    val customPresets: Flow<List<EqPreset>> =
        controller.settingsRepository.settings.map { it.customPresets }

    val volumeSteps: Flow<Int> =
        controller.settingsRepository.settings.map { it.volumeSteps }

    fun requestUsbPermission() = controller.requestUsbPermission()
    fun disconnect() = controller.disconnect()

    fun setVolume(volume: Int) = controller.setVolume(volume)
    fun stepVolume(delta: Int) = controller.stepVolume(delta)
    fun toggleMute() = controller.toggleMute()

    fun applyPreset(preset: EqPreset) = controller.applyPreset(preset)

    fun isBluetoothSupported(): Boolean = controller.isBluetoothSupported()
    fun isBluetoothEnabled(): Boolean = controller.isBluetoothEnabled()
    fun hasBtPermissions(): Boolean = controller.hasBtPermissions()
    fun scanBt() = controller.scanForBt()
    fun stopBtScan() = controller.stopBtScan()
    fun connectBt(device: BtDevice) = controller.connectBt(device)
    fun disconnectBt() = controller.disconnectBt()

    fun clearError() = controller.clearError()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel((this[APPLICATION_KEY] as IemApp).container.controller)
            }
        }
    }
}

class EqViewModel(private val controller: IemController) : ViewModel() {

    val uiState: StateFlow<DeviceUiState> = controller.state

    val presets: Flow<List<EqPreset>> =
        controller.settingsRepository.settings.map { EqPresets.BUILT_INS + it.customPresets }

    fun setBandGain(band: Int, gainDb: Float) = controller.setBandGain(band, gainDb)
    fun applyPreset(preset: EqPreset) = controller.applyPreset(preset)
    fun resetEq() = controller.resetEq()
    fun saveCustomPreset(name: String) = controller.saveCustomPreset(name)
    fun deleteCustomPreset(id: String) = controller.deleteCustomPreset(id)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EqViewModel((this[APPLICATION_KEY] as IemApp).container.controller)
            }
        }
    }
}

class OutputViewModel(private val controller: IemController) : ViewModel() {

    val uiState: StateFlow<DeviceUiState> = controller.state

    fun setSampleRate(rate: Int) = controller.setSampleRate(rate)
    fun setBitDepth(depth: Int) = controller.setBitDepth(depth)
    fun setGainDb(gainDb: Float) = controller.setGainDb(gainDb)
    fun setLed(enabled: Boolean) = controller.setLed(enabled)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                OutputViewModel((this[APPLICATION_KEY] as IemApp).container.controller)
            }
        }
    }
}

class SettingsViewModel(private val controller: IemController) : ViewModel() {

    val settings: Flow<AppSettings> = controller.settingsRepository.settings
    val uiState: StateFlow<DeviceUiState> = controller.state

    fun setDemoMode(enabled: Boolean) = controller.setDemoMode(enabled)
    fun setAutoConnect(enabled: Boolean) = controller.setAutoConnect(enabled)
    fun setVolumeSteps(steps: Int) = controller.setVolumeSteps(steps)
    fun setBtPrefix(prefix: String) = controller.setBtPrefix(prefix)
    fun setBtUuids(service: String, characteristic: String) =
        controller.setBtUuids(service, characteristic)

    fun resetAllSettings() = controller.resetAllSettings()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel((this[APPLICATION_KEY] as IemApp).container.controller)
            }
        }
    }
}
