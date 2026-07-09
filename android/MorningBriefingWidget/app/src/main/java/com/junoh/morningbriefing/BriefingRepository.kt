package com.junoh.morningbriefing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
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
        val baseUrl = context.getString(R.string.briefing_json_url)
        require(baseUrl.startsWith("http")) { "briefing_json_url을 GitHub Pages latest.json 주소로 바꿔야 합니다." }

        // GitHub Pages/CDN 또는 Android 네트워크 캐시가 예전 latest.json을 돌려주는 경우를 막는다.
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val urlText = "$baseUrl${separator}t=${System.currentTimeMillis()}"

        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
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
        val weatherObj = obj.optJSONObject("weather")
        val weather = WeatherData(
            location = weatherObj?.optString("location", "서울") ?: "서울",
            temperature = weatherObj?.opt("temperature")?.toString() ?: "-",
            condition = weatherObj?.optString("condition", "날씨 대기") ?: "날씨 대기",
            high = weatherObj?.opt("high")?.toString() ?: "-",
            low = weatherObj?.opt("low")?.toString() ?: "-"
        )

        val marketArr = obj.optJSONArray("market")
        val market = if (marketArr != null) {
            List(marketArr.length()) { idx ->
                val item = marketArr.optJSONObject(idx) ?: JSONObject()
                MarketItem(
                    label = item.optString("label", ""),
                    subLabel = item.optString("sub_label", ""),
                    value = item.optString("value", "-"),
                    change = item.optString("change", "-"),
                    changePercent = item.optString("change_percent", "-"),
                    direction = item.optString("direction", "flat")
                )
            }.filter { it.label.isNotBlank() }
        } else emptyList()

        val newsArr = obj.optJSONArray("news")
        val news = if (newsArr != null) {
            List(newsArr.length()) { idx ->
                val item = newsArr.optJSONObject(idx) ?: JSONObject()
                NewsItem(
                    title = item.optString("title", ""),
                    summary = item.optString("summary", ""),
                    source = item.optString("source", ""),
                    category = item.optString("category", "뉴스"),
                    url = item.optString("url", ""),
                    imageUrl = item.optString("image_url", "")
                )
            }.filter { it.title.isNotBlank() }
        } else emptyList()

        return BriefingData(
            date = obj.optString("date", ""),
            title = obj.optString("title", "모닝 브리핑"),
            subtitle = obj.optString("subtitle", "오늘의 핵심"),
            weather = weather,
            market = market,
            top3 = obj.optJSONArray("top3")?.let { arr ->
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList(),
            keywords = obj.optJSONArray("keywords")?.let { arr ->
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList(),
            newsTitles = obj.optJSONArray("news_titles")?.let { arr ->
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList(),
            news = news,
            briefingUrl = obj.optString("briefing_url", ""),
            generatedAt = obj.optString("generated_at", "")
        )
    }

    fun imageFile(context: Context, index: Int): File = File(context.filesDir, "briefing_news_$index.jpg")

    fun downloadNewsImages(context: Context, data: BriefingData, maxCount: Int = 5) {
        // 이전 기사 이미지가 남아 있으면 이미지가 없는 오늘 기사에 오래된 사진이 붙을 수 있으므로 먼저 삭제한다.
        for (index in 0 until maxCount) {
            try {
                imageFile(context, index).delete()
            } catch (_: Exception) {
                // ignore
            }
        }

        data.news.take(maxCount).forEachIndexed { index, news ->
            val url = news.imageUrl
            if (!url.startsWith("http")) return@forEachIndexed
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    useCaches = false
                    setRequestProperty("User-Agent", "MorningBriefingWidget/1.0")
                }
                connection.inputStream.use { input ->
                    imageFile(context, index).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()
            } catch (_: Exception) {
                // Images are nice-to-have. Keep text widget working even if thumbnails fail.
            }
        }
    }

    fun readNewsBitmap(context: Context, index: Int): Bitmap? {
        val file = imageFile(context, index)
        if (!file.exists() || file.length() == 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
