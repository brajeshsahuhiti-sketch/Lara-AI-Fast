package com.example.aiassistant.device

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Tool definitions sent to Claude so it can control the phone. */
object Tools {
    val definitions: JsonArray = buildJsonArray {
        add(
            tool(
                "set_alarm", "Phone me ek alarm set karo.",
                buildJsonObject {
                    put("hour", prop("integer", "Hour, 24-hour format (0-23)"))
                    put("minute", prop("integer", "Minute (0-59)"))
                    put("label", prop("string", "Alarm ka naam"))
                },
                listOf("hour", "minute"),
            )
        )
        add(
            tool(
                "set_timer", "Countdown timer start karo.",
                buildJsonObject {
                    put("seconds", prop("integer", "Timer ki length seconds me"))
                    put("label", prop("string", "Timer ka naam"))
                },
                listOf("seconds"),
            )
        )
        add(
            tool(
                "open_app", "Phone me installed app kholo (naam se).",
                buildJsonObject { put("app_name", prop("string", "App ka naam, jaise YouTube ya WhatsApp")) },
                listOf("app_name"),
            )
        )
        add(
            tool(
                "web_search", "Browser me Google search kholo.",
                buildJsonObject { put("query", prop("string", "Search query")) },
                listOf("query"),
            )
        )
        add(
            tool(
                "open_dialer", "Dialer kholo number pehle se bhar ke (call user khud dabayega).",
                buildJsonObject { put("number", prop("string", "Phone number")) },
                listOf("number"),
            )
        )
    }

    private fun prop(type: String, description: String) = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun tool(name: String, description: String, props: JsonObject, required: List<String>) =
        buildJsonObject {
            put("name", name)
            put("description", description)
            put("input_schema", buildJsonObject {
                put("type", "object")
                put("properties", props)
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            })
        }
}
