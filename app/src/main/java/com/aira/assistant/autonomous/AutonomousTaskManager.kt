package com.aira.assistant.autonomous

import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AutonomousTaskRecord
import com.aira.assistant.domain.model.AutonomousTaskStage
import com.aira.assistant.domain.model.AutonomousTaskSubmission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Top-Level Autonomous Task Manager (`AutonomousTaskManager`).
 *
 * Implements the full end-to-end architecture:
 * USER INPUT -> INPUT ANALYZER -> TASK CLASSIFIER -> PLANNING ENGINE
 * -> TOOL DISCOVERY -> TOOL SELECTION -> PERMISSION CHECK -> EXECUTION ENGINE
 * -> RESULT VALIDATION -> ERROR RECOVERY -> FINAL DELIVERY.
 */
class AutonomousTaskManager(
    private val scope: CoroutineScope,
    val workspaceManager: FileWorkspaceManager = FileWorkspaceManager(),
    val inputAnalyzer: InputAnalyzer = InputAnalyzer(),
    val taskClassifier: TaskClassifier = TaskClassifier(),
    val toolRegistry: ToolRegistry = ToolRegistry(),
    val toolDiscoveryEngine: ToolDiscoveryEngine = ToolDiscoveryEngine(),
    val costManager: CostManager = CostManager(),
    val permissionManager: PermissionManager = PermissionManager(),
    val taskHistoryRepository: TaskHistoryRepository = TaskHistoryRepository()
) {
    val toolSelectionEngine = ToolSelectionEngine(toolRegistry, toolDiscoveryEngine, costManager)
    val planningEngine = PlanningEngine(toolSelectionEngine)
    val codeBuildAgent = CodeBuildAgent(workspaceManager, toolDiscoveryEngine)
    val audioProductionAgent = AudioProductionAgent(workspaceManager, toolDiscoveryEngine)
    val videoProductionAgent = VideoProductionAgent(workspaceManager, audioProductionAgent)
    val resultValidator = ResultValidator()
    val errorRecoveryEngine = ErrorRecoveryEngine()

    private val executionEngine = ExecutionEngine(
        workspaceManager = workspaceManager,
        codeBuildAgent = codeBuildAgent,
        audioAgent = audioProductionAgent,
        videoAgent = videoProductionAgent,
        resultValidator = resultValidator,
        errorRecoveryEngine = errorRecoveryEngine,
        taskHistoryRepository = taskHistoryRepository
    )

    private val _activeTask = MutableStateFlow<AutonomousTaskRecord?>(null)
    val activeTask: StateFlow<AutonomousTaskRecord?> = _activeTask.asStateFlow()

    private var activeExecutionJob: Job? = null
    private var pendingSubmissionForApproval: AutonomousTaskSubmission? = null

    /**
     * Submits any user task (code, Android project, video script, voiceover text, or multi-step objective)
     * and autonomously executes the entire workflow from analysis to validated output delivery.
     */
    fun submitAutonomousTask(submission: AutonomousTaskSubmission): String {
        val taskId = "aira_task_${UUID.randomUUID().toString().take(8)}"
        activeExecutionJob?.cancel()

        activeExecutionJob = scope.launch(Dispatchers.Default) {
            runTaskPipelineSuspend(taskId, submission)
        }
        return taskId
    }

    /**
     * Synchronous suspend entrypoint for deterministic testing and direct invocation.
     */
    suspend fun runTaskPipelineSuspend(
        taskId: String = "aira_task_${UUID.randomUUID().toString().take(8)}",
        submission: AutonomousTaskSubmission
    ): AutonomousTaskRecord {
        // 1. ANALYZING
        val analysis = inputAnalyzer.analyze(submission)
        val category = taskClassifier.classify(submission, analysis)

        var record = AutonomousTaskRecord(
            taskId = taskId,
            title = submission.promptOrInstruction.take(48).ifEmpty { category.title },
            inputType = analysis.primaryInputType,
            category = category,
            stage = AutonomousTaskStage.ANALYZING,
            progressFraction = 0.05f
        )
        updateState(record)

        // 2. PLANNING & 3. SELECTING_TOOLS
        record = record.copy(stage = AutonomousTaskStage.PLANNING, progressFraction = 0.10f)
        updateState(record)

        val plan = planningEngine.buildPlan(
            taskId = taskId,
            category = category,
            userApprovedPaidApis = submission.userApprovedPaidApis
        )

        record = record.copy(
            stage = AutonomousTaskStage.SELECTING_TOOLS,
            progressFraction = 0.12f,
            steps = plan.steps,
            toolDecisions = plan.toolDecisions
        )
        updateState(record)

        // 4. PERMISSION CHECK
        val verdict = permissionManager.evaluateSubmission(
            submission = submission,
            selectedTools = plan.toolDecisions.map { it.selectedTool }
        )

        if (!verdict.allowedImmediately) {
            pendingSubmissionForApproval = submission
            record = record.copy(
                stage = AutonomousTaskStage.WAITING_APPROVAL,
                requiresApprovalReason = verdict.reasonIfBlocked
            )
            updateState(record)
            return record
        }

        // 5. EXECUTING -> VALIDATING -> COMPLETED (with Error Recovery)
        return executionEngine.executeWorkflow(
            initialRecord = record,
            submission = submission,
            onRecordUpdated = { updated -> _activeTask.value = updated }
        )
    }

    fun approveWaitingTask() {
        val waiting = _activeTask.value ?: return
        val sub = pendingSubmissionForApproval ?: return
        if (waiting.stage != AutonomousTaskStage.WAITING_APPROVAL) return

        val approvedSub = sub.copy(
            userApprovedHighConsequenceActions = true,
            userApprovedPaidApis = true
        )
        submitAutonomousTask(approvedSub)
    }

    fun cancelActiveTask() {
        val current = _activeTask.value ?: return
        activeExecutionJob?.cancel()
        activeExecutionJob = null
        workspaceManager.rollbackAndCleanTempFiles(current.taskId)
        val cancelled = current.copy(stage = AutonomousTaskStage.CANCELLED)
        _activeTask.value = cancelled
        scope.launch {
            taskHistoryRepository.saveCheckpoint(cancelled)
        }
        AiraLogger.i("AutonomousTaskManager", "Task ${current.taskId} safely cancelled and rolled back.")
    }

    private suspend fun updateState(record: AutonomousTaskRecord) {
        _activeTask.value = record
        taskHistoryRepository.saveCheckpoint(record)
    }
}
