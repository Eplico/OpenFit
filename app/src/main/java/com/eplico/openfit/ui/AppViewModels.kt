package com.eplico.openfit.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eplico.openfit.AppContainer
import com.eplico.openfit.OpenFitApp
import com.eplico.openfit.ui.calendar.CalendarViewModel
import com.eplico.openfit.ui.exercises.ExercisePickerViewModel
import com.eplico.openfit.ui.log.ExerciseLogViewModel
import com.eplico.openfit.ui.presets.PresetEditViewModel
import com.eplico.openfit.ui.presets.PresetsViewModel
import com.eplico.openfit.ui.settings.SettingsViewModel
import com.eplico.openfit.ui.workout.WorkoutViewModel

/** Builds every ViewModel from the app-wide [AppContainer]. */
object AppViewModels {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            WorkoutViewModel(c.repository, c.settings, c.selectedDate)
        }
        initializer {
            val c = container()
            ExerciseLogViewModel(createSavedStateHandle(), c.repository, c.settings)
        }
        initializer {
            ExercisePickerViewModel(createSavedStateHandle(), container().repository)
        }
        initializer {
            val c = container()
            PresetsViewModel(c.repository, c.selectedDate)
        }
        initializer {
            PresetEditViewModel(createSavedStateHandle(), container().repository)
        }
        initializer {
            val c = container()
            CalendarViewModel(c.repository, c.settings, c.selectedDate)
        }
        initializer {
            val c = container()
            SettingsViewModel(c.settings, c.backups)
        }
    }

    private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as OpenFitApp).container
}
