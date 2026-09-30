package com.eplico.openfit.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eplico.openfit.core.Trophy
import com.eplico.openfit.ui.theme.LocalTrophyColors

/** A small trophy in the colour picked for [trophy] (see Settings). Draws nothing when [trophy] is null. */
@Composable
fun TrophyBadge(trophy: Trophy?, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    if (trophy == null) return
    Icon(
        AppIcons.Trophy,
        contentDescription = "${trophy.label} trophy: ${trophy.meaning.lowercase()}",
        tint = LocalTrophyColors.current[trophy],
        modifier = modifier.size(size),
    )
}
