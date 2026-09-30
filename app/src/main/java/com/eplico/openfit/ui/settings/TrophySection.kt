@file:OptIn(ExperimentalLayoutApi::class)

package com.eplico.openfit.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.eplico.openfit.core.ColorMath
import com.eplico.openfit.core.Trophy
import com.eplico.openfit.data.UserSettings
import com.eplico.openfit.ui.common.AppIcons
import com.eplico.openfit.ui.common.TrophyBadge

/** Colours offered for trophies, starting with the four defaults. Any hex colour can be typed too. */
private val trophySwatches: List<Int> = Trophy.entries.map { it.defaultColor } + listOf(
    0xFFFFD54F, 0xFF78909C, 0xFF8D6E63, 0xFF00ACC1,
    0xFF43A047, 0xFF7CB342, 0xFFE53935, 0xFFD81B60,
    0xFF8E24AA, 0xFF5E35B1, 0xFFFB8C00, 0xFF212121,
).map { it.toInt() }

/** The trophy legend, where each trophy's colour can be changed. */
@Composable
fun TrophySection(settings: UserSettings, viewModel: SettingsViewModel) {
    var editing by rememberSaveable { mutableStateOf<Trophy?>(null) }
    Trophy.entries.forEach { trophy ->
        ListItem(
            leadingContent = { TrophyBadge(trophy, size = 28.dp) },
            headlineContent = { Text(trophy.label) },
            supportingContent = { Text(trophy.meaning) },
            modifier = Modifier.clickable(onClickLabel = "Change ${trophy.label.lowercase()} colour") { editing = trophy },
        )
    }
    Text(
        "Trophies compare the calculated weight (weight × ratio, in either unit), so 50 kg at a 2× ratio " +
            "ties 100 kg at 1×. Only rep-based exercises earn them. Tap a trophy to change its colour.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    if (settings.trophyColors.isNotEmpty()) {
        TextButton(onClick = viewModel::resetTrophyColors, modifier = Modifier.padding(start = 4.dp)) {
            Text("Reset trophy colours")
        }
    }

    editing?.let { trophy ->
        TrophyColorDialog(
            trophy = trophy,
            initial = settings.trophyColor(trophy),
            onPick = { argb ->
                viewModel.setTrophyColor(trophy, argb.takeIf { it != trophy.defaultColor })
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TrophyColorDialog(trophy: Trophy, initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var color by rememberSaveable { mutableIntStateOf(initial) }
    var hexText by rememberSaveable { mutableStateOf(ColorMath.toHex(initial)) }
    val typed = remember(hexText) { ColorMath.parseHex(hexText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Trophy, contentDescription = null, tint = Color(color), modifier = Modifier.size(40.dp)) },
        title = { Text("${trophy.label} trophy colour") },
        text = {
            Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    trophySwatches.forEach { swatch ->
                        ColorSwatch(
                            argb = swatch,
                            selected = swatch == color,
                            onClick = {
                                color = swatch
                                hexText = ColorMath.toHex(swatch)
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { text ->
                        hexText = text.take(7)
                        ColorMath.parseHex(hexText)?.let { color = it }
                    },
                    label = { Text("Hex colour") },
                    singleLine = true,
                    isError = typed == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color) }, enabled = typed != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(trophy.defaultColor) }) { Text("Default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ColorSwatch(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(argb)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(swatch)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = ColorMath.toHex(argb),
                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}
