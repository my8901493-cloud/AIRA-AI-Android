package com.aira.assistant.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: String,
    val tag: String,
    val level: String,
    val message: String
)

object AiraLogger {
    private const val MAX_LOGS = 250
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    fun i(tag: String, message: String) {
        runCatching { Log.i("AIRA_$tag", message) }
        append(tag, "INFO", message)
    }

    fun w(tag: String, message: String) {
        runCatching { Log.w("AIRA_$tag", message) }
        append(tag, "WARN", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message (${throwable.message})" else message
        runCatching { Log.e("AIRA_$tag", fullMsg, throwable) }
        append(tag, "ERROR", fullMsg)
    }

    private fun append(tag: String, level: String, message: String) {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val entry = DiagnosticLogEntry(
            timestamp = formatter.format(Date()),
            tag = tag,
            level = level,
            message = message
        )
        val updated = (_logs.value + entry).takeLast(MAX_LOGS)
        _logs.value = updated
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
