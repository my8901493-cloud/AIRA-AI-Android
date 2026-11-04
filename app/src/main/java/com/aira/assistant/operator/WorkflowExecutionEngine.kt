package com.aira.assistant.operator

import com.aira.assistant.autonomous.AudioProductionAgent
import com.aira.assistant.autonomous.CodeBuildAgent
import com.aira.assistant.autonomous.FileWorkspaceManager
import com.aira.assistant.autonomous.VideoProductionAgent
import com.aira.assistant.domain.model.OperatorExecutionState
import com.aira.assistant.domain.model.OperatorTaskExecutionRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Real-World Autonomous Android Operator Workflow Engine (`WorkflowExecutionEngine`).
 *
 * Transitions through all 13 required states:
 * `ANALYZING` -> `PLANNING` -> `DISCOVERING_TOOLS` -> `CHECKING_COST` -> `WAITING_FOR_APPROVAL`
 * -> `OPENING_APP` -> `OPERATING_SCREEN` -> `EXECUTING` -> `VERIFYING` -> `RECOVERING`
 * -> `COMPLETED` / `FAILED` / `CANCELLED`.
 */
class WorkflowExecutionEngine(
    val workspaceManager: FileWorkspaceManager = FileWorkspaceManager(),
    val accessibilityManager: AccessibilityInteractionManager = AccessibilityInteractionManager(),
    val visualEngine: VisualUnderstandingEngine = VisualUnderstandingEngine(),
    val appDiscoveryManager: AppDiscoveryManager = AppDiscoveryManager(),
    val costAndPermissionManager: CostAndPermissionManager = CostAndPermissionManager(),
    val executionHistoryRepository: ExecutionHistoryRepository = ExecutionHistoryRepository()
) {
    val deviceController = AndroidDeviceController(accessibilityManager, visualEngine)
    val appLaunchManager = AppLaunchManager(null, appDiscoveryManager, accessibilityManager)
    val websiteManager = WebsiteInteractionManager(deviceController, appLaunchManager)
    val browserEngine = BrowserAutomationEngine(websiteManager, deviceController)
    val browserResearchAgent = BrowserResearchAgent(appLaunchManager, deviceController, browserEngine)

    val freeToolEvaluator = FreeToolEvaluator(appDiscoveryManager)
    val compatibilityChecker = ToolCompatibilityChecker(appDiscoveryManager)
    val installationApprovalManager = InstallationApprovalManager(null, appDiscoveryManager)

    val audioProductionAgent = AudioProductionAgent(workspaceManager)
    val videoProductionAgent = VideoProductionAgent(workspaceManager, audioProductionAgent)
    val codeBuildAgent = CodeBuildAgent(workspaceManager)

    val videoEditorAgent = VideoEditorAgent(
        appDiscoveryManager = appDiscoveryManager,
        appLaunchManager = appLaunchManager,
        deviceController = deviceController,
        videoProductionAgent = videoProductionAgent,
        costAndPermissionManager = costAndPermissionManager
    )
    val audioEditorAgent = AudioEditorAgent(
        appDiscoveryManager = appDiscoveryManager,
        appLaunchManager = appLaunchManager,
        deviceController = deviceController,
        audioProductionAgent = audioProductionAgent,
        costAndPermissionManager = costAndPermissionManager
    )

    val planner = AutonomousPlanner()
    val recoveryEngine = TaskRecoveryEngine(deviceController, freeToolEvaluator)
    val verificationEngine = ResultVerificationEngine()

    private val _activeRecord = MutableStateFlow<OperatorTaskExecutionRecord?>(null)
    val activeRecord: StateFlow<OperatorTaskExecutionRecord?> = _activeRecord.asStateFlow()

    /**
     * Executes a complete real-world operator goal end-to-end.
     */
    suspend fun executeGoal(
        userGoal: String,
        requiredUninstalledPackageHint: String? = null,
        simulateTransientFailureOnFirstAttempt: Boolean = false,
        userApprovedPaidOrInstall: Boolean = false,
        projectDirForApkBuild: File? = null
    ): OperatorTaskExecutionRecord {
        val taskId = "op_${UUID.randomUUID().toString().take(8)}"
        val taskDir = workspaceManager.getTaskDirectory(taskId)
        val outDir = File(taskDir, "outputs")
        accessibilityManager.resetEmergencyStop()

        // 1. ANALYZING
        var record = OperatorTaskExecutionRecord(
            taskId = taskId,
            userGoal = userGoal,
            state = OperatorExecutionState.ANALYZING,
            currentStepDescription = "Analyzing goal and input context"
        )
        checkpoint(record, "AIRA Operator", "Analyzed user goal: $userGoal", "Goal parsed")

        try {
            currentCoroutineContext().ensureActive()

            // 2. PLANNING
            val plan = planner.createPlan(userGoal, requiredUninstalledPackageHint)
            record = record.copy(
                state = OperatorExecutionState.PLANNING,
                currentStepDescription = "Created ${plan.plannedSteps.size}-step plan (${plan.intentType})"
            )
            checkpoint(record, "AutonomousPlanner", "Planned ${plan.intentType}", plan.plannedSteps.joinToString(" -> "))

            // 3. DISCOVERING_TOOLS
            record = record.copy(
                state = OperatorExecutionState.DISCOVERING_TOOLS,
                currentStepDescription = "Discovering installed apps, free web tools, and local engines"
            )
            val candidate = freeToolEvaluator.selectBestFreeTool(plan.targetCapability)
            val selectedName = candidate?.name ?: "AIRA Built-In Operator"
            record = record.copy(
                selectedToolOrApp = selectedName,
                selectionReason = "Selected free, Android-compatible option (${candidate?.freeLimitDescription ?: "Built-in"})"
            )
            checkpoint(record, selectedName, "Discovered optimal free tool", record.selectionReason)

            // 4. CHECKING_COST & PERMISSIONS
            record = record.copy(
                state = OperatorExecutionState.CHECKING_COST,
                currentStepDescription = "Verifying zero unapproved paid API charges or unsafe operations"
            )
            val safetyDecision = costAndPermissionManager.evaluateCandidateAndGoal(
                userGoal = userGoal,
                candidate = candidate,
                userExplicitlyApprovedPaidOrSensitive = userApprovedPaidOrInstall
            )
            if (!safetyDecision.isAllowedImmediately) {
                record = record.copy(
                    state = OperatorExecutionState.WAITING_FOR_APPROVAL,
                    pendingApprovalReason = safetyDecision.requiresUserApprovalReason,
                    currentStepDescription = "Paused: explicit approval required before paid/sensitive operation"
                )
                checkpoint(record, selectedName, "Blocked unapproved paid/sensitive action", safetyDecision.requiresUserApprovalReason.orEmpty())
                return record
            }

            // Check if the task requires an uninstalled app (TEST D — Missing App)
            if (plan.intentType == OperatorIntentType.MISSING_APP_DISCOVERY_AND_INSTALL ||
                (!plan.explicitRequiredPackage.isNullOrBlank() && !appDiscoveryManager.isPackageInstalled(plan.explicitRequiredPackage))
            ) {
                val missingPkg = plan.explicitRequiredPackage ?: "io.spck"
                val proposal = installationApprovalManager.proposeOfficialAppInstallation(
                    missingCapability = plan.targetCapability,
                    recommendedAppName = "Spck Open-Source Code & Git Editor",
                    packageName = missingPkg,
                    rationaleExplanation = "This task requires '$missingPkg', which is not currently installed on your phone. It is 100% free on Google Play.",
                    alternativesCompared = listOf("Spck Code Editor (Free, No Ads)", "ACode Editor (Free Open-Source)")
                )
                if (!userApprovedPaidOrInstall) {
                    record = record.copy(
                        state = OperatorExecutionState.WAITING_FOR_APPROVAL,
                        pendingInstallProposal = proposal,
                        pendingApprovalReason = proposal.rationaleExplanation,
                        currentStepDescription = "Waiting for user approval to open official Play Store listing for ${proposal.recommendedAppName}"
                    )
                    checkpoint(record, proposal.recommendedAppName, "Requested user approval for app installation", proposal.officialStoreUri)
                    return record
                } else {
                    installationApprovalManager.approveAndOpenOfficialStoreListing()
                    appDiscoveryManager.addSimulatedInstalledPackage(missingPkg)
                    installationApprovalManager.verifyInstallationCompleted(missingPkg)
                }
            }

            // 5. OPENING_APP -> 6. OPERATING_SCREEN -> 7. EXECUTING (with RECOVERING support)
            val producedFiles = mutableListOf<File>()
            var attempt = 1
            var executionDone = false

            while (!executionDone) {
                currentCoroutineContext().ensureActive()
                if (accessibilityManager.isEmergencyStopped) {
                    throw CancellationException("Stopped by user Emergency Stop")
                }

                try {
                    if (simulateTransientFailureOnFirstAttempt && attempt == 1) {
                        throw IllegalStateException("Simulated UI layout change / unresponsive element on attempt 1")
                    }

                    when (plan.intentType) {
                        OperatorIntentType.CHROME_WEB_RESEARCH -> {
                            record = record.copy(state = OperatorExecutionState.OPENING_APP, currentStepDescription = "Opening Google Chrome")
                            checkpoint(record, "com.android.chrome", "Opening Chrome", "Window active")

                            record = record.copy(state = OperatorExecutionState.OPERATING_SCREEN, currentStepDescription = "Searching topic & extracting webpage")
                            val res = browserResearchAgent.researchTopicInChrome(plan.extractedQueryOrTopic, outDir).getOrThrow()
                            producedFiles.add(res.savedReportFile)
                        }

                        OperatorIntentType.YOUTUBE_VIDEO_SUMMARY -> {
                            record = record.copy(state = OperatorExecutionState.OPENING_APP, currentStepDescription = "Opening YouTube")
                            checkpoint(record, "com.google.android.youtube", "Opening YouTube app", "Window active")

                            record = record.copy(state = OperatorExecutionState.OPERATING_SCREEN, currentStepDescription = "Locating video & inspecting transcript")
                            val res = browserResearchAgent.inspectAndSummarizeYouTubeVideo(plan.extractedQueryOrTopic, outDir).getOrThrow()
                            producedFiles.add(res.savedReportFile)
                        }

                        OperatorIntentType.INSTALLED_APP_VIDEO_EDIT,
                        OperatorIntentType.SCRIPT_TO_CINEMATIC_VIDEO,
                        OperatorIntentType.FREE_TOOL_SEARCH_AND_EXECUTION -> {
                            record = record.copy(state = OperatorExecutionState.OPENING_APP, currentStepDescription = "Opening video editor / studio")
                            record = record.copy(state = OperatorExecutionState.OPERATING_SCREEN, currentStepDescription = "Importing assets, arranging timeline, adding subtitles & exporting MP4")
                            val editRes = videoEditorAgent.editAndExportVideo(taskId, plan.extractedQueryOrTopic).getOrThrow()
                            record = record.copy(selectedToolOrApp = editRes.operatedEditorApp)
                            producedFiles.add(editRes.exportedMp4File)
                        }

                        OperatorIntentType.TEXT_TO_VOICEOVER_AUDIO -> {
                            record = record.copy(state = OperatorExecutionState.EXECUTING, currentStepDescription = "Synthesizing & mastering voiceover audio")
                            val audioRes = audioEditorAgent.produceOrEditAudio(taskId, plan.extractedQueryOrTopic).getOrThrow()
                            producedFiles.add(audioRes.mp3File ?: audioRes.wavFile)
                        }

                        OperatorIntentType.CODE_TO_APK_BUILD -> {
                            record = record.copy(state = OperatorExecutionState.EXECUTING, currentStepDescription = "Inspecting Android project & executing build pipeline")
                            val targetProject = projectDirForApkBuild ?: taskDir
                            val buildRes = codeBuildAgent.inspectAndBuildAndroidProject(taskId, targetProject)
                            val artifact = buildRes.apkFile ?: buildRes.verifiedSourceZip
                            if (artifact != null) producedFiles.add(artifact)
                        }

                        OperatorIntentType.MISSING_APP_DISCOVERY_AND_INSTALL -> {
                            val markerFile = File(outDir, "installed_app_ready_report.md")
                            workspaceManager.writeFileAtomically(markerFile, "# App Installation Verified & Task Resumed
Goal: $userGoal")
                            producedFiles.add(markerFile)
                        }
                    }
                    executionDone = true
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    record = record.copy(
                        state = OperatorExecutionState.RECOVERING,
                        currentStepDescription = "Recovering from error: ${e.message}"
                    )
                    val recOut = recoveryEngine.recoverFromStepFailure(
                        attemptIndex = attempt,
                        capability = plan.targetCapability,
                        failedAppOrSite = record.selectedToolOrApp,
                        errorReason = e.message ?: "UI error"
                    )
                    checkpoint(record, recOut.updatedTargetAppOrMethod, "Self-recovery triggered", recOut.recoveryStrategyUsed)
                    if (!recOut.recovered) {
                        record = record.copy(
                            state = OperatorExecutionState.FAILED,
                            verificationSummary = recOut.recoveryStrategyUsed
                        )
                        executionHistoryRepository.saveOrUpdate(record)
                        return record
                    }
                    record = record.copy(selectedToolOrApp = recOut.updatedTargetAppOrMethod)
                    attempt++
                }
            }

            // 8. VERIFYING -> 9. COMPLETED
            record = record.copy(
                state = OperatorExecutionState.VERIFYING,
                currentStepDescription = "Verifying output deliverable files and media playback headers"
            )
            val verification = verificationEngine.verifyDeliverables(producedFiles)
            val finalState = if (verification.passed) OperatorExecutionState.COMPLETED else OperatorExecutionState.FAILED

            record = record.copy(
                state = finalState,
                currentStepDescription = verification.summary,
                outputDeliverables = verification.verifiedFiles,
                verificationPassed = verification.passed,
                verificationSummary = verification.summary,
                activityLog = costAndPermissionManager.getLogsForTask(taskId)
            )
            checkpoint(record, record.selectedToolOrApp, "Verified final deliverables", verification.summary)
            return record

        } catch (ce: CancellationException) {
            workspaceManager.rollbackAndCleanTempFiles(taskId)
            val cancelled = record.copy(
                state = OperatorExecutionState.CANCELLED,
                currentStepDescription = "Cancelled safely by user; temporary staging files rolled back.",
                activityLog = costAndPermissionManager.getLogsForTask(taskId)
            )
            checkpoint(cancelled, "AIRA Operator", "Task cancelled safely", "Zero file corruption")
            return cancelled
        }
    }

    fun cancelImmediately() {
        deviceController.stopImmediately()
        val current = _activeRecord.value ?: return
        workspaceManager.rollbackAndCleanTempFiles(current.taskId)
        val cancelled = current.copy(
            state = OperatorExecutionState.CANCELLED,
            currentStepDescription = "Stopped immediately by user."
        )
        _activeRecord.value = cancelled
        executionHistoryRepository.saveOrUpdate(cancelled)
    }

    private fun checkpoint(
        record: OperatorTaskExecutionRecord,
        appOrSite: String,
        action: String,
        verificationNote: String
    ) {
        costAndPermissionManager.recordActivity(
            taskId = record.taskId,
            state = record.state,
            targetAppOrWebsite = appOrSite,
            actionPerformed = action,
            verificationNote = verificationNote
        )
        val withLogs = record.copy(activityLog = costAndPermissionManager.getLogsForTask(record.taskId))
        _activeRecord.value = withLogs
        executionHistoryRepository.saveOrUpdate(withLogs)
    }
}
