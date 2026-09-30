package com.eplico.openfit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real app end to end: add an exercise, log a set, then visit every tab. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 10_000)
    }

    @Test
    fun logASetAndVisitEveryTab() {
        waitFor(hasText("Nothing logged for this day"))
        compose.onAllNodesWithText("Add exercise").onFirst().performClick()

        // Exercise picker: search the starter library and pick one.
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("barbell squat")
        waitFor(hasText("Barbell Squat"))
        compose.onNodeWithText("Barbell Squat").performClick()

        // Logging screen: bump weight and reps, then save.
        waitFor(hasText("Save set"))
        waitFor(hasText("Calculated weight"))
        val plusButtons = compose.onAllNodesWithText("+")
        plusButtons[0].performClick() // weight
        plusButtons[2].performClick() // reps
        compose.onNodeWithText("Save set").performClick()
        waitFor(hasText("× 1 rep", substring = true))

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Barbell Squat"))
        waitFor(hasText("1 exercise", substring = true))

        compose.onNodeWithText("Calendar").performClick()
        waitFor(hasText("Best streak"))
        waitFor(hasText("1 day"))
        compose.onNodeWithText("Year").performClick()
        waitFor(hasText("This year"))
        compose.onNodeWithText("Lifetime").performClick()
        waitFor(hasText("All time"))

        compose.onNodeWithText("Presets").performClick()
        waitFor(hasText("No presets yet"))

        compose.onNodeWithText("Settings").performClick()
        waitFor(hasText("Default unit"))
    }
}
