package com.aira.assistant.data.repository

import com.aira.assistant.core.AiraLogger
import com.aira.assistant.data.local.dao.MemoryDao
import com.aira.assistant.data.local.entity.MemoryEntity
import com.aira.assistant.domain.model.MemoryCategory
import com.aira.assistant.domain.model.MemoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Persistent Long-Term Memory Engine (`MemoryRepository`).
 *
 * Implements:
 * 1. User consent control (`isMemorySavingEnabled`).
 * 2. Sensitive data guard: blocks saving passwords, OTPs, credit card / Aadhaar / PAN numbers, or secret keys.
 * 3. Deduplication & Contradiction resolution: if a memory with the same `(category, subjectKey)`
 *    already exists (e.g., user updates `preferred_language` or `user_name`), updates the existing record
 *    instead of creating a duplicate or contradictory entry.
 * 4. Relevant memory retrieval for prompt injection based on keyword & subject overlap.
 */
class MemoryRepository(
    private val memoryDao: MemoryDao? = null
) {
    var isMemorySavingEnabled: Boolean = true

    // In-memory store used alongside Room (or standalone during JVM unit tests)
    private val inMemoryCache = MutableStateFlow<List<MemoryItem>>(emptyList())

    private val sensitivePatterns = listOf(
        Regex("\\b(?:password|passcode|pin|otp|cvv|secret_key|api_key|private_key)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}(?:[- ]?\\d{4})?\\b"), // 12-16 digit card/Aadhaar numbers
        Regex("\\b[A-Z]{5}\\d{4}[A-Z]\\b") // Indian PAN format
    )

    fun observeMemories(): Flow<List<MemoryItem>> {
        return memoryDao?.observeAllMemories()?.map { list ->
            list.map { it.toDomain() }
        } ?: inMemoryCache
    }

    fun containsSensitiveData(text: String): Boolean {
        return sensitivePatterns.any { it.containsMatchIn(text) }
    }

    /**
     * Saves or updates a memory item after checking user consent, sensitive data filtering,
     * and deduplication/contradiction resolution on `(category, subjectKey)`.
     */
    suspend fun saveMemory(
        category: MemoryCategory,
        subjectKey: String,
        content: String,
        confidence: Float = 1.0f,
        userConsented: Boolean = true
    ): Result<MemoryItem> {
        if (!isMemorySavingEnabled || !userConsented) {
            return Result.failure(IllegalStateException("Memory saving is disabled by user privacy settings."))
        }

        val normalizedSubject = subjectKey.trim().lowercase(Locale.ROOT)
        val trimmedContent = content.trim()
        if (normalizedSubject.isEmpty() || trimmedContent.isEmpty()) {
            return Result.failure(IllegalArgumentException("Memory subject and content cannot be empty."))
        }

        if (containsSensitiveData(trimmedContent) || containsSensitiveData(normalizedSubject)) {
            AiraLogger.w("MemoryRepository", "Blocked saving sensitive information for subject '$normalizedSubject'")
            return Result.failure(SecurityException("Sensitive information (passwords, OTPs, card/ID numbers) is never stored in memory."))
        }

        val now = System.currentTimeMillis()
        val dao = memoryDao
        if (dao != null) {
            val existing = dao.findByCategoryAndSubject(category.name, normalizedSubject)
            val entityToSave = if (existing != null) {
                // Contradiction / duplicate resolution: update existing fact in-place
                existing.copy(
                    content = trimmedContent,
                    confidence = confidence,
                    updatedAtMs = now,
                    lastAccessedMs = now,
                    accessCount = existing.accessCount + 1
                )
            } else {
                MemoryEntity(
                    category = category.name,
                    subjectKey = normalizedSubject,
                    content = trimmedContent,
                    confidence = confidence,
                    userConsented = true,
                    createdAtMs = now,
                    updatedAtMs = now,
                    lastAccessedMs = now,
                    accessCount = 1
                )
            }
            val id = dao.insertOrUpdateMemory(entityToSave)
            val saved = entityToSave.copy(id = if (existing != null) existing.id else id).toDomain()
            return Result.success(saved)
        } else {
            val current = inMemoryCache.value.toMutableList()
            val idx = current.indexOfFirst {
                it.category == category && it.subjectKey.equals(normalizedSubject, ignoreCase = true)
            }
            val item = if (idx >= 0) {
                val old = current[idx]
                old.copy(
                    content = trimmedContent,
                    confidence = confidence,
                    updatedAtMs = now,
                    lastAccessedMs = now,
                    accessCount = old.accessCount + 1
                ).also { current[idx] = it }
            } else {
                val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                MemoryItem(
                    id = newId,
                    category = category,
                    subjectKey = normalizedSubject,
                    content = trimmedContent,
                    confidence = confidence,
                    userConsented = true,
                    createdAtMs = now,
                    updatedAtMs = now,
                    lastAccessedMs = now,
                    accessCount = 1
                ).also { current.add(0, it) }
            }
            inMemoryCache.value = current
            return Result.success(item)
        }
    }

    /**
     * Automatically extracts explicit user memory requests from natural Hindi/Hinglish/English speech,
     * such as "My name is Rahul", "Mera naam Priya hai", "Remember that my exam is on Friday",
     * or "I prefer dark roast coffee".
     */
    suspend fun extractAndStoreImplicitMemories(userUtterance: String): List<MemoryItem> {
        if (!isMemorySavingEnabled) return emptyList()
        val savedList = mutableListOf<MemoryItem>()
        val trimmed = userUtterance.trim()

        // 1. Name extraction (English / Hinglish / Hindi)
        val nameRegexes = listOf(
            Regex("(?:my name is|call me|i am|mera naam)\\s+([A-Za-z\\u0900-\\u097F]{2,25})(?:\\s+hai)?", RegexOption.IGNORE_CASE),
            Regex("मेरा नाम\\s+([\\u0900-\\u097FA-Za-z]{2,25})\\s*है")
        )
        for (regex in nameRegexes) {
            val match = regex.find(trimmed)
            if (match != null) {
                val name = match.groupValues[1].trim()
                saveMemory(MemoryCategory.IMPORTANT_FACT, "user_name", "User's name is $name").getOrNull()?.let {
                    savedList.add(it)
                }
                break
            }
        }

        // 2. Explicit "Remember that ..." / "Yaad rakhna ki ..."
        val rememberRegex = Regex("(?:remember that|please remember|yaad rakhna ki|yaad rakho)\\s+(.+)$", RegexOption.IGNORE_CASE)
        rememberRegex.find(trimmed)?.let { match ->
            val fact = match.groupValues[1].trim()
            val key = "note_" + fact.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .take(24)
                .trim('_')
            saveMemory(MemoryCategory.IMPORTANT_FACT, key.ifEmpty { "custom_fact" }, fact).getOrNull()?.let {
                savedList.add(it)
            }
        }

        // 3. Preference extraction ("I prefer ...", "My favorite ... is ...")
        val prefRegex = Regex("(?:i prefer|my favorite|mujhe pasand hai)\\s+(.+)$", RegexOption.IGNORE_CASE)
        prefRegex.find(trimmed)?.let { match ->
            val pref = match.groupValues[1].trim()
            val key = "pref_" + pref.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .take(24)
                .trim('_')
            saveMemory(MemoryCategory.USER_PREFERENCE, key.ifEmpty { "user_preference" }, pref).getOrNull()?.let {
                savedList.add(it)
            }
        }

        return savedList
    }

    /**
     * Retrieves top relevant memories for a given user query, always including core identity facts
     * (`user_name`, `preferred_language`) plus memories matching query tokens.
     */
    suspend fun retrieveRelevantMemories(query: String, limit: Int = 6): List<MemoryItem> {
        val all = memoryDao?.getAllMemories()?.map { it.toDomain() } ?: inMemoryCache.value
        if (all.isEmpty()) return emptyList()

        val tokens = query.lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
            .filter { it.length >= 3 }
            .toSet()

        return all.sortedByDescending { item ->
            var score = 0
            if (item.subjectKey == "user_name" || item.category == MemoryCategory.USER_PREFERENCE) {
                score += 5
            }
            val combined = "${item.subjectKey} ${item.content}".lowercase(Locale.ROOT)
            tokens.forEach { tok ->
                if (combined.contains(tok)) score += 10
            }
            score * 1_000_000L + (item.updatedAtMs / 1000L)
        }.take(limit)
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao?.deleteMemoryById(id)
        inMemoryCache.value = inMemoryCache.value.filterNot { it.id == id }
    }

    suspend fun clearAllMemories() {
        memoryDao?.clearAllMemories()
        inMemoryCache.value = emptyList()
    }

    private fun MemoryEntity.toDomain(): MemoryItem = MemoryItem(
        id = id,
        category = runCatching { MemoryCategory.valueOf(category) }.getOrDefault(MemoryCategory.IMPORTANT_FACT),
        subjectKey = subjectKey,
        content = content,
        confidence = confidence,
        userConsented = userConsented,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        lastAccessedMs = lastAccessedMs,
        accessCount = accessCount
    )
}
