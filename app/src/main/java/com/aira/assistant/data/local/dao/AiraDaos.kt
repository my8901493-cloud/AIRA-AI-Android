package com.aira.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aira.assistant.data.local.entity.AutonomousTaskEntity
import com.aira.assistant.data.local.entity.ConversationEntity
import com.aira.assistant.data.local.entity.MemoryEntity
import com.aira.assistant.data.local.entity.MessageEntity
import com.aira.assistant.data.local.entity.NoteEntity
import com.aira.assistant.data.local.entity.ReminderEntity
import com.aira.assistant.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMs DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestampMs ASC")
    suspend fun getMessagesForConversation(conversationId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAtMs DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAtMs DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category AND LOWER(subjectKey) = LOWER(:subjectKey) LIMIT 1")
    suspend fun findByCategoryAndSubject(category: String, subjectKey: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMemory(memory: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

@Dao
interface AssistantItemsDao {
    @Query("SELECT * FROM notes ORDER BY updatedAtMs DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAtMs DESC")
    suspend fun getNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAtMs DESC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAtMs DESC")
    suspend fun getTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("SELECT * FROM reminders ORDER BY triggerAtMs ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long
}

@Dao
interface AutonomousTaskDao {
    @Query("SELECT * FROM autonomous_tasks ORDER BY updatedAtMs DESC")
    fun observeAutonomousTasks(): Flow<List<AutonomousTaskEntity>>

    @Query("SELECT * FROM autonomous_tasks ORDER BY updatedAtMs DESC")
    suspend fun getAllAutonomousTasks(): List<AutonomousTaskEntity>

    @Query("SELECT * FROM autonomous_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): AutonomousTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAutonomousTask(entity: AutonomousTaskEntity)

    @Query("DELETE FROM autonomous_tasks WHERE taskId = :taskId")
    suspend fun deleteAutonomousTask(taskId: String)
}
