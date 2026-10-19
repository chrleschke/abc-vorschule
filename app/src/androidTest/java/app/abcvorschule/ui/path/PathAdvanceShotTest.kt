package app.abcvorschule.ui.path

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LessonSigns
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.SkillStats
import app.abcvorschule.ui.theme.AbcTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frames des Pfads direkt nach einer zu Ende gespielten Lektion: das alte Schild springt
 * und wirft Sterne, dann hüpft der Pfeil zum nächsten. Kein Assert — ohne diesen Lauf
 * müsste man sich für jede Sichtprüfung durch eine ganze Lektion spielen. Abholen mit
 * `adb exec-out run-as app.silbo.abcvorschule cat files/pathshots/<name>`.
 */
@RunWith(AndroidJUnit4::class)
class PathAdvanceShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir: File by lazy { File(context.filesDir, "pathshots").apply { mkdirs() } }

    @Test
    fun finishingTheFirstLessonWithHelpHopsToTheSecond() {
        val pack = ContentRepository.fromContext(context).load()
        val first = pack.authoredLessons.first()
        // Mit „Zeig mir" durchgespielt: das Schild bleibt „angefangen", der Pfad zieht trotzdem weiter.
        val progress = LearnerProgress(
            taskStats = pack.tasksOf(first).associate { it.id to SkillStats(attempts = 1, resolves = 1) },
            lastFinishedLessonId = first.id,
        )
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AbcTheme {
                PathScreen(
                    lessons = pack.lessons,
                    states = LessonGating.states(pack, progress),
                    unlockAllLessons = false,
                    signsByLessonId = pack.lessons.associate { it.id to LessonSigns.forLesson(pack, it) },
                    highlightedLessonId = LessonGating.nextPlayable(pack, progress)?.id,
                    advanceFromLessonId = first.id,
                    advanceFinished = true,
                    points = 12,
                    onOpenLesson = {},
                    onLockedTap = {},
                    onAdvanceAnimated = {},
                    onParentGateUnlocked = {},
                    modifier = Modifier.fillMaxSize().testTag("shot"),
                )
            }
        }
        var t = 0L
        listOf(0L, 350L, 500L, 650L, 850L, 1100L, 1400L, 1650L, 1900L, 2300L, 2700L, 3200L).forEach { at ->
            rule.mainClock.advanceTimeBy(at - t)
            t = at
            save("advance_%04d.png".format(at))
        }
    }

    private fun save(name: String) {
        val bitmap = rule.onNodeWithTag("shot").captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
