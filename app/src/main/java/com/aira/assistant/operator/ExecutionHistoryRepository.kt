package com.aira.assistant.operator

import com.aira.assistant.domain.model.OperatorTaskExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent Execution History Repository (`ExecutionHistoryRepository`) tracking all
 * 13-state `OperatorTaskExecutionRecord` checkpoints and transparent activity logs.
 */
class ExecutionHistoryRepository {

    private val _records = MutableStateFlow<List<OperatorTaskExecutionRecord>>(emptyList())
    val records: StateFlow<List<OperatorTaskExecutionRecord>> = _records.asStateFlow()

    fun saveOrUpdate(record: OperatorTaskExecutionRecord) {
        val current = _records.value.toMutableList()
        val idx = current.indexOfFirst { it.taskId == record.taskId }
        val updated = record.copy(updatedAtMs = System.currentTimeMillis())
        if (idx >= 0) {
            current[idx] = updated
        } else {
            current.add(0, updated)
        }
        _records.value = current
    }

    fun getRecord(taskId: String): OperatorTaskExecutionRecord? =
        _records.value.firstOrNull { it.taskId == taskId }
}
