# OpenFit

A simple, offline workout log for Android, in the spirit of FitNotes and Verifit.

## Features

- **Log sets by day.** Each day is a workout: add exercises and log weight × reps for each set. Use the arrows to step between days, or tap the date to jump anywhere.
- **Cardio and timed exercises.** Each exercise tracks *Reps*, *Time*, *Distance* or *Distance + time* (metres, km or mi), and its weight can follow the workout's kg/lb switch, always be kg or lb, or be *None*. A starter **Cardio** category covers running, treadmill, walking, cycling, stationary bike, rowing, swimming, elliptical, stair climber and jump rope, plus Plank under Core. Distance + time sets show your pace.
- **Your own categories.** Add, rename, reorder or delete categories in Settings → Categories, or create one straight from the new-exercise page. Deleting a category moves its exercises to *Other*.
- **New exercise page.** Name, category, what to track, weight setting, and "Add to today's workout" in one place. Picking a cardio-style measure switches the weight setting to *None* for you.
- **Trophies for personal records.** A small trophy appears beside a set when it's a record for that exercise:
  - **Gold**: the heaviest weight you've done.
  - **Silver**: ties your heaviest weight with more reps than before.
  - **Bronze**: ties your heaviest weight *and* your best reps at it.
  - **Blue**: not your heaviest, but the most reps you've done at that weight.

  Records compare the **calculated weight** in either unit, so 50 kg at a 2× ratio is the same as 100 kg at 1× (or 220.5 lb). Each set is judged against the sets before it, so a trophy stays where it was earned. Trophies show on the Workout screen, while logging, and in the exercise's history. Change their colours in Settings → Trophies. Only rep-based exercises earn them.
- **Weight and ratio.** Every set has a *weight* (the number on the stack or the bar) and a *ratio* (default `1`). OpenFit shows the **calculated weight** = weight × ratio, which is handy for pulley machines (2:1), single-arm handles (0.5), and so on.
- **kg or lb per workout.** Each workout has its own unit, switchable from the Workout screen or while logging. Sets are stored exactly as entered and converted for display, so switching back and forth doesn't lose precision. The default unit for new workouts is set in Settings.
- **Prefill from your last set.** When you open an exercise, the weight, ratio and reps come from the last set you did of it, converted to the current workout's unit. The screen also shows which day those numbers came from.
- **Presets.** Save workout days such as "Push", "Pull" or "Legs" and load one to add all of its exercises to a day in one tap. Build presets from scratch, or save any logged day as a preset.
- **GitHub-style calendar.** Every day you trained is filled in, and busier days are darker. The calendar has **Month**, **Year** and **Lifetime** views. Tap a day to open it.
- **Weekly-goal streak.** Set how many workouts a week you're aiming for (Settings → Weekly goal). The streak counts your workouts and never goes up on rest days. You can rest up to 7 − goal days in each week without losing it; rest more than that and it starts over. Today doesn't count as a rest day until it's over. The calendar shows your streak, best streak, this week's progress and how many rest days you have left.
- **Accent colour.** Pick green, teal, blue, purple, pink, red or orange, or follow your wallpaper colours on Android 12+. The calendar heatmap uses the same colour.
- **Exercise history.** Every past session of an exercise, with an estimated 1RM per day.
- **Spreadsheet export and import.** Settings → *Backup & spreadsheet*:
  - **Save spreadsheet** writes everything to an `.xlsx` file. Pick *Drive* in the file picker to upload it straight to Google Drive.
  - **Share spreadsheet** sends the `.xlsx` to Drive, Sheets, email or any other app.
  - **Import spreadsheet** loads an OpenFit spreadsheet back into the app, including one opened or edited in Google Sheets or Excel. Import only adds data and never deletes: exercises and presets are matched by name, and an exercise that already has sets on a given day keeps them. Importing the same file twice is harmless. If the file uses categories the app doesn't have, you choose whether to add them or file those exercises under *Other*. Use this to move your log to a new phone, or to keep it across reinstalls.
