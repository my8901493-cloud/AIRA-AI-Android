package com.aira.assistant.ai

import com.aira.assistant.ai.local.LocalOnDeviceLlmEngine
import com.aira.assistant.ai.network.LocalNetworkLlmEngine
import com.aira.assistant.core.AiraLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Routes AI generation requests to the selected primary engine (Local On-Device GGUF or LAN Server)
 * and automatically falls back to the built-in local engine if the LAN server is unreachable
 * or if a model cannot be loaded due to low memory.
 */
class CompositeLlmRouter(
    private val localEngine: LocalOnDeviceLlmEngine,
    private val networkEngine: LocalNetworkLlmEngine
) : LlmEngine {

    @Volatile
    var preferNetworkEngine: Boolean = false

    override val engineId: String
        get() = if (preferNetworkEngine) networkEngine.engineId else localEngine.engineId

    override val displayName: String
        get() = if (preferNetworkEngine) networkEngine.displayName else localEngine.displayName

    override val isReady: Boolean
        get() = true

    override suspend fun initialize(): Boolean {
        localEngine.initialize()
        networkEngine.initialize()
        return true
    }

    override fun streamResponse(request: LlmGenerationRequest): Flow<String> = flow {
        val primary: LlmEngine = if (preferNetworkEngine) networkEngine else localEngine
        try {
            primary.streamResponse(request).collect { token ->
                emit(token)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            AiraLogger.w("CompositeLlmRouter", "Primary engine (${primary.displayName}) failed: ${e.message}. Falling back to local engine.")
            emitAll(localEngine.streamResponse(request))
        }
    }

    override fun cancelActiveGeneration() {
        localEngine.cancelActiveGeneration()
        networkEngine.cancelActiveGeneration()
    }

    override suspend fun release() {
        localEngine.release()
        networkEngine.release()
    }
}
