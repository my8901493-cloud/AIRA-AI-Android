package com.aira.assistant.autonomous

import com.aira.assistant.domain.model.AutonomousTaskCategory
import com.aira.assistant.domain.model.AutonomousTaskStage
import com.aira.assistant.domain.model.AutonomousTaskSubmission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JUnit 4 Acceptance Tests for AIRA's Autonomous Execution Engine (TEST A through TEST E).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutonomousEngineAcceptanceTest {

    @Test
    fun testA_androidSourceProjectInspectionAndCloudBuildPreparation() = runTest {
        val manager = AutonomousTaskManager(scope = this)
        val record = manager.runTaskPipelineSuspend(
            submission = AutonomousTaskSubmission(
                promptOrInstruction = "Inspect this Android project and build an APK",
                pastedCodeOrScript = "package com.example.demo\nimport androidx.compose.runtime.Composable"
            )
        )
        assertEquals(AutonomousTaskCategory.CODE_TO_APK_BUILD, record.category)
        assertEquals(AutonomousTaskStage.COMPLETED, record.stage)
        assertTrue(record.outputFiles.isNotEmpty())
        assertTrue(record.validationReport?.passed == true)
    }

    @Test
    fun testB_hindiScriptTo30SecondVideoProduction() = runTest {
        val manager = AutonomousTaskManager(scope = this)
        val hindiScript = """
            नमस्ते! क्या आपने कभी सोचा है कि भविष्य का पर्सनल AI कैसा होगा?
            AIRA AI सिर्फ सवालों का जवाब नहीं देती, बल्कि आपके पूरे काम को खुद प्लान और पूरा करती है।
            चाहे कोड से APK बनाना हो या स्क्रिप्ट से पूरा सिनेमैटिक वीडियो तैयार करना हो।
            AIRA हर कदम की जांच करती है और आपको फाइनल वेरिफाइड रिजल्ट देती है।
        """.trimIndent()

        val record = manager.runTaskPipelineSuspend(
            submission = AutonomousTaskSubmission(
                promptOrInstruction = "Create a 30-second video from this Hindi script",
                pastedCodeOrScript = hindiScript,
                preferredVideoAspect = "16:9"
            )
        )
        assertEquals(AutonomousTaskCategory.SCRIPT_TO_VIDEO_PRODUCTION, record.category)
        assertEquals(AutonomousTaskStage.COMPLETED, record.stage)
        assertTrue(record.outputFiles.any { it.endsWith(".mp4") })
        assertTrue(record.validationReport?.passed == true)
    }

    @Test
    fun testC_plainTextToMasteredVoiceoverAudio() = runTest {
        val manager = AutonomousTaskManager(scope = this)
        val record = manager.runTaskPipelineSuspend(
            submission = AutonomousTaskSubmission(
                promptOrInstruction = "Generate a complete voiceover audio file for this text",
                pastedCodeOrScript = "Welcome to AIRA AI. I independently plan, select tools, and deliver verified results."
            )
        )
        assertEquals(AutonomousTaskCategory.TEXT_TO_AUDIO_PRODUCTION, record.category)
        assertEquals(AutonomousTaskStage.COMPLETED, record.stage)
        assertTrue(record.outputFiles.any { it.endsWith(".wav") || it.endsWith(".mp3") })
        assertTrue(record.validationReport?.passed == true)
    }

    @Test
    fun testD_brokenCodeDiagnosisAndAutomatedRepair() = runTest {
        val manager = AutonomousTaskManager(scope = this)
        val brokenCode = """
            package com.example.broken
            class Calculator {
                fun computeTotal(items: List<Int>): Int {
                    val sum = items.sum(
                    return sum
            // Missing closing paren and two closing curly braces
        """.trimIndent()

        val record = manager.runTaskPipelineSuspend(
            submission = AutonomousTaskSubmission(
                promptOrInstruction = "Fix this broken code and verify",
                pastedCodeOrScript = brokenCode
            )
        )
        assertEquals(AutonomousTaskCategory.CODE_DIAGNOSIS_AND_REPAIR, record.category)
        assertEquals(AutonomousTaskStage.COMPLETED, record.stage)
        assertTrue(record.validationReport?.passed == true)
    }

    @Test
    fun testE_interruptActiveTaskSafelyWithoutCorruptingFiles() = runTest {
        val manager = AutonomousTaskManager(scope = this)
        val taskDir = manager.workspaceManager.getTaskDirectory("test_interrupt_safe")
        val existingFile = File(taskDir, "outputs/important_existing.txt")
        manager.workspaceManager.writeFileAtomically(existingFile, "IMMUTABLE_BASELINE_CONTENT")

        // Create a simulated in-progress staging file
        val stagingFile = File(taskDir, "outputs/important_existing.txt.tmp_99999")
        stagingFile.writeText("PARTIAL_UNFINISHED_WRITE")

        manager.workspaceManager.rollbackAndCleanTempFiles("test_interrupt_safe")

        assertFalse("Staging temp file must be removed on cancellation", stagingFile.exists())
        assertEquals("IMMUTABLE_BASELINE_CONTENT", existingFile.readText())
    }
}
