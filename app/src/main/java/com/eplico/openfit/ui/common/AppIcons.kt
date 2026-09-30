package com.eplico.openfit.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material icons that aren't part of material-icons-core. */
object AppIcons {
    /** "fitness_center" (a dumbbell). */
    val Dumbbell: ImageVector by lazy {
        icon(
            "Dumbbell",
            "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 " +
                "5.57,4.14 4.14,2.71 2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 " +
                "15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 " +
                "-1.43,-1.43L22,16.29z",
        )
    }

    /** "file_download". */
    val Download: ImageVector by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }

    /** "file_upload". */
    val Upload: ImageVector by lazy { icon("Upload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }

    /** "emoji_events" (a trophy cup). */
    val Trophy: ImageVector by lazy {
        icon(
            "Trophy",
            "M19,5h-2V3H7v2H5C3.9,5 3,5.9 3,7v1c0,2.55 1.92,4.63 4.39,4.94c0.63,1.5 1.98,2.63 3.61,2.96V19H7v2h10v-2h-4v-3.1" +
                "c1.63,-0.33 2.98,-1.46 3.61,-2.96C19.08,12.63 21,10.55 21,8V7C21,5.9 20.1,5 19,5zM5,8V7h2v3.82C5.84,10.4 5,9.3 5,8z" +
                "M19,8c0,1.3 -0.84,2.4 -2,2.82V7h2V8z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()
}
