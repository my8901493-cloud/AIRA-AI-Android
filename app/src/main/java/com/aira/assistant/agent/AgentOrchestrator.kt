package com.aira.assistant.agent

import com.aira.assistant.core.AiraLogger
import com.aira.assistant.domain.model.AgentPlan
import com.aira.assistant.domain.model.AgentStep
import com.aira.assistant.domain.model.ToolExecutionResult
import com.aira.assistant.domain.model.ToolExecutionStatus
import com.aira.assistant.tools.AiraToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Modular Agent Engine (`AgentOrchestrator`).
 *
 * Implements the full pipeline:
 * User Request -> Intent Detection -> Multi-Step Planning -> Permission & Confirmation Validation
 * -> Tool Execution -> Result Verification -> Natural Language Synthesis.
 */
class AgentOrchestrator(
    private val toolRegistry: AiraToolRegistry
) {
    private val _activePlan = MutableStateFlow<AgentPlan?>(null)
    val activePlan: StateFlow<AgentPlan?> = _activePlan.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<String>>(emptyList())
    val executionLogs: StateFlow<List<String>> = _executionLogs.asStateFlow()

    /**
     * Analyzes a user utterance (in English, Hindi, or Hinglish) to detect whether one or more
     * personal assistant tools should be planned and executed.
     */
    fun createPlanForUtterance(utterance: String): AgentPlan? {
        val trimmed = utterance.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val steps = mutableListOf<AgentStep>()
        var detectedIntent = "CONVERSATION"
        var needsConfirmation = false
        var confirmationPrompt: String? = null

        // 1. Time / Date Intent
        if (lower.contains("what time") || lower.contains("current time") || lower.contains("kitne baje") ||
            lower.contains("time kya") || lower.contains("aaj kya date") || lower.contains("today's date") ||
            lower.contains("समय क्या") || lower.contains("तारीख क्या")
        ) {
            detectedIntent = "QUERY_DATETIME"
            steps.add(
                AgentStep(
                    stepIndex = 1,
                    description = "Fetch current system time and date",
                    toolName = "get_current_datetime"
                )
            )
        }

        // 2. Mathematical Calculation Intent
        val mathRegex = Regex("(?:calculate|compute|what is|kitna hota hai|kitna hai|solve)\\s+([0-9()+\\-*/%.^\\s]+)")
        val mathMatch = mathRegex.find(lower)
        if (mathMatch != null && mathMatch.groupValues[1].any { it.isDigit() }) {
            val expr = mathMatch.groupValues[1].trim()
            detectedIntent = "CALCULATE_MATH"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Evaluate mathematical expression '$expr'",
                    toolName = "calculate_expression",
                    arguments = mapOf("expression" to expr)
                )
            )
        }

        // 3. Set Alarm Intent ("set an alarm for 7:30 am", "subah 6 baje ka alarm")
        val alarmRegex = Regex("(?:alarm).*?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?", RegexOption.IGNORE_CASE)
        val alarmMatch = alarmRegex.find(lower)
        if (alarmMatch != null && (lower.contains("set") || lower.contains("laga") || lower.contains("wake"))) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val minute = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val meridiem = alarmMatch.groupValues[3].lowercase(Locale.ROOT)
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0

            detectedIntent = "SET_ALARM"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Set Android system alarm for ${String.format(Locale.US, "%02d:%02d", hour, minute)}",
                    toolName = "set_alarm",
                    arguments = mapOf("hour" to hour.toString(), "minute" to minute.toString(), "label" to "AIRA Alarm")
                )
            )
        }

        // 4. Set Timer Intent ("set a timer for 5 minutes", "10 minute ka timer")
        val timerRegex = Regex("(?:timer).*?(\\d+)\\s*(second|seconds|sec|minute|minutes|min|hour|hours)", RegexOption.IGNORE_CASE)
        val timerMatch = timerRegex.find(lower)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 1
            val unit = timerMatch.groupValues[2].lowercase(Locale.ROOT)
            val seconds = when {
                unit.startsWith("hour") -> amount * 3600
                unit.startsWith("min") -> amount * 60
                else -> amount
            }
            detectedIntent = "SET_TIMER"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Start countdown timer for $seconds seconds",
                    toolName = "set_timer",
                    arguments = mapOf("seconds" to seconds.toString(), "label" to "AIRA Timer")
                )
            )
        }

        // 5. Create Note Intent ("create a note: buy milk", "note likho ki meeting 4 baje hai")
        val noteRegex = Regex("(?:create a note|save a note|take a note|note likho|note save karo)\\s*[:,-]?\\s*(.+)$", RegexOption.IGNORE_CASE)
        noteRegex.find(trimmed)?.let { m ->
            val content = m.groupValues[1].trim()
            detectedIntent = "CREATE_NOTE"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Save note to local database",
                    toolName = "create_note",
                    arguments = mapOf("title" to content.take(28), "content" to content)
                )
            )
        }

        if (lower.contains("show my notes") || lower.contains("list my notes") || lower.contains("mere notes dikhao")) {
            detectedIntent = "LIST_NOTES"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Retrieve saved notes",
                    toolName = "list_notes"
                )
            )
        }

        // 6. Task Management Intent ("add task finish report", "show my tasks")
        val addTaskRegex = Regex("(?:add a? ?task|create a? ?task|task jodo)\\s*[:,-]?\\s*(.+)$", RegexOption.IGNORE_CASE)
        addTaskRegex.find(trimmed)?.let { m ->
            val taskTitle = m.groupValues[1].trim()
            detectedIntent = "ADD_TASK"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Create task '$taskTitle'",
                    toolName = "manage_task",
                    arguments = mapOf("action" to "create", "title" to taskTitle)
                )
            )
        }

        if (lower.contains("my tasks") || lower.contains("pending tasks") || lower.contains("to-do list") || lower.contains("mere tasks")) {
            detectedIntent = "LIST_TASKS"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "List pending user tasks",
                    toolName = "manage_task",
                    arguments = mapOf("action" to "list")
                )
            )
        }

        // 7. Open Application Intent ("open YouTube", "WhatsApp kholo")
        val openAppRegex = Regex("^(?:open|launch)\\s+([A-Za-z0-9 ]+)$|^([A-Za-z0-9 ]+)\\s+(?:kholo|open karo)$", RegexOption.IGNORE_CASE)
        openAppRegex.find(trimmed)?.let { m ->
            val appName = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (appName.isNotEmpty() && !appName.equals("settings", ignoreCase = true)) {
                detectedIntent = "OPEN_APPLICATION"
                steps.add(
                    AgentStep(
                        stepIndex = steps.size + 1,
                        description = "Launch application '$appName'",
                        toolName = "open_application",
                        arguments = mapOf("appName" to appName)
                    )
                )
            }
        }

        // 8. Weather Intent ("weather in Guwahati", "Delhi ka mausam kaisa hai")
        val weatherRegex = Regex("(?:weather in|weather at|temperature in)\\s+([A-Za-z ]+)|([A-Za-z]+)\\s+ka mausam", RegexOption.IGNORE_CASE)
        weatherRegex.find(trimmed)?.let { m ->
            val city = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (city.isNotEmpty()) {
                detectedIntent = "GET_WEATHER"
                steps.add(
                    AgentStep(
                        stepIndex = steps.size + 1,
                        description = "Fetch current weather for $city",
                        toolName = "get_weather",
                        arguments = mapOf("city" to city)
                    )
                )
            }
        }

        // 9. Read Notifications Intent
        if (lower.contains("read my notifications") || lower.contains("check notifications") || lower.contains("mere notifications padho")) {
            detectedIntent = "READ_NOTIFICATIONS"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Read active notifications with permission check",
                    toolName = "read_notifications"
                )
            )
        }

        // 10. Search Local Files Intent (Consequential/Sensitive -> requires confirmation)
        val fileSearchRegex = Regex("(?:search local files for|find file named)\\s+([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
        fileSearchRegex.find(trimmed)?.let { m ->
            val keyword = m.groupValues[1].trim()
            detectedIntent = "SEARCH_LOCAL_FILES"
            needsConfirmation = true
            confirmationPrompt = "Allow AIRA to search local files for "$keyword"?"
            steps.add(
                AgentStep(
                    stepIndex = steps.size + 1,
                    description = "Search authorized local storage for '$keyword'",
                    toolName = "search_local_files",
                    arguments = mapOf("keyword" to keyword)
                )
            )
        }

        if (steps.isEmpty()) return null

        return AgentPlan(
            requestId = UUID.randomUUID().toString(),
            originalQuery = trimmed,
            detectedIntent = detectedIntent,
            requiresUserConfirmation = needsConfirmation,
            confirmationMessage = confirmationPrompt,
            steps = steps
        )
    }

    /**
     * Executes a planned multi-step agent workflow, verifying each tool result.
     */
    suspend fun executePlan(plan: AgentPlan, userConfirmed: Boolean = false): List<ToolExecutionResult> {
        _activePlan.value = plan
        val results = mutableListOf<ToolExecutionResult>()
        val updatedSteps = plan.steps.toMutableList()

        for (i in updatedSteps.indices) {
            val step = updatedSteps[i]
            val toolName = step.toolName ?: continue
            logAgent("Executing Step ${step.stepIndex}: ${step.description} ($toolName)")

            val res = toolRegistry.executeTool(
                toolName = toolName,
                arguments = step.arguments,
                userConfirmed = userConfirmed
            )
            results.add(res)

            updatedSteps[i] = step.copy(
                status = res.status.name,
                resultSummary = res.outputText
            )
            _activePlan.value = plan.copy(steps = updatedSteps.toList())
            logAgent("Step ${step.stepIndex} result [${res.status}]: ${res.outputText}")

            if (res.status != ToolExecutionStatus.SUCCESS) {
                break
            }
        }
        return results
    }

    private fun logAgent(entry: String) {
        AiraLogger.i("AgentOrchestrator", entry)
        _executionLogs.value = (_executionLogs.value + entry).takeLast(100)
    }
}
