package com.iemstudio.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iemstudio.app.data.model.EQ_BAND_COUNT
import com.iemstudio.app.data.model.EqPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "iem_studio_settings")

/** Persisted user preferences, observed as a single [Flow]. */
data class AppSettings(
    val demoMode: Boolean = true,
    val autoConnect: Boolean = true,
    val volumeSteps: Int = 20,
    val ledEnabled: Boolean = true,
    val btNamePrefix: String = "IEM-",
    val btServiceUuid: String = "0000FEE0-0000-1000-8000-00805f9b34fb",
    val btCharUuid: String = "0000FEE1-0000-1000-8000-00805f9b34fb",
    val customPresets: List<EqPreset> = emptyList(),
    val lastCurve: List<Float>? = null,
    val lastVolume: Int = 60,
)

/** Jetpack DataStore-backed repository for all app preferences. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val VOLUME_STEPS = intPreferencesKey("volume_steps")
        val LED_ENABLED = booleanPreferencesKey("led_enabled")
        val BT_PREFIX = stringPreferencesKey("bt_prefix")
        val BT_SERVICE_UUID = stringPreferencesKey("bt_service_uuid")
        val BT_CHAR_UUID = stringPreferencesKey("bt_char_uuid")
        val CUSTOM_PRESETS_JSON = stringPreferencesKey("custom_presets_json")
        val LAST_CURVE_JSON = stringPreferencesKey("last_curve_json")
        val LAST_VOLUME = intPreferencesKey("last_volume")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            demoMode = p[Keys.DEMO_MODE] ?: true,
            autoConnect = p[Keys.AUTO_CONNECT] ?: true,
            volumeSteps = p[Keys.VOLUME_STEPS] ?: 20,
            ledEnabled = p[Keys.LED_ENABLED] ?: true,
            btNamePrefix = p[Keys.BT_PREFIX] ?: "IEM-",
            btServiceUuid = p[Keys.BT_SERVICE_UUID] ?: "0000FEE0-0000-1000-8000-00805f9b34fb",
            btCharUuid = p[Keys.BT_CHAR_UUID] ?: "0000FEE1-0000-1000-8000-00805f9b34fb",
            customPresets = p[Keys.CUSTOM_PRESETS_JSON]?.let(::presetsFromJson) ?: emptyList(),
            lastCurve = p[Keys.LAST_CURVE_JSON]?.let(::curveFromJson),
            lastVolume = p[Keys.LAST_VOLUME] ?: 60,
        )
    }

    suspend fun setDemoMode(enabled: Boolean) = edit { it[Keys.DEMO_MODE] = enabled }

    suspend fun setAutoConnect(enabled: Boolean) = edit { it[Keys.AUTO_CONNECT] = enabled }

    suspend fun setVolumeSteps(steps: Int) = edit { it[Keys.VOLUME_STEPS] = steps }

    suspend fun setLedEnabled(enabled: Boolean) = edit { it[Keys.LED_ENABLED] = enabled }

    suspend fun setBtNamePrefix(prefix: String) = edit { it[Keys.BT_PREFIX] = prefix }

    suspend fun setBtUuids(service: String, characteristic: String) = edit {
        it[Keys.BT_SERVICE_UUID] = service
        it[Keys.BT_CHAR_UUID] = characteristic
    }

    suspend fun saveCustomPreset(preset: EqPreset) {
        edit { p ->
            val current = p[Keys.CUSTOM_PRESETS_JSON]?.let(::presetsFromJson) ?: emptyList()
            p[Keys.CUSTOM_PRESETS_JSON] = presetsToJson(current.filterNot { it.id == preset.id } + preset)
        }
    }

    suspend fun deleteCustomPreset(id: String) {
        edit { p ->
            val current = p[Keys.CUSTOM_PRESETS_JSON]?.let(::presetsFromJson) ?: emptyList()
            p[Keys.CUSTOM_PRESETS_JSON] = presetsToJson(current.filterNot { it.id == id })
        }
    }

    suspend fun saveLastCurve(gains: List<Float>) {
        edit { it[Keys.LAST_CURVE_JSON] = gains.joinToString(",") { g -> g.toString() } }
    }

    suspend fun saveLastVolume(volume: Int) {
        edit { it[Keys.LAST_VOLUME] = volume.coerceIn(0, 100) }
    }

    suspend fun resetAll() = edit { it.clear() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    // ── JSON (de)serialization for presets ──────────────────────────────────

    private fun presetsToJson(presets: List<EqPreset>): String =
        JSONArray().apply {
            presets.forEach { p ->
                put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("gains", JSONArray(p.gains))
                )
            }
        }.toString()

    private fun presetsFromJson(json: String): List<EqPreset> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val gains = obj.getJSONArray("gains")
            EqPreset(
                id = obj.getString("id"),
                name = obj.getString("name"),
                gains = List(gains.length()) { j -> gains.getDouble(j).toFloat() },
                isCustom = true,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun curveFromJson(json: String): List<Float>? = try {
        json.split(",").map { it.trim().toFloat() }.take(EQ_BAND_COUNT)
    } catch (_: Exception) {
        null
    }
}
