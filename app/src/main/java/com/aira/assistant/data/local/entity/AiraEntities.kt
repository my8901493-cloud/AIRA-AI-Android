package com.aira.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val summary: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val languageDetected: String = "en-IN",
    val toolInvoked: String? = null,
    val latencyMs: Long? = null,
    val wasInterrupted: Boolean = false
)

@Entity(
    tableName = "memories",
    indices = [Index(value = ["category", "subjectKey"], unique = true)]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val subjectKey: String,
    val content: String,
    val confidence: Float = 1.0f,
    val userConsented: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val lastAccessedMs: Long = System.currentTimeMillis(),
    val accessCount: Int = 1
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val content: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val isCompleted: Boolean = false,
    val priority: String = "NORMAL",
    val dueTimestampMs: Long? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val message: String,
    val triggerAtMs: Long,
    val isFired: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)
