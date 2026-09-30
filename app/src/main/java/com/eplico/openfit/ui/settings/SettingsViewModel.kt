package com.eplico.openfit.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.data.BackupManager
import com.eplico.openfit.data.ImportResult
import com.eplico.openfit.data.SettingsRepository
import com.eplico.openfit.data.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val backups: BackupManager,
) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDefaultUnit(unit: WeightUnit) {
        viewModelScope.launch { repository.setDefaultUnit(unit) }
    }

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch { repository.setWeekStart(day) }
    }

    fun setIncrement(unit: WeightUnit, value: Double) {
        viewModelScope.launch { repository.setIncrement(unit, value) }
    }

    // ---- Spreadsheet export / import ----

    /** True while an export or import is running. */
    var busy by mutableStateOf(false)
        private set

    /** One-off snackbar text. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Set when an import finishes; the screen shows a summary dialog. */
    var importResult by mutableStateOf<ImportResult?>(null)
        private set

    /** Set when a spreadsheet is ready to hand to the share sheet. */
    var shareUri by mutableStateOf<Uri?>(null)
        private set

    fun suggestedFileName(): String = backups.suggestedFileName()

    fun exportTo(uri: Uri) = runTask("Export failed") {
        val sets = backups.exportTo(uri)
        message = if (sets == 1) "Saved a spreadsheet with 1 set" else "Saved a spreadsheet with $sets sets"
    }

    fun share() = runTask("Couldn't create the spreadsheet") {
        shareUri = backups.exportForSharing()
    }

    fun importFrom(uri: Uri) = runTask("Import failed") {
        importResult = backups.importFrom(uri)
    }

    fun messageShown() {
        message = null
    }

    fun shareHandled() {
        shareUri = null
    }

    fun importResultShown() {
        importResult = null
    }

    private fun runTask(failure: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "$failure: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }
}
