package com.aira.assistant.operator

import com.aira.assistant.domain.model.OperatorExecutionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies all 9 Real-World Autonomous Android Operator Acceptance Tests (TEST A through TEST I)
 * defined in Section 13 of the specification.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealWorldAndroidOperatorAcceptanceTest {

    @Test
    fun testA_chromeOperationAndTopicSummary() = runTest {
        val engine = WorkflowExecutionEngine()
        val record = engine.executeGoal("Chrome kholo aur Kotlin Coroutines Clean Architecture par research karo")

        assertEquals(OperatorExecutionState.COMPLETED, record.state)
        assertTrue(record.verificationPassed)
        assertTrue(record.outputDeliverables.any { it.endsWith("chrome_research_summary.md") })
        assertTrue(engine.accessibilityManager.getInteractionTrace().any { it.contains("TAP_COORDS") })
    }

    @Test
    fun testB_youtubeVideoInspectionAndSummary() = runTest {
        val engine = WorkflowExecutionEngine()
        val record = engine.executeGoal("YouTube par ye video dekho aur iska summary banao: Autonomous AI Agents")

        assertEquals(OperatorExecutionState.COMPLETED, record.state)
        assertTrue(record.verificationPassed)
        assertTrue(record.outputDeliverables.any { it.endsWith("youtube_video_summary.md") })
        val traces = engine.accessibilityManager.getInteractionTrace()
        assertTrue(traces.any { it.contains("TYPE_TEXT") })
    }

    @Test
    fun testC_installedVideoEditorOperationAndExport() = runTest {
        val engine = WorkflowExecutionEngine()
        // Register VN Video Editor as installed on the simulated device
        engine.appDiscoveryManager.addSimulatedInstalledPackage("com.frontrow.vlog")

        val record = engine.executeGoal("Editing app kholo aur video edit karo")
        assertEquals(OperatorExecutionState.COMPLETED, record.state)
        assertTrue(record.selectedToolOrApp.contains("VN Video Editor"))
        assertTrue(record.outputDeliverables.any { it.endsWith(".mp4") })
        assertTrue(record.activityLog.any { it.actionPerformed.contains("Tapped UI control 'Export'") })
    }

    @Test
    fun testD_missingAppDiscoveryRationaleAndOfficialPlayStoreApproval() = runTest {
        val engine = WorkflowExecutionEngine()
        // Request a task requiring an uninstalled app ("io.spck") without pre-approval
        val waitingRecord = engine.executeGoal(
            userGoal = "Open mobile code sandbox app to edit repository",
            requiredUninstalledPackageHint = "io.spck",
            userApprovedPaidOrInstall = false
        )

        assertEquals(OperatorExecutionState.WAITING_FOR_APPROVAL, waitingRecord.state)
        assertNotNull(waitingRecord.pendingInstallProposal)
        assertEquals("io.spck", waitingRecord.pendingInstallProposal!!.packageName)
        assertTrue(waitingRecord.pendingInstallProposal!!.officialStoreUri.contains("play.google.com/store/apps/details?id=io.spck"))
        assertTrue("Must not open store before approval", engine.installationApprovalManager.getOpenedStoreUris().isEmpty())

        // Now approve installation and verify official Play Store URI is opened and task completes
        val completedRecord = engine.executeGoal(
            userGoal = "Open mobile code sandbox app to edit repository",
            requiredUninstalledPackageHint = "io.spck",
            userApprovedPaidOrInstall = true
        )
        assertEquals(OperatorExecutionState.COMPLETED, completedRecord.state)
        assertTrue(engine.installationApprovalManager.getOpenedStoreUris().isNotEmpty())
    }

    @Test
    fun testE_freeAlternativeSelectedWhenPreferredToolIsPaidOrUnavailable() = runTest {
        val engine = WorkflowExecutionEngine()
        engine.freeToolEvaluator.markToolUnavailableOrPaidForTest("installed_com.frontrow.vlog")

        val chosen = engine.freeToolEvaluator.selectBestFreeTool("VIDEO_EDITING", "mp4")
        assertNotNull(chosen)
        assertTrue(chosen!!.isCompletelyFree)
        assertFalse(chosen.requiresPaymentOrCredits)
    }

    @Test
    fun testF_codeToApkWorkflowInspectionAndVerification() = runTest {
        val engine = WorkflowExecutionEngine()
        val record = engine.executeGoal("Is Android project ka APK build karo")

        assertEquals(OperatorExecutionState.COMPLETED, record.state)
        assertTrue(record.verificationPassed)
        assertTrue(record.outputDeliverables.isNotEmpty())
    }

    @Test
    fun testG_errorRecoveryFromLayoutShiftOrAppFailure() = runTest {
        val engine = WorkflowExecutionEngine()
        val record = engine.executeGoal(
            userGoal = "Meri script ko cinematic video mein convert karo",
            simulateTransientFailureOnFirstAttempt = true
        )

        assertEquals(OperatorExecutionState.COMPLETED, record.state)
        assertTrue(
            "Activity log must record RECOVERING state transition",
            record.activityLog.any { it.state == OperatorExecutionState.RECOVERING }
        )
    }

    @Test
    fun testH_paidServiceProtectionBlocksUnapprovedCharges() {
        val engine = WorkflowExecutionEngine()
        val paidCandidate = engine.freeToolEvaluator.discoverCandidatesForCapability("VIDEO_EDITING")
            .first { it.requiresPaymentOrCredits }

        val decision = engine.costAndPermissionManager.evaluateCandidateAndGoal(
            userGoal = "Render video using paid API credits",
            candidate = paidCandidate,
            userExplicitlyApprovedPaidOrSensitive = false
        )
        assertFalse("Paid service must be blocked without explicit user permission", decision.isAllowedImmediately)
        assertNotNull(decision.requiresUserApprovalReason)
    }

    @Test
    fun testI_cancellationStopsSafelyWithoutCorruptingUserFiles() {
        val engine = WorkflowExecutionEngine()
        val taskDir = engine.workspaceManager.getTaskDirectory("test_i_cancel")
        val userFile = File(taskDir, "outputs/user_precious_project.txt")
        engine.workspaceManager.writeFileAtomically(userFile, "SAFE_ORIGINAL_DATA")

        val tempInFlight = File(taskDir, "outputs/user_precious_project.txt.tmp_12345")
        tempInFlight.writeText("HALF_WRITTEN_CORRUPT")

        engine.cancelImmediately()
        engine.workspaceManager.rollbackAndCleanTempFiles("test_i_cancel")

        assertTrue(engine.accessibilityManager.isEmergencyStopped)
        assertFalse(tempInFlight.exists())
        assertEquals("SAFE_ORIGINAL_DATA", userFile.readText())
    }
}
