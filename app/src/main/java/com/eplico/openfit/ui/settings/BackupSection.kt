package com.eplico.openfit.ui.settings

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eplico.openfit.core.backup.Xlsx
import com.eplico.openfit.data.BackupManager
import com.eplico.openfit.data.ImportResult
import com.eplico.openfit.data.ImportSummary
import com.eplico.openfit.ui.common.AppIcons

/** Save / share / import the whole log as an .xlsx spreadsheet. */
@Composable
fun BackupSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Xlsx.MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    val shareUri = viewModel.shareUri
    LaunchedEffect(shareUri) {
        if (shareUri != null) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = Xlsx.MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, shareUri)
                clipData = ClipData.newRawUri(null, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share workout spreadsheet"))
            viewModel.shareHandled()
        }
    }

    Column {
        if (viewModel.busy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        BackupItem(
            title = "Save spreadsheet",
            description = "Export every workout, preset and exercise as an .xlsx file. Choose Google Drive in the picker to upload it.",
            icon = AppIcons.Download,
            enabled = !viewModel.busy,
            onClick = { saveLauncher.launch(viewModel.suggestedFileName()) },
        )
        BackupItem(
            title = "Share spreadsheet",
            description = "Send the .xlsx to Google Drive, Sheets, email or any other app.",
            icon = Icons.Filled.Share,
            enabled = !viewModel.busy,
            onClick = viewModel::share,
        )
        BackupItem(
            title = "Import spreadsheet",
            description = "Load workouts and presets from an OpenFit spreadsheet, including one saved to Drive or edited in Sheets. " +
                "Anything already in the app is kept.",
            icon = AppIcons.Upload,
            enabled = !viewModel.busy,
            onClick = { importLauncher.launch(BackupManager.IMPORT_MIME_TYPES) },
        )
    }

    viewModel.importResult?.let { result ->
        ImportResultDialog(result, onDismiss = viewModel::importResultShown)
    }
}

@Composable
private fun BackupItem(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ImportResultDialog(result: ImportResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import finished") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                summaryLines(result.summary).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
                if (result.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val count = result.warnings.size
                    Text(
                        if (count == 1) "1 row couldn't be read and was skipped:" else "$count rows couldn't be read and were skipped:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    result.warnings.take(MAX_WARNINGS_SHOWN).forEach { warning ->
                        Text(
                            "• $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (count > MAX_WARNINGS_SHOWN) {
                        Text(
                            "…and ${count - MAX_WARNINGS_SHOWN} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private const val MAX_WARNINGS_SHOWN = 8

private fun plural(count: Int, one: String, many: String = "${one}s") = if (count == 1) "1 $one" else "$count $many"

internal fun summaryLines(summary: ImportSummary): List<String> {
    val lines = ArrayList<String>()
    if (summary.setsAdded > 0) {
        lines += "Added ${plural(summary.setsAdded, "set")} across ${plural(summary.daysWithNewSets, "day")}."
    }
    if (summary.presetsAdded > 0) lines += "Added ${plural(summary.presetsAdded, "preset")}."
    if (summary.exercisesAdded > 0) lines += "Added ${plural(summary.exercisesAdded, "new exercise")}."
    if (lines.isEmpty()) lines += "Nothing new to add. Everything in this file is already in the app."
    if (summary.exerciseDaysSkipped > 0) {
        lines += "Kept your existing sets for ${plural(summary.exerciseDaysSkipped, "exercise-day")} that were already logged in the app."
    }
    if (summary.presetsSkipped > 0) {
        lines += "Kept ${plural(summary.presetsSkipped, "existing preset")} with the same name."
    }
    return lines
}
