package com.aira.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aira.assistant.data.local.dao.AssistantItemsDao
import com.aira.assistant.data.local.dao.AutonomousTaskDao
import com.aira.assistant.data.local.dao.ConversationDao
import com.aira.assistant.data.local.dao.MemoryDao
import com.aira.assistant.data.local.entity.AutonomousTaskEntity
import com.aira.assistant.data.local.entity.ConversationEntity
import com.aira.assistant.data.local.entity.MemoryEntity
import com.aira.assistant.data.local.entity.MessageEntity
import com.aira.assistant.data.local.entity.NoteEntity
import com.aira.assistant.data.local.entity.ReminderEntity
import com.aira.assistant.data.local.entity.TaskEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        AutonomousTaskEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AiraDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun assistantItemsDao(): AssistantItemsDao
    abstract fun autonomousTaskDao(): AutonomousTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AiraDatabase? = null

        fun getInstance(context: Context): AiraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiraDatabase::class.java,
                    "aira_assistant.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
