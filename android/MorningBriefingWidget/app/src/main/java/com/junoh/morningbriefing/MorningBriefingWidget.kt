package com.junoh.morningbriefing

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

class MorningBriefingWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        BriefingUpdateWorker.schedulePeriodic(context)
        BriefingUpdateWorker.refreshNow(context)

        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MorningBriefingWidgetReceiver::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun textOrDash(value: String?): String {
            if (value.isNullOrBlank() || value == "null") return "-"
            return value
        }

        private fun bindMarket(views: RemoteViews, data: BriefingData) {
            val ids = listOf(
                Triple(R.id.market_1_label, R.id.market_1_value, R.id.market_1_change),
                Triple(R.id.market_2_label, R.id.market_2_value, R.id.market_2_change),
                Triple(R.id.market_3_label, R.id.market_3_value, R.id.market_3_change),
                Triple(R.id.market_4_label, R.id.market_4_value, R.id.market_4_change),
                Triple(R.id.market_5_label, R.id.market_5_value, R.id.market_5_change),
            )
            val defaultLabels = listOf("환율", "KOSPI", "KOSDAQ", "NASDAQ", "S&P 500")

            for (index in ids.indices) {
                val (labelId, valueId, changeId) = ids[index]
                val item = data.market.getOrNull(index)

                views.setTextViewText(labelId, item?.label?.ifBlank { defaultLabels[index] } ?: defaultLabels[index])
                views.setTextViewText(valueId, textOrDash(item?.value))

                val direction = item?.direction ?: "flat"
                val changePercent = textOrDash(item?.changePercent)
                val arrow = when (direction) {
                    "up" -> "▲"
                    "down" -> "▼"
                    else -> "•"
                }
                views.setTextViewText(changeId, if (changePercent == "-") "-" else "$arrow $changePercent")
                val color = when (direction) {
                    "up" -> 0xFFFF5A5A.toInt()
                    "down" -> 0xFF25D6A2.toInt()
                    else -> 0xFF9AA4B2.toInt()
                }
                views.setTextColor(changeId, color)
            }
        }

        private data class WidgetNewsRow(
            val title: String,
            val subtitle: String,
            val newsIndex: Int? = null
        )

        private fun buildNewsRows(data: BriefingData): List<WidgetNewsRow> {
            val rows = mutableListOf<WidgetNewsRow>()

            data.news.forEachIndexed { index, item ->
                if (item.title.isBlank()) return@forEachIndexed
                val parts = listOf(item.category, item.source).filter { it.isNotBlank() && it != "뉴스" }
                val prefix = if (parts.isNotEmpty()) parts.joinToString(" · ") + " · " else ""
                val subtitleBody = item.summary.ifBlank { "자세한 내용은 전체 브리핑에서 확인" }
                rows += WidgetNewsRow(item.title, prefix + subtitleBody, index)
            }

            if (rows.size < 5) {
                val fallbackTitles = when {
                    data.newsTitles.isNotEmpty() -> data.newsTitles
                    data.top3.isNotEmpty() -> data.top3
                    else -> emptyList()
                }

                fallbackTitles.forEachIndexed { index, title ->
                    if (rows.size >= 5) return@forEachIndexed
                    val alreadyUsed = rows.any { it.title == title }
                    if (!alreadyUsed && title.isNotBlank()) {
                        val subtitle = when (index) {
                            0 -> "오늘의 핵심 포인트"
                            1 -> "시장과 산업 흐름에서 확인할 내용"
                            else -> "자세한 내용은 전체 브리핑에서 확인"
                        }
                        rows += WidgetNewsRow(title, subtitle, null)
                    }
                }
            }

            if (rows.isEmpty()) {
                rows += WidgetNewsRow("브리핑 데이터를 불러오는 중", "앱을 열어 지금 새로고침을 눌러주세요.", null)
            }

            return rows.take(5)
        }

        private fun setNewsRow(
            context: Context,
            views: RemoteViews,
            rowId: Int,
            imageId: Int,
            titleId: Int,
            subtitleId: Int,
            row: WidgetNewsRow?,
            allowImage: Boolean
        ) {
            if (row == null) {
                views.setViewVisibility(rowId, View.GONE)
                return
            }

            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(titleId, row.title)
            views.setTextViewText(subtitleId, row.subtitle)

            val bitmap = row.newsIndex?.let { BriefingRepository.readNewsBitmap(context, it) }
            if (allowImage && bitmap != null) {
                views.setViewVisibility(imageId, View.VISIBLE)
                views.setImageViewBitmap(imageId, bitmap)
                views.setInt(titleId, "setMaxLines", 2)
                views.setInt(subtitleId, "setMaxLines", 1)
            } else {
                // 이미지가 없으면 이미지 뷰 자체를 GONE 처리한다. 빈 썸네일 자리도 차지하지 않는다.
                views.setViewVisibility(imageId, View.GONE)
                views.setInt(titleId, "setMaxLines", 1)
                views.setInt(subtitleId, "setMaxLines", 1)
            }
        }

        private fun bindNews(context: Context, views: RemoteViews, data: BriefingData) {
            val rows = buildNewsRows(data)

            // 이미지가 많은 날은 위젯 높이가 부족해질 수 있으므로, 썸네일은 최대 2개까지만 표시한다.
            // 이미지가 없는 뉴스는 텍스트 전용 카드로 압축되어 4~5개까지 더 볼 수 있다.
            val imageAllowed = BooleanArray(rows.size)
            var imageCount = 0
            rows.forEachIndexed { index, row ->
                val hasBitmap = row.newsIndex?.let { BriefingRepository.readNewsBitmap(context, it) } != null
                if (hasBitmap && imageCount < 2) {
                    imageAllowed[index] = true
                    imageCount += 1
                }
            }

            val maxRows = when (imageCount) {
                0 -> 5
                1 -> 4
                else -> 3
            }

            setNewsRow(context, views, R.id.news_1_card, R.id.news_1_image, R.id.news_1_title, R.id.news_1_summary, rows.getOrNull(0), imageAllowed.getOrNull(0) == true)
            setNewsRow(context, views, R.id.news_2_card, R.id.news_2_image, R.id.news_2_title, R.id.news_2_summary, rows.getOrNull(1)?.takeIf { 1 < maxRows }, imageAllowed.getOrNull(1) == true)
            setNewsRow(context, views, R.id.news_3_card, R.id.news_3_image, R.id.news_3_title, R.id.news_3_summary, rows.getOrNull(2)?.takeIf { 2 < maxRows }, imageAllowed.getOrNull(2) == true)
            setNewsRow(context, views, R.id.news_4_card, R.id.news_4_image, R.id.news_4_title, R.id.news_4_summary, rows.getOrNull(3)?.takeIf { 3 < maxRows }, imageAllowed.getOrNull(3) == true)
            setNewsRow(context, views, R.id.news_5_card, R.id.news_5_image, R.id.news_5_title, R.id.news_5_summary, rows.getOrNull(4)?.takeIf { 4 < maxRows }, imageAllowed.getOrNull(4) == true)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val data = BriefingRepository.read(context)
            val error = BriefingRepository.lastError(context)
            val views = RemoteViews(context.packageName, R.layout.widget_briefing)

            views.setTextViewText(R.id.widget_title, data.title.ifBlank { "모닝 브리핑" })
            views.setTextViewText(R.id.widget_subtitle, data.subtitle.ifBlank { "오늘의 핵심" })
            views.setTextViewText(R.id.widget_date, data.date.ifBlank { "업데이트 대기 중" })

            val weatherTemp = when (data.weather.temperature) {
                "-", "null", "None", "" -> "--°"
                else -> "${data.weather.temperature}°"
            }
            views.setTextViewText(R.id.weather_temp, weatherTemp)
            views.setTextViewText(
                R.id.weather_detail,
                "${data.weather.location.ifBlank { "서울" }} · ${data.weather.condition.ifBlank { "날씨" }}"
            )
            val high = textOrDash(data.weather.high)
            val low = textOrDash(data.weather.low)
            views.setTextViewText(R.id.weather_range, "최고 $high° · 최저 $low°")

            bindMarket(views, data)
            bindNews(context, views, data)

            val footerText = when {
                error.isNotBlank() -> "업데이트 오류: $error"
                data.generatedAt.isNotBlank() -> "탭해서 전체 브리핑 보기  ›"
                else -> "앱을 열어 새로고침"
            }
            views.setTextViewText(R.id.widget_footer, footerText)

            val openIntent = if (data.briefingUrl.startsWith("http")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(data.briefingUrl))
            } else {
                Intent(context, MainActivity::class.java)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
