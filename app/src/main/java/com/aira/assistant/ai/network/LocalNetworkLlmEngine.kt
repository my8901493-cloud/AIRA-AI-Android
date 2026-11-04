package com.aira.assistant.ai.network

import com.aira.assistant.ai.AiraPersonaPromptBuilder
import com.aira.assistant.ai.LlmEngine
import com.aira.assistant.ai.LlmGenerationRequest
import com.aira.assistant.core.AiraLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Provider-independent Local Network / OpenAI-compatible Streaming Engine (`LocalNetworkLlmEngine`).
 * Works with:
 * - Self-hosted Ollama (`http://192.168.x.x:11434/v1/chat/completions`)
 * - llama.cpp HTTP server (`http://127.0.0.1:8080/v1/chat/completions`)
 * - LM Studio / vLLM / optional OpenAI-compatible endpoints without locking into any paid vendor.
 */
class LocalNetworkLlmEngine(
    private var endpointUrl: String = "http://127.0.0.1:11434/v1/chat/completions",
    private var modelName: String = "llama3.2:1b",
    private var apiKey: String = ""
) : LlmEngine {

    override val engineId: String = "local-network-llm"
    override val displayName: String
        get() = "LAN / OpenAI-Compatible ($modelName)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val activeCall = AtomicReference<Call?>(null)

    override val isReady: Boolean = true

    fun updateConfiguration(url: String, model: String, key: String = "") {
        endpointUrl = url.trim()
        modelName = model.trim()
        apiKey = key.trim()
    }

    override suspend fun initialize(): Boolean = true

    override fun cancelActiveGeneration() {
        activeCall.getAndSet(null)?.cancel()
        AiraLogger.i("LocalNetworkLlmEngine", "Cancelled active network LLM stream.")
    }

    override fun streamResponse(request: LlmGenerationRequest): Flow<String> = flow {
        val systemPrompt = AiraPersonaPromptBuilder.buildSystemPrompt(
            language = request.language,
            memories = request.relevantMemories,
            screenContext = request.screenContextText,
            toolOutput = request.toolContextOutput
        )

        val messagesJson = JSONArray()
        messagesJson.put(JSONObject().put("role", "system").put("content", systemPrompt))

        AiraPersonaPromptBuilder.formatChatHistoryWindow(request.conversationHistory).forEach { msg ->
            val roleStr = when (msg.role.name) {
                "USER" -> "user"
                "ASSISTANT" -> "assistant"
                else -> "system"
            }
            messagesJson.put(JSONObject().put("role", roleStr).put("content", msg.content))
        }
        messagesJson.put(JSONObject().put("role", "user").put("content", request.userPrompt))

        val payload = JSONObject()
            .put("model", modelName)
            .put("messages", messagesJson)
            .put("temperature", request.temperature.toDouble())
            .put("max_tokens", request.maxTokens)
            .put("stream", true)

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val call = client.newCall(reqBuilder.build())
        activeCall.set(call)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("LLM server returned HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty LLM stream body")
            BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    currentCoroutineContext().ensureActive()
                    val trimmed = line?.trim() ?: continue
                    if (!trimmed.startsWith("data:")) continue
                    val dataStr = trimmed.removePrefix("data:").trim()
                    if (dataStr == "[DONE]") break
                    runCatching {
                        val json = JSONObject(dataStr)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val contentPiece = delta?.optString("content", "") ?: ""
                            if (contentPiece.isNotEmpty()) {
                                emit(contentPiece)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (call.isCanceled()) {
                throw CancellationException("Network LLM generation cancelled by user barge-in")
            }
            throw e
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun release() {
        cancelActiveGeneration()
    }
}
