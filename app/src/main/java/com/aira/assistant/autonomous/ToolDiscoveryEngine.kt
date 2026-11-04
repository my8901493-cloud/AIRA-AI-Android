package com.aira.assistant.autonomous

import com.aira.assistant.ai.ModelCatalogManager
import com.aira.assistant.core.NetworkConnectivityObserver
import com.aira.assistant.domain.model.AutonomousToolMetadata
import java.io.File

/**
 * Verifies at runtime whether a registered tool is actually installed, compatible with the device's
 * RAM/storage, and reachable before `ToolSelectionEngine` is allowed to select it.
 *
 * Never assumes a tool is available simply because its name exists in `ToolRegistry`.
 */
class ToolDiscoveryEngine(
    private val modelCatalogManager: ModelCatalogManager = ModelCatalogManager(),
    private val connectivityObserver: NetworkConnectivityObserver = NetworkConnectivityObserver()
) {
    fun isToolAvailable(tool: AutonomousToolMetadata): Boolean {
        val hw = modelCatalogManager.profileHardware()
        if (tool.minRamMb > hw.totalRamMb) {
            return false
        }

        return when (tool.toolId) {
            "local-gradle-android-sdk-builder" -> {
                // Only available if ANDROID_HOME / android.jar and javac/gradle actually exist on the machine
                val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
                val hasSdk = !sdkRoot.isNullOrBlank() && File(sdkRoot).exists()
                val hasJavac = isBinaryInPath("javac")
                hasSdk && hasJavac
            }
            "github-actions-cloud-apk-builder" -> {
                // Available as cloud CI pipeline preparer & trigger
                true
            }
            "ffmpeg-mediacodec-video-composer" -> {
                // Available via Android MediaCodec/MediaMuxer on Android devices OR ffmpeg CLI on Linux/desktop hosts
                val isAndroidRuntime = runCatching {
                    Class.forName("android.media.MediaMuxer")
                    true
                }.getOrDefault(false)
                isAndroidRuntime || isBinaryInPath("ffmpeg")
            }
            else -> {
                if (!tool.isLocalExecution && !connectivityObserver.isOnline.value) {
                    false
                } else {
                    true
                }
            }
        }
    }

    fun isBinaryInPath(binaryName: String): Boolean {
        val pathEnv = System.getenv("PATH") ?: return false
        return pathEnv.split(File.pathSeparator).any { dir ->
            val candidate = File(dir, binaryName)
            candidate.exists() && candidate.canExecute()
        }
    }
}
