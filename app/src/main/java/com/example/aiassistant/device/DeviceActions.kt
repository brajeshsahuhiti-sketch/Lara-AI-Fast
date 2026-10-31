package com.example.aiassistant.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Executes the tool calls Claude makes. Returns a short result string for Claude. */
class DeviceActions(private val context: Context) {

    fun execute(name: String, input: JsonObject): String = try {
        when (name) {
            "set_alarm" -> {
                val h = input.int("hour")
                val m = input.int("minute")
                if (h !in 0..23 || m !in 0..59) return "Invalid time: $h:$m"
                launch(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, m)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, input.str("label") ?: "Assistant alarm")
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                "Alarm set for %02d:%02d".format(h, m)
            }
            "set_timer" -> {
                val s = input.int("seconds")
                if (s !in 1..86400) return "Invalid timer length: $s seconds (1 se 86400 allowed)"
                launch(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, s)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, input.str("label") ?: "Assistant timer")
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                "Timer started for $s seconds"
            }
            "open_app" -> openApp(input.str("app_name").orEmpty())
            "web_search" -> {
                val q = input.str("query").orEmpty()
                if (q.isBlank()) return "Empty query"
                launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
                "Search opened for: $q"
            }
            "open_dialer" -> {
                val n = input.str("number").orEmpty().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                if (n.isEmpty()) return "Invalid phone number"
                launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))))
                "Dialer opened with $n (user ko call khud dabana hoga)"
            }
            else -> "Unknown tool: $name"
        }
    } catch (e: ActivityNotFoundException) {
        "Is kaam ke liye phone me koi app nahi mili"
    } catch (e: Exception) {
        "Failed: ${e.message}"
    }

    private fun openApp(query: String): String {
        if (query.isBlank()) return "App ka naam khali hai"
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().equals(query, ignoreCase = true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(query, ignoreCase = true) }
            ?: return "App '$query' nahi mila"
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "App '$query' launch nahi ho sakta"
        launch(intent)
        return "Opened ${match.loadLabel(pm)}"
    }

    private fun launch(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: error("missing '$key'")

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
