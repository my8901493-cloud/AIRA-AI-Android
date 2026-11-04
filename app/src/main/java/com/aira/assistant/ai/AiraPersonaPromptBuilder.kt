package com.aira.assistant.ai

import com.aira.assistant.domain.model.AiraLanguage
import com.aira.assistant.domain.model.ChatMessage
import com.aira.assistant.domain.model.MemoryItem
import com.aira.assistant.domain.model.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds context-aware prompts preserving AIRA AI's personality:
 * - Female AI assistant, friendly, intelligent, caring, and natural (never robotic).
 * - Fluent in Hindi (Devanagari), Hinglish (conversational Roman Hindi + English), and English.
 * - Injects relevant long-term memories, screen understanding context, and verified tool outputs.
 */
object AiraPersonaPromptBuilder {

    fun buildSystemPrompt(
        language: AiraLanguage,
        memories: List<MemoryItem>,
        screenContext: String?,
        toolOutput: String?
    ): String {
        val nowStr = SimpleDateFormat("EEEE, dd MMM yyyy HH:mm", Locale.US).format(Date())
        val langInstruction = when (language) {
            AiraLanguage.HINDI ->
                "Respond naturally in warm, conversational Hindi (हिन्दी) or Hinglish matching the user's phrasing."
            AiraLanguage.HINGLISH ->
                "Respond naturally in warm Indian Hinglish (natural blend of Hindi and English) or English matching how the user speaks."
            AiraLanguage.ENGLISH ->
                "Respond naturally in warm, clear, conversational English. If the user speaks Hindi or Hinglish, seamlessly understand and reply naturally."
        }

        val memoryBlock = if (memories.isNotEmpty()) {
            buildString {
                appendLine("
[Saved User Context & Long-Term Memory]")
                memories.take(8).forEach { mem ->
                    appendLine("- (${mem.category.displayName}) ${mem.subjectKey}: ${mem.content}")
                }
            }
        } else ""

        val screenBlock = if (!screenContext.isNullOrBlank()) {
            "
[Visible Android Screen Content (User Authorized)]
$screenContext
"
        } else ""

        val toolBlock = if (!toolOutput.isNullOrBlank()) {
            "
[Verified System Tool Output]
$toolOutput
"
        } else ""

        return """
You are AIRA AI, a caring, intelligent, female personal AI assistant running natively on the user's Android device.
Current Date & Time: $nowStr
Personality Rules:
- Be warm, sweet, perceptive, and concise without sounding robotic or repetitive.
- Use natural spoken cadence suitable for real-time voice playback (avoid markdown tables or raw code walls when speaking aloud unless asked for code).
- $langInstruction
- Never claim an action was performed unless verified in [Verified System Tool Output].
$memoryBlock$screenBlock$toolBlock
        """.trimIndent()
    }

    fun detectLanguageFromUtterance(text: String): AiraLanguage {
        val trimmed = text.trim()
        if (trimmed.any { it in '\u0900'..'\u097F' }) {
            return AiraLanguage.HINDI
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        val hinglishMarkers = setOf(
            "kya", "kaise", "kaisa", "kaisi", "mera", "meri", "tum", "aap",
            "batao", "karo", "kar", "hai", "hain", "nahi", "acha", "accha",
            "yaar", "bhai", "dost", "kal", "aaj", "abhi", "baj", "baje",
            "kitna", "kahan", "kyun", "chuno", "sunao", "bolo", "yaad"
        )
        val words = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val markerHits = words.count { it in hinglishMarkers }
        return if (markerHits >= 1) AiraLanguage.HINGLISH else AiraLanguage.ENGLISH
    }

    fun formatChatHistoryWindow(history: List<ChatMessage>, maxTurns: Int = 10): List<ChatMessage> {
        return history.takeLast(maxTurns * 2)
    }
}