- A starter library of about 80 common exercises (including machine and cable variations like the Bayesian Curl, Hack Squat, Rotary Torso and Wide Grip Cable Rows), all of which can be edited, deleted or added to. Updating from an older version adds the new ones and renames a few old starter names to match (for example *Tricep Pushdown* → *Triceps Pushdown*, *Pec Deck* → *Seated Machine Fly*), keeping their history. Exercises you've deleted aren't brought back, and a rename is skipped if you already have an exercise with the new name. Importing a spreadsheet saved with the old names puts those sets on the renamed exercises.

## Getting the app

Every push builds the app on GitHub Actions. Open the latest **Build** run under the repo's *Actions* tab and download the `openfit-apk` artifact. Then install `app-release.apk` (or `app-debug.apk`) on your phone; you'll need to allow installs from unknown sources.

> The release APK is signed with the debug key so it can be sideloaded as-is. Set up a real signing config before publishing it to a store.

## Spreadsheet format

The exported workbook has three sheets. The first row of each is a header:

| Sheet | Columns |
| --- | --- |
| **Sets** (one row per set) | Date, Exercise, Category, Set, Weight, Unit, Ratio, Calculated Weight, Reps, Time, Distance, Distance Unit, Workout Unit |
| **Presets** (one row per exercise in a preset) | Preset, Order, Exercise, Category |
| **Exercises** (the exercise library) | Exercise, Category, Measure, Weight |
| **Categories** (in display order) | Category |

To import a sheet you made yourself, you only need a **Sets** sheet with *Date* (a real date cell, or text like `2026-09-30`), *Exercise*, and at least one of *Reps*, *Time* or *Distance*, in any order. Other columns are optional:
- *Weight* defaults to 0.
- *Ratio* defaults to 1.
- *Unit* accepts `kg` or `lb` and defaults to the day's unit.
- *Time* accepts `25:30` or `1:02:03`; a plain number means minutes.
- *Distance Unit* accepts `m`, `km` or `mi` and defaults to km.
- *Calculated Weight* is ignored on import.
- A row with no weight, reps, time or distance adds the exercise to that day without logging a set.

Without an **Exercises** sheet, an exercise's type is worked out from its sets (for example, time and distance but no reps means *Distance + time*). Spreadsheets saved by OpenFit 0.1 import as they are.

Rows that can't be read are skipped, and the import summary lists them.

## Building locally

Requirements: JDK 17+ and the Android SDK (API 35), for example through Android Studio.

```bash
./gradlew :core:test            # pure-Kotlin unit tests (units, ratios, heatmap, streaks, spreadsheets)
./gradlew :app:testDebugUnitTest # Robolectric tests: database, migrations, trophies, backup round trip, app smoke tests
./gradlew :app:assembleDebug    # builds app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

| Module | What's in it |
| --- | --- |
| `core/` | Plain Kotlin, no Android: kg/lb and m/km/mi conversion, ratio and calculated-weight math, durations and pace, set descriptions, heatmap grids (month/year/lifetime), the weekly-goal streak, trophy (personal record) rules, accent-colour palette generation, and the `.xlsx` backup format (a small dependency-free reader and writer). Fully unit-tested. |
| `app/` | The Android app: Jetpack Compose + Material 3 UI, Room database, DataStore settings. |

Inside `app/`:

- `data/`: Room entities (`Exercise`, `Category`, `Workout`, `WorkoutExercise`, `SetEntry`, `Preset`, `PresetExercise`), DAOs, database migrations, `WorkoutRepository`, and `SettingsRepository`.
- `ui/workout`: the day view (date navigation, unit toggle, exercise cards, load/save presets).
- `ui/log`: logging sets for one exercise (weight / ratio / calculated weight / reps, prefill, history).
- `ui/exercises`: exercise picker, the new/edit exercise page, and category management.
- `ui/presets`: preset list and editor.
- `ui/calendar`: the heatmap calendar and streak stats.
- `ui/settings`: accent colour, trophy colours, weekly goal, units, first day of the week, spreadsheet save/share/import.
- `data/BackupManager`: moves data between the database and `.xlsx` files via the system file picker and share sheet.
