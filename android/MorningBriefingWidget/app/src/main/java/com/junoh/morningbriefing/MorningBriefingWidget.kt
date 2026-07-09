package com.junoh.morningbriefing

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
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

            val listIntent = Intent(context, NewsRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // AppWidget hosts sometimes cache adapters. Unique data forces refresh.
                setData(Uri.parse("morningbriefing://news/$appWidgetId/${System.currentTimeMillis()}"))
            }
            views.setRemoteAdapter(R.id.news_list, listIntent)
            views.setEmptyView(R.id.news_list, R.id.news_empty)

            val openWidgetIntent = if (data.briefingUrl.startsWith("http")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(data.briefingUrl))
            } else {
                Intent(context, MainActivity::class.java)
            }
            val openWidgetPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openWidgetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openWidgetPendingIntent)

            val itemPendingIntent = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.news_list, itemPendingIntent)

            val footerText = when {
                error.isNotBlank() -> "업데이트 오류: $error"
                data.generatedAt.isNotBlank() -> "v10 · 저장 뉴스 ${data.news.size}개 · 뉴스 영역 스크롤 가능  ›"
                else -> "v10 · 앱을 열어 새로고침"
            }
            views.setTextViewText(R.id.widget_footer, footerText)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.news_list)
        }
    }
}
