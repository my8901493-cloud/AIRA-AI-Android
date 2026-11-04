package com.aira.assistant.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aira.assistant.domain.model.AiraLanguage

/**
 * Encrypted Configuration & Privacy Storage (`SecurityPreferences`).
 *
 * Uses Android Keystore `EncryptedSharedPreferences` (`AES256_SIV` / `AES256_GCM`)
 * when running on an Android device, with an in-memory map fallback for JVM unit tests.
 * Never embeds API keys in source code.
 */
class SecurityPreferences(context: Context? = null) {

    private val memoryMap = mutableMapOf<String, Any>()
    private val prefs: SharedPreferences? = runCatching {
        if (context != null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "aira_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } else null
    }.getOrNull()

    var selectedLlmModelId: String
        get() = getString("selected_llm_model_id", "aira-local-contextual-v1")
        set(value) = putString("selected_llm_model_id", value)

    var selectedSttModelId: String
        get() = getString("selected_stt_model_id", "android-speech-recognizer")
        set(value) = putString("selected_stt_model_id", value)

    var selectedTtsModelId: String
        get() = getString("selected_tts_model_id", "android-native-female-tts")
        set(value) = putString("selected_tts_model_id", value)

    var preferredLanguage: AiraLanguage
        get() = AiraLanguage.fromCode(getString("preferred_language", AiraLanguage.HINGLISH.code))
        set(value) = putString("preferred_language", value.code)

    var continuousConversationMode: Boolean
        get() = getBoolean("continuous_conversation", true)
        set(value) = putBoolean("continuous_conversation", value)

    var voiceSpeed: Float
        get() = getFloat("voice_speed", 1.02f)
        set(value) = putFloat("voice_speed", value.coerceIn(0.5f, 2.0f))

    var memorySavingEnabled: Boolean
        get() = getBoolean("memory_saving_enabled", true)
        set(value) = putBoolean("memory_saving_enabled", value)

    var offlineOnlyStrictMode: Boolean
        get() = getBoolean("offline_only_strict_mode", false)
        set(value) = putBoolean("offline_only_strict_mode", value)

    var lanLlmServerUrl: String
        get() = getString("lan_llm_server_url", "http://127.0.0.1:11434/v1/chat/completions")
        set(value) = putString("lan_llm_server_url", value)

    var lanLlmModelName: String
        get() = getString("lan_llm_model_name", "llama3.2:1b")
        set(value) = putString("lan_llm_model_name", value)

    private fun getString(key: String, defaultVal: String): String =
        prefs?.getString(key, defaultVal) ?: (memoryMap[key] as? String ?: defaultVal)

    private fun putString(key: String, value: String) {
        memoryMap[key] = value
        prefs?.edit()?.putString(key, value)?.apply()
    }

    private fun getBoolean(key: String, defaultVal: Boolean): Boolean =
        prefs?.getBoolean(key, defaultVal) ?: (memoryMap[key] as? Boolean ?: defaultVal)

    private fun putBoolean(key: String, value: Boolean) {
        memoryMap[key] = value
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    private fun getFloat(key: String, defaultVal: Float): Float =
        prefs?.getFloat(key, defaultVal) ?: (memoryMap[key] as? Float ?: defaultVal)

    private fun putFloat(key: String, value: Float) {
        memoryMap[key] = value
        prefs?.edit()?.putFloat(key, value)?.apply()
    }
}
