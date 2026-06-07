package com.example.notavia.data

import org.json.JSONArray
import org.json.JSONObject


data class ChecklistItem(
    val text: String,
    val isDone: Boolean = false,
)


object ChecklistContent {
    private const val KEY_TEXT = "text"
    private const val KEY_IS_DONE = "isDone"


    fun parse(content: String): List<ChecklistItem> {
        if (content.isBlank()) return emptyList()

        return runCatching {
            val jsonItems = JSONArray(content)
            mutableListOf<ChecklistItem>().apply {
                for (index in 0 until jsonItems.length()) {
                    val item = jsonItems.getJSONObject(index)
                    val text = item.optString(KEY_TEXT).trim()
                    if (text.isNotBlank()) {
                        add(
                            ChecklistItem(
                                text = text,
                                isDone = item.optBoolean(KEY_IS_DONE, false),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }


    fun serialize(items: List<ChecklistItem>): String {
        val jsonItems = JSONArray()
        items
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotBlank() }
            .forEach { item ->
                jsonItems.put(
                    JSONObject()
                        .put(KEY_TEXT, item.text)
                        .put(KEY_IS_DONE, item.isDone),
                )
            }
        return jsonItems.toString()
    }

    fun preview(content: String): String {
        return parse(content)
            .take(3)
            .joinToString(separator = "\n") { item ->
                "${if (item.isDone) "[x]" else "[ ]"} ${item.text}"
            }
    }

    fun progress(content: String): Pair<Int, Int> {
        val items = parse(content)
        return items.count { it.isDone } to items.size
    }
}
