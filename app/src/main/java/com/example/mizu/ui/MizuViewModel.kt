package com.example.mizu.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mizu.R
import com.example.mizu.container
import com.example.mizu.core.Bottle
import com.example.mizu.core.DrinkLog
import com.example.mizu.core.DrinkSource
import com.example.mizu.core.MizuSettings
import com.example.mizu.core.WeighCalculator
import com.example.mizu.core.WeighOutcome
import com.example.mizu.update.UpdateState
import com.example.mizu.util.localized
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MizuViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    /** Null until DataStore has loaded, so the UI never flashes the wrong language. */
    val settings: StateFlow<MizuSettings?> = c.settings.settings
        .map<MizuSettings, MizuSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val logs: StateFlow<List<DrinkLog>> = c.repository.logs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bottles: StateFlow<List<Bottle>> = c.repository.bottles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ticks so "today", the suggestion and the wave stay correct while the app stays open. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    val notificationPrompted: StateFlow<Boolean?> = c.settings.notificationPrompted
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    init {
        viewModelScope.launch {
            val language = c.settings.settings.first().language
            val name = getApplication<Application>().localized(language).getString(R.string.default_bottle_name)
            c.repository.ensureDefaultBottle(name)
            c.scheduler.reschedule()
        }
    }

    // ---- logging ----

    fun quickAdd(ml: Int) = viewModelScope.launch { c.repository.addLog(ml, DrinkSource.QUICK) }

    fun manualAdd(ml: Int) = viewModelScope.launch { c.repository.addLog(ml, DrinkSource.MANUAL) }

    fun editLog(id: Long, ml: Int) = viewModelScope.launch { c.repository.updateLogAmount(id, ml) }

    fun deleteLog(id: Long) = viewModelScope.launch { c.repository.deleteLog(id) }

    // ---- weigh ----

    fun evaluateWeigh(bottle: Bottle, totalWeightG: Int): WeighOutcome {
        val s = settings.value ?: MizuSettings()
        return WeighCalculator.evaluate(bottle.currentWaterG, totalWeightG, s.emptyWeightOf(bottle))
    }

    fun commitWeigh(bottleId: Long, outcome: WeighOutcome) =
        viewModelScope.launch { c.repository.saveWeigh(bottleId, outcome) }

    // ---- settings & bottles ----

    fun updateSettings(transform: (MizuSettings) -> MizuSettings) = viewModelScope.launch {
        c.settings.update(transform)
        c.scheduler.reschedule()
    }

    fun saveBottle(bottle: Bottle) = viewModelScope.launch { c.repository.saveBottle(bottle) }

    fun archiveBottle(id: Long) = viewModelScope.launch { c.repository.archiveBottle(id) }

    fun markNotificationPrompted() = viewModelScope.launch { c.settings.markNotificationPrompted() }

    // ---- export ----

    suspend fun buildCsv(from: LocalDate, to: LocalDate): String = c.repository.buildCsv(from, to)

    // ---- in-app update ----

    fun checkForUpdate() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            _updateState.value = try {
                c.updater.checkLatest()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun downloadUpdate() {
        val available = _updateState.value as? UpdateState.Available ?: return
        val info = available.info
        _updateState.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            _updateState.value = try {
                val file = c.updater.download(info) { p -> _updateState.value = UpdateState.Downloading(info, p) }
                UpdateState.ReadyToInstall(info, file)
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun installUpdate() {
        val ready = _updateState.value as? UpdateState.ReadyToInstall ?: return
        c.updater.install(ready.file)
    }
}
