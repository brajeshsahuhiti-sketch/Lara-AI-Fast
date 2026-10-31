package com.example.aiassistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class GeminiClient {
    private val ipv4PrioritizedDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val all = Dns.SYSTEM.lookup(hostname)
                // Prioritize IPv4 to prevent 5G IPv6 DNS hangs & UnknownHostException
                all.sortedBy { if (it is Inet4Address) 0 else 1 }
            } catch (e: Exception) {
                if (hostname == "generativelanguage.googleapis.com") {
                    listOf(
                        InetAddress.getByAddress(hostname, byteArrayOf(142.toByte(), 250.toByte(), 195.toByte(), 74.toByte())),
                        InetAddress.getByAddress(hostname, byteArrayOf(142.toByte(), 250.toByte(), 192.toByte(), 106.toByte()))
                    )
                } else throw e
            }
        }
    }

    private val http = OkHttpClient.Builder()
        .dns(ipv4PrioritizedDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        contents: JsonArray,
        tools: JsonArray? = null,
    ): JsonArray {
        var attempt = 0
        while (true) {
            try {
                return call(apiKey, model, system, contents, tools)
            } catch (e: ApiException) {
                val retryable = e.code == 429 || e.code >= 500
                if (retryable && attempt < 1) {
                    attempt++
                    delay(800L)
                } else throw e
            }
        }
    }

    private suspend fun call(
        apiKey: String,
        model: String,
        system: String,
        contents: JsonArray,
        tools: JsonArray?,
    ): JsonArray = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", contents)
            if (tools != null && tools.isNotEmpty()) put("tools", toGeminiTools(tools))
            put("generationConfig", buildJsonObject {
                put("temperature", 0.6)
                put("maxOutputTokens", 250)
                if (model.contains("2.5")) {
                    put("thinkingConfig", buildJsonObject {
                        put("thinkingBudget", 0)
                    })
                }
            })
        }.toString()

        val request = Request.Builder()
            .url("$BASE_URL/$model:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("content-type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, friendlyError(resp.code, body))

            val json = Json.parseToJsonElement(body).jsonObject
            val parts = json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            if (parts == null || parts.isEmpty()) {
                val reason = json["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
                throw IllegalStateException(
                    "Gemini se khali response aaya" + (reason?.let { " (block: $it)" } ?: "") +
                        ". Safety block ya quota check karo."
                )
            }
            parts
        }
    }

    private fun toGeminiTools(defs: JsonArray): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("functionDeclarations", buildJsonArray {
                defs.forEach { d ->
                    val o = d.jsonObject
                    val name = o["name"] ?: return@forEach
                    val schema = o["input_schema"] ?: return@forEach
                    add(buildJsonObject {
                        put("name", name)
                        put("description", o["description"] ?: JsonPrimitive(""))
                        put("parameters", schema)
                    })
                }
            })
        })
    }

    private fun friendlyError(code: Int, body: String): String {
        val detail = try {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
        val hint = when (code) {
            400 -> "Gemini request, API key ya model check karo."
            401, 403 -> "Gemini API key galat hai ya API access enable nahi hai."
            404 -> "Gemini model ID nahi mila. Settings me Model ID check karo."
            429 -> "Gemini rate limit / quota hit hui; baad me try karo."
            else -> "Gemini server error."
        }
        return "$hint (HTTP$code${detail?.let { ": $it" } ?: ""})"
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODEL = "gemini-2.5-flash"
    }
}
