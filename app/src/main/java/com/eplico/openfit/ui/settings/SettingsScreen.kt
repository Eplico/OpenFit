@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eplico.openfit.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.core.AccentColor
import com.eplico.openfit.core.DistanceUnit
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.ChoiceToggle
import com.eplico.openfit.ui.common.UnitToggle
import com.eplico.openfit.ui.theme.supportsWallpaperColors
import java.time.DayOfWeek

private val incrementOptions = mapOf(
    WeightUnit.KG to listOf(0.5, 1.0, 1.25, 2.5, 5.0),
    WeightUnit.LB to listOf(1.0, 2.5, 5.0, 10.0),
)

@Composable
fun SettingsScreen(
    onManageCategories: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val settings = loaded ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Appearance")
            ListItem(
                headlineContent = { Text("Accent colour") },
                supportingContent = {
                    AccentPicker(
                        selected = settings.accent,
                        onSelect = viewModel::setAccent,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Trophies")
            TrophySection(settings, viewModel)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Weekly goal")
            ListItem(
                headlineContent = { Text("Workouts per week") },
                supportingContent = {
                    Column {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..7).forEach { goal ->
                                FilterChip(
                                    selected = settings.weeklyGoal == goal,
                                    onClick = { viewModel.setWeeklyGoal(goal) },
                                    label = { Text("$goal") },
                                )
                            }
                        }
                        val restDays = 7 - settings.weeklyGoal
                        Text(
                            when (restDays) {
                                0 -> "Every day counts: any rest day resets your streak."
                                1 -> "Rest 1 day a week without breaking your streak."
                                else -> "Rest up to $restDays days a week without breaking your streak."
                            },
                        )
                    }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Units")
            ListItem(
                headlineContent = { Text("Default unit") },
                supportingContent = { Text("Used for new workouts. You can still switch any single workout between kg and lb.") },
                trailingContent = {
                    UnitToggle(
                        unit = settings.defaultUnit,
                        onUnitChange = viewModel::setDefaultUnit,
                        modifier = Modifier.width(132.dp),
                    )
                },
            )
            WeightUnit.entries.forEach { unit ->
                ListItem(
                    headlineContent = { Text("Weight step (${unit.label})") },
                    supportingContent = {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            incrementOptions.getValue(unit).forEach { option ->
                                FilterChip(
                                    selected = settings.increment(unit) == option,
                                    onClick = { viewModel.setIncrement(unit, option) },
                                    label = { Text(WeightMath.format(option)) },
                                )
                            }
                        }
                    },
                )
            }

            ListItem(
                headlineContent = { Text("Distance unit") },
                supportingContent = { Text("For new cardio sets.") },
                trailingContent = {
                    ChoiceToggle(
                        options = DistanceUnit.entries,
                        selected = settings.distanceUnit,
                        label = { it.label },
                        onSelect = viewModel::setDistanceUnit,
                        modifier = Modifier.width(156.dp),
                    )
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Exercises")
            ListItem(
                headlineContent = { Text("Categories") },
                supportingContent = { Text("Add, rename, reorder or delete exercise categories.") },
                modifier = Modifier.clickable(onClick = onManageCategories),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Calendar")
            ListItem(
                headlineContent = { Text("Week starts on") },
                trailingContent = {
                    val options = listOf(DayOfWeek.MONDAY to "Mon", DayOfWeek.SUNDAY to "Sun")
                    SingleChoiceSegmentedButtonRow(Modifier.width(132.dp)) {
                        options.forEachIndexed { index, (day, label) ->
                            SegmentedButton(
                                selected = settings.weekStart == day,
                                onClick = { viewModel.setWeekStart(day) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                icon = {},
                            ) {
                                Text(label)
                            }
                        }
                    }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Backup & spreadsheet")
            BackupSection(viewModel)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("About ratios")
            Text(
                "Some machines don't move the weight shown on the stack: a 2:1 pulley doubles it, " +
                    "a single-arm cable handle might halve it. Enter the number on the machine as the weight " +
                    "and set the ratio, and OpenFit shows the calculated weight (weight × ratio) next to it. " +
                    "The ratio defaults to 1, and each exercise remembers the ratio you used last.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val options = AccentColor.entries.filter { it.seed != null || supportsWallpaperColors }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        options.forEach { accent ->
            val swatch = accent.seed?.let(::Color)
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context).primary else Color.Gray
            val isSelected = accent == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(accent) })
                    .padding(4.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                        ),
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                        )
                    }
                }
                Text(accent.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
