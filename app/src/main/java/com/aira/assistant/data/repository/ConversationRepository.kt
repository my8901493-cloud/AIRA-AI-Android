package com.aira.assistant.data.repository

import com.aira.assistant.data.local.dao.ConversationDao
import com.aira.assistant.data.local.entity.ConversationEntity
import com.aira.assistant.data.local.entity.MessageEntity
import com.aira.assistant.domain.model.ChatMessage
import com.aira.assistant.domain.model.ConversationSession
import com.aira.assistant.domain.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Manages persistent Conversation Sessions & Messages in Room (`ConversationRepository`),
 * strictly separated from Long-Term Memory (`MemoryRepository`).
 */
class ConversationRepository(
    private val conversationDao: ConversationDao? = null
) {
    private val inMemorySessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    private val inMemoryMessages = MutableStateFlow<Map<Long, List<ChatMessage>>>(emptyMap())

    fun observeConversations(): Flow<List<ConversationSession>> {
        return conversationDao?.observeConversations()?.map { list ->
            list.map { it.toDomain() }
        } ?: inMemorySessions
    }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> {
        return conversationDao?.observeMessages(conversationId)?.map { list ->
            list.map { it.toDomain() }
        } ?: inMemoryMessages.map { it[conversationId].orEmpty() }
    }

    suspend fun getMessages(conversationId: Long): List<ChatMessage> {
        return conversationDao?.getMessagesForConversation(conversationId)?.map { it.toDomain() }
            ?: inMemoryMessages.value[conversationId].orEmpty()
    }

    suspend fun createNewSession(initialTitle: String = "New Conversation"): ConversationSession {
        val now = System.currentTimeMillis()
        val dao = conversationDao
        return if (dao != null) {
            val entity = ConversationEntity(title = initialTitle, createdAtMs = now, updatedAtMs = now)
            val id = dao.insertConversation(entity)
            entity.copy(id = id).toDomain()
        } else {
            val nextId = (inMemorySessions.value.maxOfOrNull { it.id } ?: 0L) + 1L
            val session = ConversationSession(id = nextId, title = initialTitle, createdAtMs = now, updatedAtMs = now)
            inMemorySessions.value = listOf(session) + inMemorySessions.value
            session
        }
    }

    suspend fun appendMessage(
        conversationId: Long,
        role: MessageRole,
        content: String,
        languageDetected: String = "en-IN",
        toolInvoked: String? = null,
        latencyMs: Long? = null,
        wasInterrupted: Boolean = false
    ): ChatMessage {
        val now = System.currentTimeMillis()
        val dao = conversationDao
        if (dao != null) {
            val entity = MessageEntity(
                conversationId = conversationId,
                role = role.name,
                content = content,
                timestampMs = now,
                languageDetected = languageDetected,
                toolInvoked = toolInvoked,
                latencyMs = latencyMs,
                wasInterrupted = wasInterrupted
            )
            val msgId = dao.insertMessage(entity)
            return entity.copy(id = msgId).toDomain()
        } else {
            val existing = inMemoryMessages.value[conversationId].orEmpty()
            val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1L
            val msg = ChatMessage(
                id = nextId,
                conversationId = conversationId,
                role = role,
                content = content,
                timestampMs = now,
                languageDetected = languageDetected,
                toolInvoked = toolInvoked,
                latencyMs = latencyMs,
                wasInterrupted = wasInterrupted
            )
            val updatedList = existing + msg
            inMemoryMessages.value = inMemoryMessages.value + (conversationId to updatedList)

            // Update session title & count on first user message
            inMemorySessions.value = inMemorySessions.value.map { s ->
                if (s.id == conversationId) {
                    val newTitle = if (s.messageCount == 0 && role == MessageRole.USER) {
                        content.take(36)
                    } else s.title
                    s.copy(title = newTitle, updatedAtMs = now, messageCount = updatedList.size)
                } else s
            }
            return msg
        }
    }

    suspend fun deleteConversation(conversationId: Long) {
        conversationDao?.deleteConversation(conversationId)
        inMemorySessions.value = inMemorySessions.value.filterNot { it.id == conversationId }
        inMemoryMessages.value = inMemoryMessages.value - conversationId
    }

    private fun ConversationEntity.toDomain() = ConversationSession(
        id = id,
        title = title,
        summary = summary,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        messageCount = messageCount
    )

    private fun MessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.ASSISTANT),
        content = content,
        timestampMs = timestampMs,
        languageDetected = languageDetected,
        toolInvoked = toolInvoked,
        latencyMs = latencyMs,
        wasInterrupted = wasInterrupted
    )
}
