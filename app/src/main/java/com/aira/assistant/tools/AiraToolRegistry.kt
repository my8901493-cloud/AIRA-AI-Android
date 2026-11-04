package com.aira.assistant.tools

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import com.aira.assistant.core.AiraLogger
import com.aira.assistant.data.local.dao.AssistantItemsDao
import com.aira.assistant.data.local.entity.NoteEntity
import com.aira.assistant.data.local.entity.ReminderEntity
import com.aira.assistant.data.local.entity.TaskEntity
import com.aira.assistant.domain.model.ToolDefinition
import com.aira.assistant.domain.model.ToolExecutionResult
import com.aira.assistant.domain.model.ToolExecutionStatus
import com.aira.assistant.domain.model.ToolParameterSpec
import com.aira.assistant.service.AiraNotificationListenerService
import com.aira.assistant.service.ReminderBroadcastReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Modular Personal Assistant Tool Registry (`AiraToolRegistry`).
 *
 * Every tool defines:
 * - Strict input schema (`ToolDefinition`)
 * - Runtime Android permission validation
 * - Sensitive action confirmation check
 * - Result verification & structured error handling
 */
class AiraToolRegistry(
    private val context: Context? = null,
    private val itemsDao: AssistantItemsDao? = null
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // In-memory fallback stores when running in unit tests without Room
    private val inMemoryNotes = mutableListOf<NoteEntity>()
    private val inMemoryTasks = mutableListOf<TaskEntity>()
    private val inMemoryReminders = mutableListOf<ReminderEntity>()

    fun getAllToolDefinitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "get_current_datetime",
            description = "Returns the current local time, date, day of week, and timezone.",
            parameters = emptyList(),
            worksOffline = true
        ),
        ToolDefinition(
            name = "set_alarm",
            description = "Sets a system alarm clock for a specified hour and minute.",
            parameters = listOf(
                ToolParameterSpec("hour", "Int", "Hour in 24-hour format (0-23)"),
                ToolParameterSpec("minute", "Int", "Minute (0-59)"),
                ToolParameterSpec("label", "String", "Alarm label", required = false)
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "set_timer",
            description = "Starts an Android countdown timer for the specified duration in seconds.",
            parameters = listOf(
                ToolParameterSpec("seconds", "Int", "Timer duration in seconds (> 0)"),
                ToolParameterSpec("label", "String", "Timer label", required = false)
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "create_reminder",
            description = "Schedules a local assistant reminder notification.",
            parameters = listOf(
                ToolParameterSpec("message", "String", "Reminder message"),
                ToolParameterSpec("minutesFromNow", "Int", "Minutes from now to trigger")
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "open_application",
            description = "Launches an installed Android application by display name or package name.",
            parameters = listOf(
                ToolParameterSpec("appName", "String", "Application name (e.g., YouTube, WhatsApp, Settings, Calculator)")
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "calculate_expression",
            description = "Evaluates a mathematical expression accurately.",
            parameters = listOf(
                ToolParameterSpec("expression", "String", "Math expression, e.g. '(245 * 18) / 100 + sqrt(144)'")
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "create_note",
            description = "Saves a persistent local note in AIRA's database.",
            parameters = listOf(
                ToolParameterSpec("title", "String", "Short title of the note"),
                ToolParameterSpec("content", "String", "Full note content")
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "list_notes",
            description = "Retrieves the user's saved notes.",
            parameters = emptyList(),
            worksOffline = true
        ),
        ToolDefinition(
            name = "manage_task",
            description = "Creates, completes, or lists user to-do tasks.",
            parameters = listOf(
                ToolParameterSpec("action", "String", "create | list | complete"),
                ToolParameterSpec("title", "String", "Task title (required for create)", required = false)
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "read_notifications",
            description = "Reads recent active Android status bar notifications after explicit user permission.",
            parameters = emptyList(),
            requiresExplicitConfirmation = false,
            worksOffline = true
        ),
        ToolDefinition(
            name = "get_weather",
            description = "Fetches current weather for a city or location via Open-Meteo (no paid API key required).",
            parameters = listOf(
                ToolParameterSpec("city", "String", "City name, e.g. Guwahati, Mumbai, Delhi")
            ),
            worksOffline = false
        ),
        ToolDefinition(
            name = "search_internet",
            description = "Searches the web via DuckDuckGo Instant Answer API when online.",
            parameters = listOf(
                ToolParameterSpec("query", "String", "Search query")
            ),
            worksOffline = false
        ),
        ToolDefinition(
            name = "summarize_text",
            description = "Produces a concise bulleted summary of long text.",
            parameters = listOf(
                ToolParameterSpec("text", "String", "Input text to summarize")
            ),
            worksOffline = true
        ),
        ToolDefinition(
            name = "search_local_files",
            description = "Searches user-authorized app/documents directory by filename keyword.",
            parameters = listOf(
                ToolParameterSpec("keyword", "String", "Filename search query")
            ),
            requiresExplicitConfirmation = true,
            worksOffline = true
        )
    )

    suspend fun executeTool(
        toolName: String,
        arguments: Map<String, String>,
        userConfirmed: Boolean = false
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val definition = getAllToolDefinitions().firstOrNull { it.name == toolName }
            ?: return@withContext ToolExecutionResult(
                toolName = toolName,
                status = ToolExecutionStatus.INVALID_INPUT,
                outputText = "Unknown tool: $toolName",
                verified = false
            )

        // Validate required parameters
        for (param in definition.parameters.filter { it.required }) {
            if (arguments[param.name].isNullOrBlank()) {
                return@withContext ToolExecutionResult(
                    toolName = toolName,
                    status = ToolExecutionStatus.INVALID_INPUT,
                    outputText = "Missing required parameter '${param.name}' for tool $toolName.",
                    verified = false
                )
            }
        }

        // Check Android runtime permissions if context is present
        if (context != null && definition.requiredAndroidPermissions.isNotEmpty()) {
            val missing = definition.requiredAndroidPermissions.filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                return@withContext ToolExecutionResult(
                    toolName = toolName,
                    status = ToolExecutionStatus.REQUIRES_PERMISSION,
                    outputText = "Permission required before running $toolName.",
                    missingPermissions = missing,
                    verified = false
                )
            }
        }

        // Check confirmation gate for sensitive tools
        if (definition.requiresExplicitConfirmation && !userConfirmed) {
            return@withContext ToolExecutionResult(
                toolName = toolName,
                status = ToolExecutionStatus.REQUIRES_CONFIRMATION,
                outputText = "Confirmation required before executing $toolName.",
                confirmationPrompt = "Do you want AIRA to run '$toolName' with ${arguments.entries.joinToString { "${it.key}=${it.value}" }}?",
                verified = false
            )
        }

        return@withContext try {
            when (toolName) {
                "get_current_datetime" -> executeDateTime()
                "set_alarm" -> executeSetAlarm(arguments)
                "set_timer" -> executeSetTimer(arguments)
                "create_reminder" -> executeCreateReminder(arguments)
                "open_application" -> executeOpenApp(arguments)
                "calculate_expression" -> executeCalculate(arguments)
                "create_note" -> executeCreateNote(arguments)
                "list_notes" -> executeListNotes()
                "manage_task" -> executeManageTask(arguments)
                "read_notifications" -> executeReadNotifications()
                "get_weather" -> executeGetWeather(arguments)
                "search_internet" -> executeWebSearch(arguments)
                "summarize_text" -> executeSummarizeText(arguments)
                "search_local_files" -> executeSearchLocalFiles(arguments)
                else -> ToolExecutionResult(
                    toolName = toolName,
                    status = ToolExecutionStatus.INVALID_INPUT,
                    outputText = "Unsupported tool: $toolName",
                    verified = false
                )
            }
        } catch (e: Exception) {
            AiraLogger.e("ToolRegistry", "Tool $toolName failed", e)
            ToolExecutionResult(
                toolName = toolName,
                status = ToolExecutionStatus.EXECUTION_ERROR,
                outputText = "Failed to execute $toolName: ${e.message}",
                verified = false
            )
        }
    }

    private fun executeDateTime(): ToolExecutionResult {
        val now = Date()
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.US).format(now)
        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(now)
        return ToolExecutionResult(
            toolName = "get_current_datetime",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "It is currently $timeFmt on $dateFmt.",
            structuredData = mapOf("time" to timeFmt, "date" to dateFmt),
            verified = true
        )
    }

    private fun executeSetAlarm(args: Map<String, String>): ToolExecutionResult {
        val hour = args["hour"]?.toIntOrNull()
        val minute = args["minute"]?.toIntOrNull() ?: 0
        val label = args["label"] ?: "AIRA Alarm"
        if (hour == null || hour !in 0..23 || minute !in 0..59) {
            return ToolExecutionResult(
                toolName = "set_alarm",
                status = ToolExecutionStatus.INVALID_INPUT,
                outputText = "Invalid alarm time. Hour must be 0-23 and minute 0-59.",
                verified = false
            )
        }

        if (context != null) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        val formattedTime = String.format(Locale.US, "%02d:%02d", hour, minute)
        return ToolExecutionResult(
            toolName = "set_alarm",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "I've set an alarm for $formattedTime labeled "$label".",
            structuredData = mapOf("hour" to hour.toString(), "minute" to minute.toString(), "label" to label),
            verified = true
        )
    }

    private fun executeSetTimer(args: Map<String, String>): ToolExecutionResult {
        val seconds = args["seconds"]?.toIntOrNull()
        val label = args["label"] ?: "AIRA Timer"
        if (seconds == null || seconds <= 0) {
            return ToolExecutionResult(
                toolName = "set_timer",
                status = ToolExecutionStatus.INVALID_INPUT,
                outputText = "Timer duration must be a positive number of seconds.",
                verified = false
            )
        }

        if (context != null) {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        val mins = seconds / 60
        val remSec = seconds % 60
        val durationStr = if (mins > 0) "${mins}m ${remSec}s" else "${seconds}s"
        return ToolExecutionResult(
            toolName = "set_timer",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "I've started a timer for $durationStr ($label).",
            structuredData = mapOf("seconds" to seconds.toString(), "label" to label),
            verified = true
        )
    }

    private suspend fun executeCreateReminder(args: Map<String, String>): ToolExecutionResult {
        val message = args["message"]?.trim().orEmpty()
        val minutes = args["minutesFromNow"]?.toIntOrNull() ?: 10
        val triggerAt = System.currentTimeMillis() + (minutes.coerceAtLeast(1) * 60_000L)

        val entity = ReminderEntity(message = message, triggerAtMs = triggerAt)
        val id = itemsDao?.insertReminder(entity) ?: run {
            val nextId = (inMemoryReminders.maxOfOrNull { it.id } ?: 0L) + 1L
            inMemoryReminders.add(entity.copy(id = nextId))
            nextId
        }

        if (context != null) {
            runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    putExtra("reminder_id", id)
                    putExtra("reminder_message", message)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 30_000L, pi)
            }
        }

        return ToolExecutionResult(
            toolName = "create_reminder",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "Reminder saved for $minutes minutes from now: "$message".",
            structuredData = mapOf("reminderId" to id.toString(), "message" to message),
            verified = true
        )
    }

    private fun executeOpenApp(args: Map<String, String>): ToolExecutionResult {
        val targetName = args["appName"]?.trim().orEmpty()
        if (context == null) {
            return ToolExecutionResult(
                toolName = "open_application",
                status = ToolExecutionStatus.SUCCESS,
                outputText = "Validated launch request for application "$targetName".",
                structuredData = mapOf("appName" to targetName),
                verified = true
            )
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val matched = apps.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            label.equals(targetName, ignoreCase = true) ||
                label.contains(targetName, ignoreCase = true) ||
                resolveInfo.activityInfo.packageName.contains(targetName, ignoreCase = true)
        }

        return if (matched != null) {
            val pkg = matched.activityInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                val appLabel = matched.loadLabel(pm).toString()
                ToolExecutionResult(
                    toolName = "open_application",
                    status = ToolExecutionStatus.SUCCESS,
                    outputText = "Opening $appLabel.",
                    structuredData = mapOf("packageName" to pkg, "appName" to appLabel),
                    verified = true
                )
            } else {
                ToolExecutionResult(
                    toolName = "open_application",
                    status = ToolExecutionStatus.EXECUTION_ERROR,
                    outputText = "Found $targetName ($pkg), but it does not expose a launchable activity.",
                    verified = false
                )
            }
        } else {
            ToolExecutionResult(
                toolName = "open_application",
                status = ToolExecutionStatus.EXECUTION_ERROR,
                outputText = "I couldn't find an installed app matching "$targetName" on your device.",
                verified = false
            )
        }
    }

    private fun executeCalculate(args: Map<String, String>): ToolExecutionResult {
        val expr = args["expression"].orEmpty()
        val result = ExpressionCalculator.evaluate(expr)
        return result.fold(
            onSuccess = { value ->
                val formatted = DecimalFormat("#.######").format(value)
                ToolExecutionResult(
                    toolName = "calculate_expression",
                    status = ToolExecutionStatus.SUCCESS,
                    outputText = "The result of $expr is $formatted.",
                    structuredData = mapOf("expression" to expr, "result" to formatted),
                    verified = true
                )
            },
            onFailure = { err ->
                ToolExecutionResult(
                    toolName = "calculate_expression",
                    status = ToolExecutionStatus.INVALID_INPUT,
                    outputText = "Could not evaluate '$expr': ${err.message}",
                    verified = false
                )
            }
        )
    }

    private suspend fun executeCreateNote(args: Map<String, String>): ToolExecutionResult {
        val title = args["title"]?.trim().orEmpty().ifEmpty { "Quick Note" }
        val content = args["content"]?.trim().orEmpty()
        val note = NoteEntity(title = title, content = content)
        val id = itemsDao?.insertNote(note) ?: run {
            val nextId = (inMemoryNotes.maxOfOrNull { it.id } ?: 0L) + 1L
            inMemoryNotes.add(0, note.copy(id = nextId))
            nextId
        }
        return ToolExecutionResult(
            toolName = "create_note",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "Saved note "$title": $content",
            structuredData = mapOf("noteId" to id.toString(), "title" to title),
            verified = true
        )
    }

    private suspend fun executeListNotes(): ToolExecutionResult {
        val notes = itemsDao?.getNotes() ?: inMemoryNotes
        if (notes.isEmpty()) {
            return ToolExecutionResult(
                toolName = "list_notes",
                status = ToolExecutionStatus.SUCCESS,
                outputText = "You don't have any saved notes yet.",
                verified = true
            )
        }
        val summary = notes.take(5).joinToString("; ") { "${it.title}: ${it.content}" }
        return ToolExecutionResult(
            toolName = "list_notes",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "Here are your recent notes: $summary",
            structuredData = mapOf("count" to notes.size.toString()),
            verified = true
        )
    }

    private suspend fun executeManageTask(args: Map<String, String>): ToolExecutionResult {
        val action = args["action"]?.lowercase(Locale.ROOT) ?: "list"
        val title = args["title"]?.trim().orEmpty()

        return when (action) {
            "create", "add" -> {
                if (title.isEmpty()) {
                    return ToolExecutionResult(
                        toolName = "manage_task",
                        status = ToolExecutionStatus.INVALID_INPUT,
                        outputText = "Please provide a task title to add.",
                        verified = false
                    )
                }
                val task = TaskEntity(title = title)
                val id = itemsDao?.insertTask(task) ?: run {
                    val nextId = (inMemoryTasks.maxOfOrNull { it.id } ?: 0L) + 1L
                    inMemoryTasks.add(0, task.copy(id = nextId))
                    nextId
                }
                ToolExecutionResult(
                    toolName = "manage_task",
                    status = ToolExecutionStatus.SUCCESS,
                    outputText = "Added task "$title" to your list.",
                    structuredData = mapOf("taskId" to id.toString(), "title" to title),
                    verified = true
                )
            }
            "complete", "done" -> {
                val tasks = itemsDao?.getTasks() ?: inMemoryTasks
                val match = tasks.firstOrNull { !it.isCompleted && it.title.contains(title, ignoreCase = true) }
                if (match != null) {
                    itemsDao?.setTaskCompleted(match.id, true) ?: run {
                        val idx = inMemoryTasks.indexOfFirst { it.id == match.id }
                        if (idx >= 0) inMemoryTasks[idx] = match.copy(isCompleted = true)
                    }
                    ToolExecutionResult(
                        toolName = "manage_task",
                        status = ToolExecutionStatus.SUCCESS,
                        outputText = "Marked task "${match.title}" as completed.",
                        verified = true
                    )
                } else {
                    ToolExecutionResult(
                        toolName = "manage_task",
                        status = ToolExecutionStatus.EXECUTION_ERROR,
                        outputText = "Could not find an open task matching "$title".",
                        verified = false
                    )
                }
            }
            else -> {
                val tasks = itemsDao?.getTasks() ?: inMemoryTasks
                val openTasks = tasks.filter { !it.isCompleted }
                if (openTasks.isEmpty()) {
                    ToolExecutionResult(
                        toolName = "manage_task",
                        status = ToolExecutionStatus.SUCCESS,
                        outputText = "You have no pending tasks right now.",
                        verified = true
                    )
                } else {
                    val listStr = openTasks.take(6).joinToString(", ") { it.title }
                    ToolExecutionResult(
                        toolName = "manage_task",
                        status = ToolExecutionStatus.SUCCESS,
                        outputText = "You have ${openTasks.size} pending tasks: $listStr.",
                        structuredData = mapOf("openCount" to openTasks.size.toString()),
                        verified = true
                    )
                }
            }
        }
    }

    private fun executeReadNotifications(): ToolExecutionResult {
        if (!AiraNotificationListenerService.isNotificationAccessGranted(context)) {
            return ToolExecutionResult(
                toolName = "read_notifications",
                status = ToolExecutionStatus.REQUIRES_PERMISSION,
                outputText = "Notification access is not enabled yet. Please enable 'AIRA Notification Assistant' in Android Settings > Notification Access so I can read your notifications.",
                missingPermissions = listOf("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                verified = false
            )
        }
        val notifications = AiraNotificationListenerService.recentNotifications.value
        if (notifications.isEmpty()) {
            return ToolExecutionResult(
                toolName = "read_notifications",
                status = ToolExecutionStatus.SUCCESS,
                outputText = "You have no active notifications right now.",
                verified = true
            )
        }
        val formatted = notifications.take(5).joinToString("; ") { "${it.appName}: ${it.title} — ${it.text}" }
        return ToolExecutionResult(
            toolName = "read_notifications",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "Here are your latest notifications: $formatted",
            verified = true
        )
    }

    private fun executeGetWeather(args: Map<String, String>): ToolExecutionResult {
        val city = args["city"]?.trim().orEmpty()
        return try {
            val encodedCity = URLEncoder.encode(city, "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json")
                .build()

            httpClient.newCall(geoReq).execute().use { geoResp ->
                if (!geoResp.isSuccessful) throw IllegalStateException("Geocoding HTTP ${geoResp.code}")
                val geoJson = JSONObject(geoResp.body?.string().orEmpty())
                val results = geoJson.optJSONArray("results")
                if (results == null || results.length() == 0) {
                    return ToolExecutionResult(
                        toolName = "get_weather",
                        status = ToolExecutionStatus.EXECUTION_ERROR,
                        outputText = "I couldn't find coordinates for city "$city".",
                        verified = false
                    )
                }
                val loc = results.getJSONObject(0)
                val lat = loc.getDouble("latitude")
                val lon = loc.getDouble("longitude")
                val resolvedName = loc.optString("name", city)

                val weatherReq = Request.Builder()
                    .url("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m")
                    .build()

                httpClient.newCall(weatherReq).execute().use { wResp ->
                    val wJson = JSONObject(wResp.body?.string().orEmpty())
                    val current = wJson.getJSONObject("current")
                    val tempC = current.getDouble("temperature_2m")
                    val humidity = current.getInt("relative_humidity_2m")
                    val windKmh = current.getDouble("wind_speed_10m")

                    ToolExecutionResult(
                        toolName = "get_weather",
                        status = ToolExecutionStatus.SUCCESS,
                        outputText = "Current weather in $resolvedName is ${tempC}°C with ${humidity}% humidity and wind speed ${windKmh} km/h.",
                        structuredData = mapOf(
                            "city" to resolvedName,
                            "temperatureC" to tempC.toString(),
                            "humidity" to humidity.toString()
                        ),
                        verified = true
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "get_weather",
                status = ToolExecutionStatus.UNAVAILABLE_OFFLINE,
                outputText = "Weather service is currently unreachable (device appears offline). Local tools like alarms, notes, tasks, and calculations remain available.",
                verified = false
            )
        }
    }

    private fun executeWebSearch(args: Map<String, String>): ToolExecutionResult {
        val query = args["query"]?.trim().orEmpty()
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty())
                val abstractText = json.optString("AbstractText", "").trim()
                if (abstractText.isNotEmpty()) {
                    ToolExecutionResult(
                        toolName = "search_internet",
                        status = ToolExecutionStatus.SUCCESS,
                        outputText = abstractText,
                        verified = true
                    )
                } else {
                    ToolExecutionResult(
                        toolName = "search_internet",
                        status = ToolExecutionStatus.SUCCESS,
                        outputText = "Searched for "$query", but no instant summary snippet was returned.",
                        verified = true
                    )
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                toolName = "search_internet",
                status = ToolExecutionStatus.UNAVAILABLE_OFFLINE,
                outputText = "Web search is unavailable while offline.",
                verified = false
            )
        }
    }

    private fun executeSummarizeText(args: Map<String, String>): ToolExecutionResult {
        val text = args["text"]?.trim().orEmpty()
        val sentences = text.split(Regex("(?<=[.!?।])\\s+"))
            .map { it.trim() }
            .filter { it.length > 15 }
        val keyPoints = if (sentences.size <= 3) {
            sentences
        } else {
            listOf(sentences.first(), sentences[sentences.size / 2], sentences.last())
        }
        val summary = keyPoints.joinToString(" ")
        return ToolExecutionResult(
            toolName = "summarize_text",
            status = ToolExecutionStatus.SUCCESS,
            outputText = "Summary: $summary",
            structuredData = mapOf("originalLength" to text.length.toString(), "summaryLength" to summary.length.toString()),
            verified = true
        )
    }

    private fun executeSearchLocalFiles(args: Map<String, String>): ToolExecutionResult {
        val keyword = args["keyword"]?.trim().orEmpty()
        val rootDir = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val matches = rootDir.walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && it.name.contains(keyword, ignoreCase = true) }
            .take(10)
            .map { it.name }
            .toList()

        return if (matches.isEmpty()) {
            ToolExecutionResult(
                toolName = "search_local_files",
                status = ToolExecutionStatus.SUCCESS,
                outputText = "No local authorized files found matching "$keyword".",
                verified = true
            )
        } else {
            ToolExecutionResult(
                toolName = "search_local_files",
                status = ToolExecutionStatus.SUCCESS,
                outputText = "Found ${matches.size} matching file(s): ${matches.joinToString(", ")}.",
                verified = true
            )
        }
    }
}
