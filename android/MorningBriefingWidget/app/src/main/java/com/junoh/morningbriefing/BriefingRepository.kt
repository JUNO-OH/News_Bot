package com.junoh.morningbriefing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object BriefingRepository {
    private const val PREFS = "morning_briefing_prefs"
    private const val KEY_JSON = "latest_json"
    private const val KEY_ERROR = "last_error"

    private val weatherCodeText = mapOf(
        0 to "맑음",
        1 to "대체로 맑음",
        2 to "구름 조금",
        3 to "흐림",
        45 to "안개",
        48 to "안개",
        51 to "이슬비",
        53 to "이슬비",
        55 to "이슬비",
        61 to "비",
        63 to "비",
        65 to "강한 비",
        71 to "눈",
        73 to "눈",
        75 to "강한 눈",
        80 to "소나기",
        81 to "소나기",
        82 to "강한 소나기",
        95 to "뇌우"
    )

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

    private fun httpGet(urlText: String, accept: String = "application/json"): String {
        val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", accept)
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

    fun fetchLatestJson(context: Context): String {
        val baseUrl = context.getString(R.string.briefing_json_url)
        require(baseUrl.startsWith("http")) { "briefing_json_url을 GitHub Pages latest.json 주소로 바꿔야 합니다." }

        val separator = if (baseUrl.contains("?")) "&" else "?"
        val urlText = "$baseUrl${separator}t=${System.currentTimeMillis()}"
        val raw = httpGet(urlText)
        return enrichJsonIfNeeded(raw)
    }

    private fun enrichJsonIfNeeded(raw: String): String {
        val obj = JSONObject(raw)

        val weatherObj = obj.optJSONObject("weather")
        if (isWeatherMissing(weatherObj)) {
            try {
                obj.put("weather", fetchWeatherSnapshot())
            } catch (_: Exception) {
                // Keep the original JSON working even if weather fails.
            }
        }

        val marketArr = obj.optJSONArray("market") ?: obj.optJSONArray("markets")
        if (isMarketMissing(marketArr)) {
            try {
                obj.put("market", fetchMarketSnapshot())
            } catch (_: Exception) {
                // Keep the original JSON working even if markets fail.
            }
        }

        return obj.toString()
    }

    private fun isBlankish(value: Any?): Boolean {
        val s = value?.toString()?.trim() ?: return true
        return s.isBlank() || s == "-" || s.equals("null", true) || s.equals("none", true) || s == "--"
    }

    private fun isWeatherMissing(obj: JSONObject?): Boolean {
        if (obj == null) return true
        return isBlankish(obj.opt("temperature")) || isBlankish(obj.opt("condition"))
    }

    private fun isMarketMissing(arr: JSONArray?): Boolean {
        if (arr == null || arr.length() == 0) return true
        var validCount = 0
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (!isBlankish(item.opt("value"))) validCount += 1
        }
        return validCount < 3
    }

    private fun roundToIntText(value: Any?): String {
        return try {
            Math.round(value.toString().toDouble()).toString()
        } catch (_: Exception) {
            "-"
        }
    }

    private fun fetchWeatherSnapshot(): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=37.5665" +
            "&longitude=126.9780" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=Asia%2FSeoul"
        val data = JSONObject(httpGet(url))
        val current = data.optJSONObject("current") ?: JSONObject()
        val daily = data.optJSONObject("daily") ?: JSONObject()
        val code = current.optInt("weather_code", 0)
        val highs = daily.optJSONArray("temperature_2m_max")
        val lows = daily.optJSONArray("temperature_2m_min")
        return JSONObject().apply {
            put("location", "서울")
            put("temperature", roundToIntText(current.opt("temperature_2m")))
            put("condition", weatherCodeText[code] ?: "날씨")
            put("high", roundToIntText(highs?.opt(0)))
            put("low", roundToIntText(lows?.opt(0)))
        }
    }

    private data class MarketSpec(val label: String, val subLabel: String, val ticker: String)

    private fun fetchMarketSnapshot(): JSONArray {
        val specs = listOf(
            MarketSpec("환율", "USD/KRW", "USDKRW=X"),
            MarketSpec("KOSPI", "", "^KS11"),
            MarketSpec("KOSDAQ", "", "^KQ11"),
            MarketSpec("NASDAQ", "", "^IXIC"),
            MarketSpec("S&P 500", "", "^GSPC")
        )
        val arr = JSONArray()
        specs.forEach { spec -> arr.put(fetchMarketItem(spec)) }
        return arr
    }

    private fun fetchMarketItem(spec: MarketSpec): JSONObject {
        return try {
            val encoded = URLEncoder.encode(spec.ticker, "UTF-8")
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=10d&interval=1d"
            val payload = JSONObject(httpGet(url))
            val result = payload.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
            val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
            val close = quote.getJSONArray("close")
            val closes = mutableListOf<Double>()
            for (i in 0 until close.length()) {
                val value = close.opt(i)
                if (value != null && value.toString() != "null") {
                    closes += value.toString().toDouble()
                }
            }
            if (closes.isEmpty()) error("no close data")
            val value = closes.last()
            val previous = if (closes.size >= 2) closes[closes.size - 2] else value
            val change = value - previous
            val percent = if (previous != 0.0) change / previous * 100.0 else 0.0
            JSONObject().apply {
                put("label", spec.label)
                put("sub_label", spec.subLabel)
                put("value", formatValue(value))
                put("change", formatValue(change))
                put("change_percent", String.format(Locale.US, "%+.2f%%", percent))
                put("direction", when {
                    change > 0 -> "up"
                    change < 0 -> "down"
                    else -> "flat"
                })
            }
        } catch (_: Exception) {
            JSONObject().apply {
                put("label", spec.label)
                put("sub_label", spec.subLabel)
                put("value", "-")
                put("change", "-")
                put("change_percent", "-")
                put("direction", "flat")
            }
        }
    }

    private fun formatValue(value: Double): String {
        return if (kotlin.math.abs(value) >= 1000) {
            String.format(Locale.US, "%,.2f", value)
        } else {
            String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
    }

    fun parse(raw: String): BriefingData {
        val obj = JSONObject(raw)
        val weatherObj = obj.optJSONObject("weather")
        val weather = WeatherData(
            location = weatherObj?.optString("location", "서울") ?: "서울",
            temperature = weatherObj?.opt("temperature")?.toString()?.takeUnless { isBlankish(it) } ?: "-",
            condition = weatherObj?.optString("condition", "날씨 대기")?.takeUnless { isBlankish(it) } ?: "날씨 대기",
            high = weatherObj?.opt("high")?.toString()?.takeUnless { isBlankish(it) } ?: "-",
            low = weatherObj?.opt("low")?.toString()?.takeUnless { isBlankish(it) } ?: "-"
        )

        val marketArr = obj.optJSONArray("market") ?: obj.optJSONArray("markets")
        val market = if (marketArr != null) {
            List(marketArr.length()) { idx ->
                val item = marketArr.optJSONObject(idx) ?: JSONObject()
                MarketItem(
                    label = item.optString("label", ""),
                    subLabel = item.optString("sub_label", item.optString("subLabel", "")),
                    value = item.optString("value", "-").takeUnless { isBlankish(it) } ?: "-",
                    change = item.optString("change", "-").takeUnless { isBlankish(it) } ?: "-",
                    changePercent = item.optString("change_percent", item.optString("changePercent", "-")).takeUnless { isBlankish(it) } ?: "-",
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
                    imageUrl = item.optString("image_url", item.optString("imageUrl", ""))
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

    fun downloadNewsImages(context: Context, data: BriefingData, maxCount: Int = 8) {
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
