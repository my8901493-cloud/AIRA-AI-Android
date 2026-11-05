package com.aira.assistant.mobile

data class ClipboardHistoryEntry(
    val id: Long,
    val content: String,
    val sourceApp: String,
    val copiedAtMs: Long = System.currentTimeMillis()
)

/**
 * Universal Cross-App Clipboard Hub (`UniversalClipboardAndConverter`).
 *
 * Remembers the last 20 copied text snippets/links across apps so the user can say:
 * *"Jo link maine 5 minute pehle Chrome se copy kiya tha, woh Notes me paste kar do"*.
 */
class UniversalClipboardAndConverter {

    private val history = mutableListOf<ClipboardHistoryEntry>()
    private var nextId = 1L

    fun pushCopiedItem(content: String, sourceApp: String = "unknown"): ClipboardHistoryEntry {
        val trimmed = content.trim()
        history.removeAll { it.content == trimmed }
        val entry = ClipboardHistoryEntry(
            id = nextId++,
            content = trimmed,
            sourceApp = sourceApp
        )
        history.add(0, entry)
        if (history.size > 20) {
            history.removeAt(history.lastIndex)
        }
        return entry
    }

    fun findRecentCopiedItem(keywordOrSourceApp: String = ""): ClipboardHistoryEntry? {
        if (keywordOrSourceApp.isBlank()) return history.firstOrNull()
        return history.firstOrNull {
            it.content.contains(keywordOrSourceApp, ignoreCase = true) ||
                it.sourceApp.contains(keywordOrSourceApp, ignoreCase = true)
        }
    }

    fun getHistory(): List<ClipboardHistoryEntry> = history.toList()
}
