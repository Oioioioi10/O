package com.avatarsdev.tiktokupgrade

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.avatarsdev.tiktokupgrade.data.AppSettings
import com.avatarsdev.tiktokupgrade.data.db.DataSource
import com.avatarsdev.tiktokupgrade.ui.MainActivity
import com.avatarsdev.tiktokupgrade.ui.likes.LikesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * End-to-end checks on a real device/emulator. Also saves screenshots to
 * /sdcard/Android/data/<pkg>/files/screens so CI can publish them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TikTokUpgradeApp

    private fun resetAll() {
        runBlocking {
            app.container.likes.clear(DataSource.REAL)
            app.container.likes.clear(DataSource.SAMPLE)
        }
        app.container.settings.update { AppSettings() }
        rule.waitForIdle()
    }

    private fun waitForTag(tag: String, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForExact(text: String, timeout: Long = 10_000) {
        rule.waitUntil(timeout) { rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(900) // let enter/number animations settle
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(app.getExternalFilesDir(null), "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun scrollTo(list: String, tag: String): SemanticsNodeInteraction {
        rule.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
        return rule.onNodeWithTag(tag)
    }

    @Test
    fun a_sampleData_dashboard_analytics_history() {
        resetAll()
        waitForTag("trySample")
        shot("01_dashboard_empty")

        rule.onNodeWithTag("trySample").performClick()
        waitForTag("dashTotal", 15_000)
        rule.onNodeWithTag("sampleBanner").assertExists()
        shot("02_dashboard_sample")

        rule.onNodeWithTag("nav_analytics").performClick()
        waitForTag("totalLikes")
        rule.onNodeWithText("30 วัน").performClick()
        rule.waitForIdle()
        shot("04_analytics")
        scrollTo("analyticsList", "hourlyChart").assertExists()
        scrollTo("analyticsList", "exportCsv").assertExists()
        shot("05_analytics_charts")

        rule.onNodeWithTag("nav_history").performClick()
        waitForTag("historyCount")
        shot("06_history")
        rule.onNodeWithTag("searchField").performTextInput("SAMPLE-1001")
        rule.waitForIdle()
        waitForExact("1 คลิป")
        rule.onNodeWithTag("sortToggle").performClick()
        rule.waitForIdle()
    }

    @Test
    fun b_aiFeedController_parsesSpecPrompt() {
        resetAll()
        rule.onNodeWithTag("nav_feed").performClick()
        waitForTag("promptField")
        rule.onNodeWithTag("promptField").performTextClearance()
        rule.onNodeWithTag("promptField").performTextInput(
            "แสดงคลิป Roblox 50%\nAI Tools 30%\nProgramming 20%\nไม่ต้องการคลิปเต้น\nไม่ต้องการคลิปตลก",
        )
        rule.onNodeWithTag("analyzeButton").performClick()
        rule.waitForIdle()
        scrollTo("feedList", "rulesCard").assertExists()
        rule.onNode(hasText("50%") and hasAnyAncestor(hasTestTag("weight_roblox")), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("30%") and hasAnyAncestor(hasTestTag("weight_ai_tools")), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("20%") and hasAnyAncestor(hasTestTag("weight_programming")), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("exclude_dance").assertExists()
        rule.onNodeWithTag("exclude_comedy").assertExists()
        shot("03_ai_feed_rules")

        scrollTo("feedList", "planCard").assertExists()
        shot("03b_ai_feed_plan")

        scrollTo("feedList", "checkerField")
        rule.onNodeWithTag("checkerField").performTextInput("dance challenge #dance")
        waitForTag("checkerScore")
        waitForText("ถูกกรอง")
        rule.onNodeWithTag("checkerField").performTextClearance()
        rule.onNodeWithTag("checkerField").performTextInput("สอนทำเกม Roblox Studio #roblox")
        waitForText("ตรงหมวด Roblox")
        shot("03c_ai_feed_checker")
    }

    @Test
    fun c_realImport_5to6Nov_isSeparateFromSample() {
        resetAll()
        // Sample rows exist but must never be counted with real ones.
        runBlocking { app.container.likes.loadSample() }

        val export = """
            {"Activity":{"Like List":{"ItemFavoriteList":[
              {"Date":"2026-11-04 16:59:59","Link":"https://www.tiktokv.com/share/video/7434000000000000001/"},
              {"Date":"2026-11-04 17:00:00","Link":"https://www.tiktokv.com/share/video/7434000000000000002/"},
              {"Date":"2026-11-05 09:15:00","Link":"https://www.tiktokv.com/share/video/7434000000000000003/"},
              {"Date":"2026-11-06 16:59:59","Link":"https://www.tiktokv.com/share/video/7434000000000000004/"},
              {"Date":"2026-11-06 17:00:00","Link":"https://www.tiktokv.com/share/video/7434000000000000005/"}
            ]}}}
        """.trimIndent()
        val outcome = runBlocking { app.container.likes.importExport(export.toByteArray(), "user_data_tiktok.json", ZoneId.of("UTC")) }
        assertEquals(5, outcome.inserted)
        val again = runBlocking { app.container.likes.importExport(export.toByteArray(), "user_data_tiktok.json", ZoneId.of("UTC")) }
        assertEquals(0, again.inserted)
        assertEquals(5, again.duplicates)
        assertEquals(5, runBlocking { app.container.likes.count(DataSource.REAL) })

        app.container.settings.update { it.copy(dataSource = DataSource.REAL) }
        val zone = ZoneId.systemDefault()
        rule.runOnIdle {
            ViewModelProvider(rule.activity)[LikesViewModel::class.java].setRange(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))
        }

        rule.onNodeWithTag("nav_analytics").performClick()
        waitForTag("totalLikes")
        // Expected count depends on the emulator zone; compute it the same way the app does.
        val expected = listOf("2026-11-04T16:59:59Z", "2026-11-04T17:00:00Z", "2026-11-05T09:15:00Z", "2026-11-06T16:59:59Z", "2026-11-06T17:00:00Z")
            .map { java.time.Instant.parse(it).atZone(zone).toLocalDate() }
            .count { !it.isBefore(LocalDate.of(2026, 11, 5)) && !it.isAfter(LocalDate.of(2026, 11, 6)) }
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(expected.toString()) and hasAnyAncestor(hasTestTag("totalLikes")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasTestTag("sampleBanner")).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        shot("07_analytics_real_5_6_nov")

        rule.onNodeWithTag("nav_history").performClick()
        waitForTag("historyCount")
        waitForExact("$expected คลิป")
        shot("08_history_real")

        rule.onNodeWithTag("nav_settings").performClick()
        waitForTag("importButton")
        shot("09_settings")
    }
}
