package com.junoh.morningbriefing

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object BriefingRepository {
    private const val PREFS = "morning_briefing_prefs"
    private const val KEY_JSON = "latest_json"
    private const val KEY_ERROR = "last_error"

    fun read(context: Context): BriefingData {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
        if (raw.isNullOrBlank()) return BriefingData()
        return parse(raw)
    }

    fun lastError(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ERROR, "") ?: ""
    }

    fun save(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, json)
            .putString(KEY_ERROR, "")
            .apply()
    }

    fun saveError(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ERROR, message)
            .apply()
    }

    fun fetchLatestJson(context: Context): String {
        val urlText = context.getString(R.string.briefing_json_url)
        require(urlText.startsWith("http")) { "briefing_json_url을 GitHub Pages latest.json 주소로 바꿔야 합니다." }

        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MorningBriefingWidget/1.0")
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) error("HTTP $code: $body")
            body
        } finally {
            connection.disconnect()
        }
    }

    fun parse(raw: String): BriefingData {
        val obj = JSONObject(raw)
        return BriefingData(
            date = obj.optString("date", ""),
            title = obj.optString("title", "오늘의 모닝 브리핑"),
            top3 = obj.optJSONArray("top3")?.let { arr ->
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList(),
            keywords = obj.optJSONArray("keywords")?.let { arr ->
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList(),
            newsTitles = obj.optJSONArray("news_titles")?.let { arr ->
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList(),
            briefingUrl = obj.optString("briefing_url", ""),
            generatedAt = obj.optString("generated_at", "")
        )
    }
}
