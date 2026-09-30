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

    private var today = LocalDate.now()

    /** The day shown on the Workout tab; the calendar and presets tabs move it. */
    val selectedDate = MutableStateFlow(today)

    /**
     * Called when the app comes back to the foreground. If the date rolled over while the
     * app sat in memory and the user was looking at "today", follow along to the new day.
     */
    fun onForeground() {
        val now = LocalDate.now()
        if (now == today) return
        if (selectedDate.value == today) selectedDate.value = now
        today = now
    }
}
