package com.aira.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aira.assistant.agent.AgentOrchestrator
import com.aira.assistant.ai.CompositeLlmRouter
import com.aira.assistant.ai.ModelCatalogManager
import com.aira.assistant.ai.local.LocalOnDeviceLlmEngine
import com.aira.assistant.ai.network.LocalNetworkLlmEngine
import com.aira.assistant.autonomous.AutonomousTaskManager
import com.aira.assistant.autonomous.FileWorkspaceManager
import com.aira.assistant.autonomous.TaskHistoryRepository
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.core.NetworkConnectivityObserver
import com.aira.assistant.core.SecurityPreferences
import com.aira.assistant.data.repository.ConversationRepository
import com.aira.assistant.data.repository.MemoryRepository
import com.aira.assistant.domain.model.AgentPlan
import com.aira.assistant.domain.model.AiModelSpec
import com.aira.assistant.domain.model.AiraLanguage
import com.aira.assistant.domain.model.AutonomousTaskRecord
import com.aira.assistant.domain.model.AutonomousTaskSubmission
import com.aira.assistant.domain.model.ChatMessage
import com.aira.assistant.domain.model.ConversationSession
import com.aira.assistant.domain.model.DeviceHardwareProfile
import com.aira.assistant.domain.model.MemoryCategory
import com.aira.assistant.domain.model.MemoryItem
import com.aira.assistant.domain.model.MessageRole
import com.aira.assistant.domain.model.OperatorTaskExecutionRecord
import com.aira.assistant.domain.model.VoiceState
import com.aira.assistant.domain.model.VoiceTelemetry
import com.aira.assistant.operator.WorkflowExecutionEngine
import com.aira.assistant.service.ScreenCaptureService
import com.aira.assistant.tools.AiraToolRegistry
import com.aira.assistant.voice.VoiceConversationCoordinator
import com.aira.assistant.voice.stt.ModularAndroidSttEngine
import com.aira.assistant.voice.stt.SttEngine
import com.aira.assistant.voice.tts.ModularStreamingTtsEngine
import com.aira.assistant.voice.tts.TtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Central MVVM ViewModel (`AiraViewModel`) coordinating:
 * - Central AI Orb state (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `INTERRUPTING`, `ERROR`)
 * - Full-Duplex Voice Conversation & Instant Barge-In
 * - Real-World Autonomous Android Operator (`WorkflowExecutionEngine`) for Chrome/Website operation,
 *   YouTube inspection, Installed Video/Audio Editor control, Free Tool Discovery, and Missing App Installation approval
 * - Autonomous Task Execution Studio (`AutonomousTaskManager`)
 * - Long-Term Memory & Conversation History persistence
 * - Screen Understanding context injection
 */
