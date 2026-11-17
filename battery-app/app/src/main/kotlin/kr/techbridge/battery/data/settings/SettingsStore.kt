package kr.techbridge.battery.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kr.techbridge.battery.core.Calibration

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

data class Settings(
    val monitoringEnabled: Boolean = true,
    val chargeLimitPercent: Int = 80,
    val chargeLimitEnabled: Boolean = true,
    val tempWarnEnabled: Boolean = true,
    val tempWarnDeciC: Int = 400,
    val ongoingNotification: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val designCapacityMah: Int? = null,
    /** null이면 아직 보정 전. 그동안은 [Calibration.SPEC]을 쓴다. */
    val calibration: Calibration? = null,
    val calibrationCheckedMs: Long? = null,
    val lastChargerId: Long? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val monitoring = booleanPreferencesKey("monitoring_enabled")
        val limit = intPreferencesKey("charge_limit")
        val limitEnabled = booleanPreferencesKey("charge_limit_enabled")
        val tempEnabled = booleanPreferencesKey("temp_warn_enabled")
        val tempDeciC = intPreferencesKey("temp_warn_deci_c")
        val ongoing = booleanPreferencesKey("ongoing_notification")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val designCapacity = intPreferencesKey("design_capacity_mah")
        val calScale = intPreferencesKey("cal_scale")
        val calFlip = booleanPreferencesKey("cal_flip")
        val calChecked = longPreferencesKey("cal_checked_ms")
        val lastCharger = longPreferencesKey("last_charger_id")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        val scale = p[Keys.calScale]
        Settings(
            monitoringEnabled = p[Keys.monitoring] ?: true,
            chargeLimitPercent = p[Keys.limit] ?: 80,
            chargeLimitEnabled = p[Keys.limitEnabled] ?: true,
            tempWarnEnabled = p[Keys.tempEnabled] ?: true,
            tempWarnDeciC = p[Keys.tempDeciC] ?: 400,
            ongoingNotification = p[Keys.ongoing] ?: true,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: true,
            designCapacityMah = p[Keys.designCapacity],
            calibration = scale?.let { Calibration(it, p[Keys.calFlip] ?: false) },
            calibrationCheckedMs = p[Keys.calChecked],
            lastChargerId = p[Keys.lastCharger],
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun setMonitoringEnabled(v: Boolean) = edit { it[Keys.monitoring] = v }
    suspend fun setChargeLimit(percent: Int) = edit { it[Keys.limit] = percent.coerceIn(50, 100) }
    suspend fun setChargeLimitEnabled(v: Boolean) = edit { it[Keys.limitEnabled] = v }
    suspend fun setTempWarnEnabled(v: Boolean) = edit { it[Keys.tempEnabled] = v }
    suspend fun setOngoingNotification(v: Boolean) = edit { it[Keys.ongoing] = v }
    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(v: Boolean) = edit { it[Keys.dynamic] = v }
    suspend fun setDesignCapacity(mah: Int?) = edit { p ->
        if (mah == null || mah <= 0) p.remove(Keys.designCapacity) else p[Keys.designCapacity] = mah
    }
    suspend fun setLastCharger(id: Long?) = edit { p -> if (id == null) p.remove(Keys.lastCharger) else p[Keys.lastCharger] = id }

    suspend fun setCalibration(c: Calibration?, checkedMs: Long = System.currentTimeMillis()) = edit { p ->
        if (c == null) {
            p.remove(Keys.calScale); p.remove(Keys.calFlip); p.remove(Keys.calChecked)
        } else {
            p[Keys.calScale] = c.scaleToMicroAmps
            p[Keys.calFlip] = c.flipSign
            p[Keys.calChecked] = checkedMs
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
