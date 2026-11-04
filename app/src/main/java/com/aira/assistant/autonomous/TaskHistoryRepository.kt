package com.aira.assistant.autonomous

import com.aira.assistant.data.local.dao.AutonomousTaskDao
import com.aira.assistant.data.local.entity.AutonomousTaskEntity
import com.aira.assistant.domain.model.AutonomousTaskRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent Checkpoint & Task State Repository (`TaskHistoryRepository`).
 *
 * Saves every stage transition and step completion checkpoint to Room (`autonomous_tasks` table)
 * and an in-memory StateFlow cache so interrupted or backgrounded tasks can safely resume
 * from their last verified step without repeating completed work.
 */
class TaskHistoryRepository(
    private val taskDao: AutonomousTaskDao? = null
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val inMemoryTasks = MutableStateFlow<List<AutonomousTaskRecord>>(emptyList())

    fun observeTasks(): Flow<List<AutonomousTaskRecord>> {
        return taskDao?.observeAutonomousTasks()?.map { entities ->
            entities.mapNotNull { runCatching { json.decodeFromString<AutonomousTaskRecord>(it.serializedRecordJson) }.getOrNull() }
        } ?: inMemoryTasks
    }

    suspend fun saveCheckpoint(record: AutonomousTaskRecord, workspacePath: String = "") {
        val updated = record.copy(updatedAtMs = System.currentTimeMillis())
        val completedSteps = updated.steps.count { it.status == "COMPLETED" }

        val dao = taskDao
        if (dao != null) {
            val entity = AutonomousTaskEntity(
                taskId = updated.taskId,
                title = updated.title,
                inputType = updated.inputType.name,
                category = updated.category.name,
                stage = updated.stage.name,
                progressFraction = updated.progressFraction,
                serializedRecordJson = json.encodeToString(updated),
                lastCompletedStepNumber = completedSteps,
                workspaceDirPath = workspacePath,
                createdAtMs = updated.createdAtMs,
                updatedAtMs = updated.updatedAtMs
            )
            dao.upsertAutonomousTask(entity)
        }

        val current = inMemoryTasks.value.toMutableList()
        val idx = current.indexOfFirst { it.taskId == updated.taskId }
        if (idx >= 0) {
            current[idx] = updated
        } else {
            current.add(0, updated)
        }
        inMemoryTasks.value = current
    }

    suspend fun getTask(taskId: String): AutonomousTaskRecord? {
        val dao = taskDao
        if (dao != null) {
            val entity = dao.getTaskById(taskId)
            if (entity != null) {
                return runCatching { json.decodeFromString<AutonomousTaskRecord>(entity.serializedRecordJson) }.getOrNull()
            }
        }
        return inMemoryTasks.value.firstOrNull { it.taskId == taskId }
    }

    suspend fun deleteTask(taskId: String) {
        taskDao?.deleteAutonomousTask(taskId)
        inMemoryTasks.value = inMemoryTasks.value.filterNot { it.taskId == taskId }
    }
}
