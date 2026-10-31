package com.example.aiassistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/** Minimal Anthropic Messages API client (non-streaming, with tool use). */
class ClaudeClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Retries up to 2 times on 429 / 5xx / 529 (overloaded). */
    suspend fun send(
        apiKey: String,
        system: String,
        messages: JsonArray,
        tools: JsonArray,
        model: String = MODEL,
    ): JsonObject {
        var attempt = 0
        while (true) {
            try {
                return call(apiKey, system, messages, tools, model)
            } catch (e: ApiException) {
                val retryable = e.code == 429 || e.code >= 500
                if (retryable && attempt < 2) {
                    attempt++
                    delay(1500L * attempt)
                } else throw e
            }
        }
    }

    private suspend fun call(
        apiKey: String,
        system: String,
        messages: JsonArray,
        tools: JsonArray,
        model: String,
    ): JsonObject = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 1024)
            put("system", system)
            put("messages", messages)
            put("tools", tools)
        }.toString()

        val request = Request.Builder()
            .url(URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, friendlyError(resp.code, body))
            Json.parseToJsonElement(body).jsonObject
        }
    }

    private fun friendlyError(code: Int, body: String): String {
        val apiMessage = try {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
        val hint = when (code) {
            401 -> "API key galat ya expire hai. Settings me check karo."
            403 -> "Is key ko ye model/permission nahi mili."
            404 -> "Model ID nahi mila (ClaudeClient.MODEL check karo)."
            429 -> "Rate limit / credits khatam. Thodi der baad try karo."
            else -> "Server error."
        }
        return "$hint (HTTP $code${apiMessage?.let { ": $it" } ?: ""})"
    }

    companion object {
        const val URL = "https://api.anthropic.com/v1/messages"
        const val MODEL = "claude-sonnet-5-5"
    }
}