class AiraViewModel(
    val preferences: SecurityPreferences = SecurityPreferences(),
    val modelCatalogManager: ModelCatalogManager = ModelCatalogManager(),
    val memoryRepository: MemoryRepository = MemoryRepository(),
    val conversationRepository: ConversationRepository = ConversationRepository(),
    val toolRegistry: AiraToolRegistry = AiraToolRegistry(),
    val connectivityObserver: NetworkConnectivityObserver = NetworkConnectivityObserver(),
    val taskHistoryRepository: TaskHistoryRepository = TaskHistoryRepository(),
    val fileWorkspaceManager: FileWorkspaceManager = FileWorkspaceManager(),
    sttEngineOverride: SttEngine? = null,
    ttsEngineOverride: TtsEngine? = null
) : ViewModel() {

    private val localLlmEngine = LocalOnDeviceLlmEngine()
    private val networkLlmEngine = LocalNetworkLlmEngine(
        endpointUrl = preferences.lanLlmServerUrl,
        modelName = preferences.lanLlmModelName
    )
    val llmRouter = CompositeLlmRouter(localLlmEngine, networkLlmEngine)

    private val sttEngine: SttEngine = sttEngineOverride ?: ModularAndroidSttEngine(null)
    private val ttsEngine: TtsEngine = ttsEngineOverride ?: ModularStreamingTtsEngine(null)

    val agentOrchestrator = AgentOrchestrator(toolRegistry)

    val autonomousTaskManager = AutonomousTaskManager(
        scope = viewModelScope,
        workspaceManager = fileWorkspaceManager,
        taskHistoryRepository = taskHistoryRepository
    )

    val operatorWorkflowEngine = WorkflowExecutionEngine(
        workspaceManager = fileWorkspaceManager
    )

    val activeAutonomousTask: StateFlow<AutonomousTaskRecord?> = autonomousTaskManager.activeTask
    val activeOperatorRecord: StateFlow<OperatorTaskExecutionRecord?> = operatorWorkflowEngine.activeRecord
    val operatorHistory: StateFlow<List<OperatorTaskExecutionRecord>> = operatorWorkflowEngine.executionHistoryRepository.records

    val autonomousTaskHistory: StateFlow<List<AutonomousTaskRecord>> = taskHistoryRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceCoordinator = VoiceConversationCoordinator(
        scope = viewModelScope,
        sttEngine = sttEngine,
        ttsEngine = ttsEngine,
        llmEngine = llmRouter
    )

    val voiceState: StateFlow<VoiceState> = voiceCoordinator.voiceState
    val orbAudioLevel: StateFlow<Float> = voiceCoordinator.orbAudioLevel
    val livePartialTranscript: StateFlow<String> = voiceCoordinator.livePartialTranscript
    val streamingAssistantReply: StateFlow<String> = voiceCoordinator.streamingAssistantReply
    val telemetry: StateFlow<VoiceTelemetry> = voiceCoordinator.telemetry
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
    val isScreenSharingActive: StateFlow<Boolean> = ScreenCaptureService.isScreenSharingActive

    private val _activeConversationId = MutableStateFlow(1L)
    val activeConversationId: StateFlow<Long> = _activeConversationId.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val currentMessages: StateFlow<List<ChatMessage>> = _currentMessages.asStateFlow()

    val conversations: StateFlow<List<ConversationSession>> = conversationRepository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryItem>> = memoryRepository.observeMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _modelsCatalog = MutableStateFlow(modelCatalogManager.getAvailableModels())
    val modelsCatalog: StateFlow<List<AiModelSpec>> = _modelsCatalog.asStateFlow()

    private val _hardwareProfile = MutableStateFlow(modelCatalogManager.profileHardware())
    val hardwareProfile: StateFlow<DeviceHardwareProfile> = _hardwareProfile.asStateFlow()

    private val _pendingConfirmationPlan = MutableStateFlow<AgentPlan?>(null)
    val pendingConfirmationPlan: StateFlow<AgentPlan?> = _pendingConfirmationPlan.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(preferences.preferredLanguage)
    val selectedLanguage: StateFlow<AiraLanguage> = _selectedLanguage.asStateFlow()

    private val _continuousMode = MutableStateFlow(preferences.continuousConversationMode)
    val continuousMode: StateFlow<Boolean> = _continuousMode.asStateFlow()

    private val _voiceSpeed = MutableStateFlow(preferences.voiceSpeed)
    val voiceSpeed: StateFlow<Float> = _voiceSpeed.asStateFlow()

    private val _memorySavingEnabled = MutableStateFlow(preferences.memorySavingEnabled)
    val memorySavingEnabled: StateFlow<Boolean> = _memorySavingEnabled.asStateFlow()

    init {
        voiceCoordinator.selectedLanguage = preferences.preferredLanguage
        voiceCoordinator.continuousConversationEnabled = preferences.continuousConversationMode
        ttsEngine.setSpeechRate(preferences.voiceSpeed)
        memoryRepository.isMemorySavingEnabled = preferences.memorySavingEnabled

        setupCoordinatorHooks()

        viewModelScope.launch {
            llmRouter.initialize()
            val initialSession = conversationRepository.createNewSession("AIRA Session")
            _activeConversationId.value = initialSession.id
        }
    }

    private fun setupCoordinatorHooks() {
        voiceCoordinator.contextProvider = { userQuery ->
            memoryRepository.extractAndStoreImplicitMemories(userQuery)

            var toolOutputText: String? = null
            val lower = userQuery.lowercase()
            if (lower.contains("chrome") || lower.contains("youtube") ||
                lower.contains("editing app") || lower.contains("free tool") ||
                lower.contains("make a video") || lower.contains("cinematic video") ||
                lower.contains("build apk") || lower.contains("voiceover")
            ) {
                val opRecord = operatorWorkflowEngine.executeGoal(userGoal = userQuery)
                toolOutputText = "Operator [${opRecord.state.displayLabel}] via ${opRecord.selectedToolOrApp}: ${opRecord.verificationSummary}"
            } else {
                val plan = agentOrchestrator.createPlanForUtterance(userQuery)
                if (plan != null) {
                    if (plan.requiresUserConfirmation) {
                        _pendingConfirmationPlan.value = plan
                        toolOutputText = plan.confirmationMessage ?: "Please confirm before I run this sensitive tool."
                    } else {
                        val results = agentOrchestrator.executePlan(plan, userConfirmed = true)
                        toolOutputText = results.joinToString(" ") { it.outputText }
                    }
                }
            }

            val relevantMemories = memoryRepository.retrieveRelevantMemories(userQuery)
            val recentHistory = conversationRepository.getMessages(_activeConversationId.value)

            VoiceConversationCoordinator.TurnContextBundle(
                history = recentHistory,
                memories = relevantMemories,
                screenContext = ScreenCaptureService.latestExtractedScreenText.value,
                toolOutput = toolOutputText
            )
        }

        voiceCoordinator.onTurnCompleted = { userText, assistantText, language, wasInterrupted ->
            val convId = _activeConversationId.value
            if (userText.isNotBlank()) {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.USER,
                    content = userText,
                    languageDetected = language.localeTag
                )
            }
            if (assistantText.isNotBlank()) {
                conversationRepository.appendMessage(
                    conversationId = convId,
                    role = MessageRole.ASSISTANT,
                    content = assistantText,
                    languageDetected = language.localeTag,
                    latencyMs = telemetry.value.totalTurnAroundMs,
                    wasInterrupted = wasInterrupted
                )
            }
            _currentMessages.value = conversationRepository.getMessages(convId)
        }
    }

    fun submitAutonomousTask(submission: AutonomousTaskSubmission): String {
        viewModelScope.launch {
            operatorWorkflowEngine.executeGoal(
                userGoal = submission.promptOrInstruction.ifBlank { submission.pastedCodeOrScript }
            )
        }
        return autonomousTaskManager.submitAutonomousTask(submission)
    }

    fun executeRealWorldOperatorGoal(goal: String) {
        viewModelScope.launch {
            operatorWorkflowEngine.executeGoal(userGoal = goal)
        }
    }

    fun emergencyStopAllOperations() {
        operatorWorkflowEngine.cancelImmediately()
        autonomousTaskManager.cancelActiveTask()
    }

    fun cancelActiveAutonomousTask() {
        operatorWorkflowEngine.cancelImmediately()
        autonomousTaskManager.cancelActiveTask()
    }

    fun approveWaitingAutonomousTask() {
        val currentOp = activeOperatorRecord.value
        if (currentOp != null) {
            viewModelScope.launch {
                operatorWorkflowEngine.executeGoal(
                    userGoal = currentOp.userGoal,
                    userApprovedPaidOrInstall = true
                )
            }
        }
        autonomousTaskManager.approveWaitingTask()
    }

    fun onOrbTapped() {
        when (voiceState.value) {
            VoiceState.IDLE, VoiceState.ERROR -> {
                voiceCoordinator.startSession(enableMicCapture = false)
            }
            VoiceState.SPEAKING, VoiceState.THINKING -> {
                voiceCoordinator.triggerBargeIn("User tapped central Orb during playback/thinking")
            }
            VoiceState.LISTENING, VoiceState.INTERRUPTING -> {
                voiceCoordinator.stopSession()
            }
        }
    }

    fun sendTextOrVoiceUtterance(text: String) {
        if (text.isBlank()) return
        voiceCoordinator.processUserUtterance(text.trim())
    }

    fun confirmPendingAgentAction() {
        val plan = _pendingConfirmationPlan.value ?: return
        _pendingConfirmationPlan.value = null
        viewModelScope.launch {
            val results = agentOrchestrator.executePlan(plan, userConfirmed = true)
            val summary = results.joinToString(" ") { it.outputText }
            conversationRepository.appendMessage(
                conversationId = _activeConversationId.value,
                role = MessageRole.ASSISTANT,
                content = summary,
                toolInvoked = plan.steps.firstOrNull()?.toolName
            )
            _currentMessages.value = conversationRepository.getMessages(_activeConversationId.value)
            ttsEngine.speakImmediate(summary, selectedLanguage.value)
        }
    }

    fun dismissPendingAgentAction() {
        _pendingConfirmationPlan.value = null
    }

    fun setLanguage(language: AiraLanguage) {
        preferences.preferredLanguage = language
        _selectedLanguage.value = language
        voiceCoordinator.selectedLanguage = language
        ttsEngine.setLanguage(language)
    }

    fun setContinuousMode(enabled: Boolean) {
        preferences.continuousConversationMode = enabled
        _continuousMode.value = enabled
        voiceCoordinator.continuousConversationEnabled = enabled
    }

    fun setVoiceSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        preferences.voiceSpeed = clamped
        _voiceSpeed.value = clamped
        ttsEngine.setSpeechRate(clamped)
    }

    fun setMemorySavingEnabled(enabled: Boolean) {
        preferences.memorySavingEnabled = enabled
        _memorySavingEnabled.value = enabled
        memoryRepository.isMemorySavingEnabled = enabled
    }

    fun addManualMemory(category: MemoryCategory, subject: String, content: String) {
        viewModelScope.launch {
            memoryRepository.saveMemory(category, subject, content)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearAllMemories()
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val session = conversationRepository.createNewSession("Conversation ${conversations.value.size + 1}")
            _activeConversationId.value = session.id
            _currentMessages.value = emptyList()
        }
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch {
            _activeConversationId.value = conversationId
            _currentMessages.value = conversationRepository.getMessages(conversationId)
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
            if (_activeConversationId.value == conversationId) {
                startNewConversation()
            }
        }
    }

    fun downloadModel(spec: AiModelSpec) {
        viewModelScope.launch {
            modelCatalogManager.downloadModel(spec)
            _modelsCatalog.value = modelCatalogManager.getAvailableModels()
        }
    }

    fun deleteModelFile(spec: AiModelSpec) {
        viewModelScope.launch {
            modelCatalogManager.deleteModel(spec)
            _modelsCatalog.value = modelCatalogManager.getAvailableModels()
        }
    }

    fun selectModel(spec: AiModelSpec) {
        when (spec.modality.name) {
            "LLM" -> {
                preferences.selectedLlmModelId = spec.id
                llmRouter.preferNetworkEngine = (spec.id == "local-network-ollama")
            }
            "STT" -> preferences.selectedSttModelId = spec.id
            "TTS" -> preferences.selectedTtsModelId = spec.id
        }
    }

    fun onLowMemoryWarning() {
        AiraLogger.w("AiraViewModel", "System onTrimMemory / onLowMemory triggered: releasing non-essential model buffers.")
        viewModelScope.launch {
            llmRouter.release()
        }
    }

    override fun onCleared() {
        voiceCoordinator.release()
        super.onCleared()
    }
}
