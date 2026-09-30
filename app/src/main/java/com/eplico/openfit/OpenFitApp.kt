package com.eplico.openfit

import android.app.Application
import android.content.Context
import com.eplico.openfit.data.OpenFitDatabase
import com.eplico.openfit.data.SettingsRepository
import com.eplico.openfit.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class OpenFitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container shared by every screen. */
class AppContainer(context: Context) {
    private val database = OpenFitDatabase.build(context)
    val settings = SettingsRepository(context)
    val repository = WorkoutRepository(database, settings)

    /** The day shown on the Workout tab; the calendar and presets tabs move it. */
    val selectedDate = MutableStateFlow(LocalDate.now())
}
