package com.example.aiassistant.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Every API key is stored encrypted (see [SecureBox]); each provider has its own key and model ID. */
class SettingsStore(private val context: Context) {
    private val box = SecureBox()

    private val keyLegacy = stringPreferencesKey("api_key_enc")      // v1.1: one shared key
    private val keyClaude = stringPreferencesKey("api_key_claude_enc")
    private val keyGemini = stringPreferencesKey("api_key_gemini_enc")
    private val modelClaude = stringPreferencesKey("model_claude")
    private val modelGemini = stringPreferencesKey("model_gemini")
    private val keySpeak = booleanPreferencesKey("speak_replies")
    private val keyLang = stringPreferencesKey("language")
    private val keyProvider = stringPreferencesKey("ai_provider")

    val provider: Flow<String> = context.dataStore.data.map { it[keyProvider] ?: "claude" }
    val speakReplies: Flow<Boolean> = context.dataStore.data.map { it[keySpeak] ?: true }
    val language: Flow<String> = context.dataStore.data.map { it[keyLang] ?: "hi-IN" }

    // Migration: the old single key belonged to whichever provider was selected when it was saved.
    val claudeKey: Flow<String> = context.dataStore.data.map { p ->
        val legacy = if ((p[keyProvider] ?: "claude") == "claude") p[keyLegacy] else null
        box.decrypt((p[keyClaude] ?: legacy).orEmpty())
    }
    val geminiKey: Flow<String> = context.dataStore.data.map { p ->
        val legacy = if (p[keyProvider] == "gemini") p[keyLegacy] else null
        box.decrypt((p[keyGemini] ?: legacy).orEmpty())
    }
    val claudeModel: Flow<String> = context.dataStore.data.map {
        it[modelClaude]?.takeIf { m -> m.isNotBlank() } ?: ClaudeClient.MODEL
    }
    val geminiModel: Flow<String> = context.dataStore.data.map {
        it[modelGemini]?.takeIf { m -> m.isNotBlank() } ?: GeminiClient.MODEL
    }

    suspend fun save(
        provider: String,
        claudeKey: String,
        geminiKey: String,
        claudeModel: String,
        geminiModel: String,
        speak: Boolean,
        language: String,
    ) {
        val encClaude = box.encrypt(claudeKey.trim())
        val encGemini = box.encrypt(geminiKey.trim())
        context.dataStore.edit {
            it[keyProvider] = provider
            it[keyClaude] = encClaude
            it[keyGemini] = encGemini
            it[modelClaude] = claudeModel.trim()
            it[modelGemini] = geminiModel.trim()
            it[keySpeak] = speak
            it[keyLang] = language
            it.remove(keyLegacy)
        }
    }
}
