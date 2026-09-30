# OpenFit

A simple, offline workout log for Android, in the spirit of FitNotes and Verifit.

## Features

- **Log sets by day.** Each day is a workout: add exercises and log weight × reps for each set. Use the arrows to step between days, or tap the date to jump anywhere.
- **Weight and ratio.** Every set has a *weight* (the number on the stack or the bar) and a *ratio* (default `1`). OpenFit shows the **calculated weight** = weight × ratio, which is handy for pulley machines (2:1), single-arm handles (0.5), and so on.
- **kg or lb per workout.** Each workout has its own unit, switchable from the Workout screen or while logging. Sets are stored exactly as entered and converted for display, so switching back and forth doesn't lose precision. The default unit for new workouts is set in Settings.
- **Prefill from your last set.** When you open an exercise, the weight, ratio and reps come from the last set you did of it, converted to the current workout's unit. The screen also shows which day those numbers came from.
- **Presets.** Save workout days such as "Push", "Pull" or "Legs" and load one to add all of its exercises to a day in one tap. Build presets from scratch, or save any logged day as a preset.
- **GitHub-style calendar.** Every day you trained is filled in, and busier days are darker. The calendar has **Month**, **Year** and **Lifetime** views, plus current, best and weekly streaks. Tap a day to open it.
- **Exercise history.** Every past session of an exercise, with an estimated 1RM per day.
- **Spreadsheet export and import.** Settings → *Backup & spreadsheet*:
  - **Save spreadsheet** writes everything to an `.xlsx` file. Pick *Drive* in the file picker to upload it straight to Google Drive.
  - **Share spreadsheet** sends the `.xlsx` to Drive, Sheets, email or any other app.
  - **Import spreadsheet** loads an OpenFit spreadsheet back into the app, including one opened or edited in Google Sheets or Excel. Import only adds data and never deletes: exercises and presets are matched by name, and an exercise that already has sets on a given day keeps them. Importing the same file twice is harmless. Use this to move your log to a new phone, or to keep it across reinstalls.
- A starter library of about 60 common exercises, all of which can be edited, deleted or added to.

## Getting the app

Every push builds the app on GitHub Actions. Open the latest **Build** run under the repo's *Actions* tab and download the `openfit-apk` artifact. Then install `app-release.apk` (or `app-debug.apk`) on your phone; you'll need to allow installs from unknown sources.

> The release APK is signed with the debug key so it can be sideloaded as-is. Set up a real signing config before publishing it to a store.

## Spreadsheet format

The exported workbook has three sheets. The first row of each is a header:

| Sheet | Columns |
| --- | --- |
| **Sets** (one row per set) | Date, Exercise, Category, Set, Weight, Unit, Ratio, Calculated Weight, Reps, Workout Unit |
| **Presets** (one row per exercise in a preset) | Preset, Order, Exercise, Category |
| **Exercises** (the exercise library) | Exercise, Category |

To import a sheet you made yourself, you only need a **Sets** sheet with *Date* (a real date cell, or text like `2026-09-30`), *Exercise*, *Weight* and *Reps* columns, in any order. Other columns are optional:
- *Ratio* defaults to 1.
- *Unit* accepts `kg` or `lb` and defaults to the day's unit.
- *Calculated Weight* is ignored on import.
- A row with no weight and no reps adds the exercise to that day without logging a set.

Rows that can't be read are skipped, and the import summary lists them.

## Building locally

Requirements: JDK 17+ and the Android SDK (API 35), for example through Android Studio.

```bash
./gradlew :core:test            # pure-Kotlin unit tests (units, ratios, heatmap, streaks, spreadsheets)
./gradlew :app:testDebugUnitTest # Robolectric tests: database, backup round trip, app smoke test
./gradlew :app:assembleDebug    # builds app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

| Module | What's in it |
| --- | --- |
| `core/` | Plain Kotlin, no Android: kg/lb conversion, ratio and calculated-weight math, number formatting, heatmap grids (month/year/lifetime), streaks, and the `.xlsx` backup format (a small dependency-free reader and writer). Fully unit-tested. |
| `app/` | The Android app: Jetpack Compose + Material 3 UI, Room database, DataStore settings. |

Inside `app/`:

- `data/`: Room entities (`Exercise`, `Workout`, `WorkoutExercise`, `SetEntry`, `Preset`, `PresetExercise`), DAOs, `WorkoutRepository`, and `SettingsRepository`.
- `ui/workout`: the day view (date navigation, unit toggle, exercise cards, load/save presets).
- `ui/log`: logging sets for one exercise (weight / ratio / calculated weight / reps, prefill, history).
- `ui/exercises`: exercise picker and library management.
- `ui/presets`: preset list and editor.
- `ui/calendar`: the heatmap calendar and streak stats.
- `ui/settings`: default unit, weight step sizes, first day of the week, spreadsheet save/share/import.
- `data/BackupManager`: moves data between the database and `.xlsx` files via the system file picker and share sheet.
