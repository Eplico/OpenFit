@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.eplico.openfit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplico.openfit.core.WeightMath
import com.eplico.openfit.core.WeightUnit
import com.eplico.openfit.ui.AppViewModels
import com.eplico.openfit.ui.common.UnitToggle
import java.time.DayOfWeek

private val incrementOptions = mapOf(
    WeightUnit.KG to listOf(0.5, 1.0, 1.25, 2.5, 5.0),
    WeightUnit.LB to listOf(1.0, 2.5, 5.0, 10.0),
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = AppViewModels.Factory)) {
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
