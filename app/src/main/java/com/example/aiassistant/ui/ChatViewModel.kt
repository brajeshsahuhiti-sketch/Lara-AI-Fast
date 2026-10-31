package com.example.aiassistant.ui

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiassistant.data.ApiException
import com.example.aiassistant.data.ClaudeClient
import com.example.aiassistant.data.GeminiClient
import com.example.aiassistant.data.SettingsStore
import com.example.aiassistant.device.DeviceActions
import com.example.aiassistant.device.Tools
import com.example.aiassistant.voice.VoiceManager
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class Role { USER, ASSISTANT }
data class ChatMessage(val role: Role, val text: String)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsStore(app)
    private val client = ClaudeClient()
    private val geminiClient = GeminiClient()
    private val actions = DeviceActions(app)
    private val voice = VoiceManager(app)

    private val history = mutableListOf<JsonElement>()
    private val geminiHistory = mutableListOf<JsonElement>()
    private var warnedNoHindiVoice = false

    val messages = mutableStateListOf<ChatMessage>()

    var isLoading by mutableStateOf(false)
        private set
    var isListening by mutableStateOf(false)
        private set
    var partialText by mutableStateOf("")
        private set
    var claudeKey by mutableStateOf("")
        private set
    var geminiKey by mutableStateOf("")
        private set
    var claudeModel by mutableStateOf(ClaudeClient.MODEL)
        private set
    var geminiModel by mutableStateOf(GeminiClient.MODEL)
        private set
    var speakReplies by mutableStateOf(true)
        private set
    var language by mutableStateOf("hi-IN")
        private set
    var provider by mutableStateOf("gemini")
        private set

    var isTesting by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<String?>(null)
        private set

    private val activeKey: String get() = if (provider == "gemini") geminiKey else claudeKey

    init {
        viewModelScope.launch { settings.claudeKey.collect { claudeKey = it } }
        viewModelScope.launch { settings.geminiKey.collect { geminiKey = it } }
        viewModelScope.launch { settings.claudeModel.collect { claudeModel = it } }
        viewModelScope.launch { settings.geminiModel.collect { geminiModel = it } }
        viewModelScope.launch { settings.speakReplies.collect { speakReplies = it } }
        viewModelScope.launch { settings.language.collect { language = it } }
        viewModelScope.launch { settings.provider.collect { provider = it } }
    }

    fun saveSettings(
        aiProvider: String,
        claudeApiKey: String,
        geminiApiKey: String,
        claudeModelId: String,
        geminiModelId: String,
        speak: Boolean,
        lang: String,
    ) {
        viewModelScope.launch {
            settings.save(aiProvider, claudeApiKey, geminiApiKey, claudeModelId, geminiModelId, speak, lang)
        }
    }

    fun testConnection(aiProvider: String, key: String, model: String) {
        if (isTesting) return
        val k = key.trim()
        val m = model.trim()
        if (k.isEmpty()) {
            testResult = "❌ Pehle API key daalo."
            return
        }
        isTesting = true
        testResult = null
        viewModelScope.launch {
            testResult = try {
                if (aiProvider == "gemini") {
                    val contents = buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", "Reply with the single word OK.") }) })
                        })
                    }
                    geminiClient.generate(k, m, "Connection test.", contents)
                    "✅ Gemini ne jawab diya (model: $m)."
                } else {
                    val msgs = JsonArray(listOf(buildJsonObject {
                        put("role", "user"); put("content", "Reply with the single word OK.")
                    }))
                    client.send(k, "Connection test.", msgs, Tools.definitions, m)
                    "✅ Claude ne jawab diya (model: $m)."
                }
            } catch (e: ApiException) {
                "❌ ${e.message}"
            } catch (e: IOException) {
                "❌ Internet/DNS error (${e.javaClass.simpleName})."
            } catch (e: Exception) {
                "❌ ${e.message}"
            } finally {
                isTesting = false
            }
        }
    }

    fun clearTestResult() {
        testResult = null
    }

    fun clearChat() {
        voice.stopSpeaking()
        history.clear()
        geminiHistory.clear()
        messages.clear()
    }

    fun startVoice() {
        if (!voice.isAvailable()) {
            note("⚠️ Is phone par speech recognition service available nahi hai.")
            return
        }
        voice.stopSpeaking()
        isListening = true
        partialText = ""
        voice.startListening(
            languageTag = language,
            partial = { partialText = it },
            result = {
                isListening = false
                partialText = ""
                send(it)
            },
            failed = { code ->
                isListening = false
                partialText = ""
                voiceErrorMessage(code)?.let { note(it) }
            },
        )
    }

    fun stopVoice() {
        voice.stopListening()
        isListening = false
        partialText = ""
    }

    private fun voiceErrorMessage(code: Int): String? = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "⚠️ Mic permission do (App settings → Permissions)."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> "⚠️ Speech service ke liye internet chahiye."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "⚠️ Is bhasha ka speech pack available nahi. Settings me English chuno ya Google voice typing me Hindi download karo."
        else -> "⚠️ Voice error (code $code)."
    }

    private fun note(text: String) {
        messages.add(ChatMessage(Role.ASSISTANT, text))
    }

    private fun speakReply(text: String) {
        val ok = voice.speak(text, language)
        if (!ok && !warnedNoHindiVoice) {
            warnedNoHindiVoice = true
            note("ℹ️ Hindi voice install nahi hai. Phone Settings → General management → Text-to-speech output me Hindi (India) voice data download karo.")
        }
    }

    private fun tryOfflineMath(expr: String): String? {
        val clean = expr.replace(" ", "").replace("×", "*").replace("÷", "/")
        if (!clean.matches(Regex("^[0-9]+([+\\-*/][0-9.]+)+$"))) return null
        return try {
            val tokens = Regex("([+\\-*/])|([0-9.]+)").findAll(clean).map { it.value }.toMutableList()
            var i = 1
            while (i < tokens.size - 1) {
                val op = tokens[i]
                if (op == "*" || op == "/") {
                    val left = tokens[i - 1].toDouble()
                    val right = tokens[i + 1].toDouble()
                    val res = if (op == "*") left * right else left / right
                    tokens[i - 1] = res.toString()
                    tokens.removeAt(i)
                    tokens.removeAt(i)
                } else i += 2
            }
            var total = tokens[0].toDouble()
            i = 1
            while (i < tokens.size - 1) {
                val op = tokens[i]
                val next = tokens[i + 1].toDouble()
                if (op == "+") total += next else if (op == "-") total -= next
                i += 2
            }
            if (total % 1.0 == 0.0) total.toLong().toString() else total.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun needsDeviceTools(input: String): Boolean {
        val lower = input.lowercase()
        val keywords = listOf(
            "alarm", "timer", "open", "kholo", "khol", "call", "dial", "phone",
            "search", "dhundho", "youtube", "whatsapp", "camera", "setting", "baje", "minute", "second"
        )
        return keywords.any { lower.contains(it) }
    }

    fun send(text: String) {
        val input = text.trim()
        if (input.isEmpty() || isLoading) return

        // 1. Instant Offline Math (0ms response without internet)
        tryOfflineMath(input)?.let { ans ->
            messages.add(ChatMessage(Role.USER, input))
            messages.add(ChatMessage(Role.ASSISTANT, ans))
            if (speakReplies) speakReply(ans)
            return
        }

        if (activeKey.isBlank()) {
            note(if (provider == "gemini") "Pehle Settings (⚙️) me Gemini API key daaliye." else "Pehle Settings (⚙️) me Anthropic API key daaliye.")
            return
        }

        messages.add(ChatMessage(Role.USER, input))
        if (provider == "gemini") {
            geminiHistory.add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray { add(buildJsonObject { put("text", input) }) })
            })
        } else {
            history.add(buildJsonObject { put("role", "user"); put("content", input) })
        }
        trimHistory()
        isLoading = true

        val activeTools = if (needsDeviceTools(input)) Tools.definitions else null

        viewModelScope.launch {
            try {
                if (provider == "gemini") {
                    for (turn in 0 until MAX_TOOL_TURNS) {
                        val parts = geminiClient.generate(
                            geminiKey, geminiModel, systemPrompt(),
                            JsonArray(geminiHistory.toList()), activeTools,
                        )
                        geminiHistory.add(buildJsonObject { put("role", "model"); put("parts", parts) })

                        val blocks = parts.map { it.jsonObject }
                        val reply = blocks
                            .mapNotNull { it["text"]?.jsonPrimitive?.content }
                            .joinToString("\n").trim()
                        if (reply.isNotEmpty()) {
                            messages.add(ChatMessage(Role.ASSISTANT, reply))
                            if (speakReplies) speakReply(reply)
                        }

                        val calls = blocks.mapNotNull { it["functionCall"]?.jsonObject }
                        if (calls.isEmpty()) break

                        val responses = JsonArray(calls.map { call ->
                            val name = call["name"]?.jsonPrimitive?.content.orEmpty()
                            val args = call["args"]?.jsonObject ?: JsonObject(emptyMap())
                            buildJsonObject {
                                put("functionResponse", buildJsonObject {
                                    put("name", name)
                                    put("response", buildJsonObject { put("result", actions.execute(name, args)) })
                                })
                            }
                        })
                        geminiHistory.add(buildJsonObject { put("role", "user"); put("parts", responses) })
                    }
                } else {
                    for (turn in 0 until MAX_TOOL_TURNS) {
                        val resp = client.send(claudeKey, systemPrompt(), JsonArray(history.toList()), Tools.definitions, claudeModel)
                        val content = resp["content"]?.jsonArray ?: error("API se khali response aaya")
                        history.add(buildJsonObject { put("role", "assistant"); put("content", content) })
                        val blocks = content.map { it.jsonObject }
                        val reply = blocks
                            .filter { it["type"]?.jsonPrimitive?.content == "text" }
                            .joinToString("\n") { it["text"]?.jsonPrimitive?.content.orEmpty() }
                            .trim()
                        if (reply.isNotEmpty()) {
                            messages.add(ChatMessage(Role.ASSISTANT, reply))
                            if (speakReplies) speakReply(reply)
                        }
                        val toolUses = blocks.filter { it["type"]?.jsonPrimitive?.content == "tool_use" }
                        if (toolUses.isEmpty()) break
                        val results = JsonArray(toolUses.map { tu ->
                            val name = tu["name"]?.jsonPrimitive?.content.orEmpty()
                            val toolInput = tu["input"]?.jsonObject ?: JsonObject(emptyMap())
                            buildJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", tu["id"]?.jsonPrimitive?.content.orEmpty())
                                put("content", actions.execute(name, toolInput))
                            }
                        })
                        history.add(buildJsonObject { put("role", "user"); put("content", results) })
                    }
                }
            } catch (e: ApiException) {
                note("⚠️ ${e.message}")
            } catch (e: IOException) {
                note("⚠️ Internet/DNS issue (${e.javaClass.simpleName}).")
            } catch (e: Exception) {
                note("⚠️ Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    private fun trimHistory() {
        while (history.size > MAX_HISTORY) {
            history.removeAt(0)
            while (history.isNotEmpty() && !isPlainUserMessage(history[0])) history.removeAt(0)
        }
        while (geminiHistory.size > MAX_HISTORY) {
            geminiHistory.removeAt(0)
            while (geminiHistory.isNotEmpty() && !isGeminiUserText(geminiHistory[0])) geminiHistory.removeAt(0)
        }
    }

    private fun isGeminiUserText(e: JsonElement): Boolean {
        val o = e.jsonObject
        if (o["role"]?.jsonPrimitive?.content != "user") return false
        return o["parts"]?.jsonArray?.firstOrNull()?.jsonObject?.containsKey("text") == true
    }

    private fun isPlainUserMessage(e: JsonElement): Boolean {
        val o = e.jsonObject
        return o["role"]?.jsonPrimitive?.content == "user" && o["content"] is JsonPrimitive
    }

    private fun systemPrompt(): String {
        val now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm (z)"))
        val scriptRule = if (speakReplies)
            "Hindi jawab Devanagari (हिन्दी) me likho."
        else ""
        return """
            Tum Samsung S24 par chalne wale fast AI assistant ho. User ki bhasha me 1-2 chhote sentences me seedha jawab do. Markdown mat lagao. $scriptRule
            Samay: $now
        """.trimIndent()
    }

    override fun onCleared() {
        voice.destroy()
    }

    private companion object {
        const val MAX_TOOL_TURNS = 3
        const val MAX_HISTORY = 12
    }
}
