package com.eplico.openfit

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eplico.openfit.ui.workout.DAY_PAGER_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Drives the real app end to end: add an exercise, log a set, then visit every tab. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntilAtLeastOneExists(matcher, timeoutMillis = 10_000)
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { compose.onRoot(useUnmergedTree = true).printToString() }.getOrElse { "<unavailable: $it>" }
            throw AssertionError("Timed out waiting for ${matcher.description}. Screen was:\n$tree", e)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun logASetAndVisitEveryTab() {
        waitFor(hasText("Nothing logged for this day"))
        compose.onNodeWithContentDescription("Add exercise").performClick()

        // Exercise picker: search the starter library and pick one.
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("barbell squat")
        waitFor(hasText("Barbell Squat"))
        compose.onNodeWithText("Barbell Squat").performClick()

        // Logging screen: bump weight and reps, then save.
        waitFor(hasText("Save set"))
        waitFor(hasText("Calculated weight"))
        val plusButtons = compose.onAllNodesWithText("+")
        plusButtons[0].performScrollTo().performClick() // weight
        plusButtons[2].performScrollTo().performClick() // reps
        // Switching kg/lb relabels the number that's there; it doesn't convert it.
        val weightField = compose.onAllNodes(hasSetTextAction())[0]
        val typed = weightField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        compose.onNodeWithText("kg").performClick()
        compose.onNodeWithText("lb").performClick()
        assertEquals(typed, weightField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("Save set").performScrollTo().performClick()
        waitFor(hasText("$typed lb × 1 rep"))
        // The first set of an exercise is its heaviest yet.
        waitFor(hasContentDescription("Gold trophy", substring = true))

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Barbell Squat"))
        waitFor(hasText("1 exercise", substring = true))

        // Swipe the day: left for tomorrow, right to come back to today.
        compose.onNodeWithTag(DAY_PAGER_TAG).performTouchInput { swipeLeft() }
        waitFor(hasText("Tomorrow"))
        compose.onNodeWithTag(DAY_PAGER_TAG).performTouchInput { swipeRight() }
        waitFor(hasText("Today"))
        waitFor(hasText("1 exercise", substring = true))

        compose.onNodeWithText("Calendar").performClick()
        waitFor(hasText("Best streak"))
        waitFor(hasText("1/3")) // this week: 1 of the default 3 workouts
        compose.onNodeWithText("Year").performClick()
        waitFor(hasText("This year"))
        compose.onNodeWithText("Lifetime").performClick()
        waitFor(hasText("All time"))
        waitFor(hasText("Total weight moved", substring = true))

        compose.onNodeWithText("Presets").performClick()
        waitFor(hasText("No presets yet"))

        compose.onNodeWithText("Settings").performClick()
        waitFor(hasText("Default unit"))
        waitFor(hasText("Accent colour"))
        waitFor(hasText("Trophies"))
        waitFor(hasText("Workouts per week"))
        waitFor(hasText("Save spreadsheet"))
        waitFor(hasText("Import spreadsheet"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun logARunThenCreateAnExerciseOnTheNewExercisePage() {
        waitFor(hasText("Nothing logged for this day"))
        compose.onNodeWithContentDescription("Add exercise").performClick()
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("running")
        waitFor(hasText("Running"))
        compose.onNodeWithText("Running").performClick()

        // A distance + time exercise: no weight or reps, just distance, minutes and seconds.
        waitFor(hasText("Distance (", substring = true))
        waitFor(hasText("Time"))
        compose.onAllNodesWithText("Reps").assertCountEquals(0)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields.assertCountEquals(3)
        fields[0].performTextInput("5")
        fields[1].performTextInput("25")
        fields[2].performTextInput("30")
        compose.onNodeWithText("Save set").performScrollTo().performClick()
        waitFor(hasText("in 25:30", substring = true))

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Running"))

        // Create a brand-new exercise and land straight in its log.
        // (An extended FAB hides its text from accessibility, so it's found by its label.)
        compose.onNodeWithContentDescription("Add exercise").performClick()
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Sled Push")
        waitFor(hasText("Create \"Sled Push\""))
        compose.onNodeWithText("Create \"Sled Push\"").performClick()
        waitFor(hasText("Save exercise"))
        compose.onNodeWithText("Distance").performClick()
        waitFor(hasText("Add to today's workout"))
        compose.onNodeWithText("Save exercise").performScrollTo().performClick()

        waitFor(hasText("Save set"))
        waitFor(hasText("Sled Push"))
        waitFor(hasText("Distance (", substring = true))
        compose.onAllNodesWithText("Weight", substring = true).assertCountEquals(0)
    }

    /** Runs on Robolectric's default small screen because it opens a dialog with a text field (see robolectric.properties). */
    @Test
    fun manageCategoriesFromSettings() {
        waitFor(hasText("Nothing logged for this day"))
        compose.onNodeWithText("Settings").performClick()
        waitFor(hasText("Categories"))
        compose.onNodeWithText("Categories").performScrollTo().performClick()
        waitFor(hasText("Chest"))
        scrollListTo(hasText("Cardio"))
        scrollListTo(hasText("where exercises go when their category is deleted", substring = true))

        compose.onNodeWithContentDescription("New category").performClick()
        waitFor(hasSetTextAction())
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Mobility")
        compose.onNodeWithText("Create").performClick()
        scrollListTo(hasText("Mobility"))
    }

    /** Scrolls the screen's list until [matcher] is shown, retrying while the database catches up. */
    private fun scrollListTo(matcher: SemanticsMatcher) {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            try {
                compose.onNode(hasScrollAction()).performScrollToNode(matcher)
                return
            } catch (e: AssertionError) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(50)
            }
        }
    }

    private companion object {
        /** A typical phone screen; Robolectric defaults to a small 320x470dp one. */
        const val PHONE = "w411dp-h914dp-xxhdpi"
    }
}
