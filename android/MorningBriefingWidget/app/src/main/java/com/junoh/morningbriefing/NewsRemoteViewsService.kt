package com.junoh.morningbriefing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class NewsRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return NewsRemoteViewsFactory(applicationContext)
    }
}

private data class WidgetNewsRow(
    val title: String,
    val subtitle: String,
    val url: String,
    val newsIndex: Int? = null
)

class NewsRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<WidgetNewsRow> = emptyList()

    override fun onCreate() {
        loadRows()
    }

    override fun onDataSetChanged() {
        loadRows()
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position)
            ?: WidgetNewsRow("브리핑 데이터를 불러오는 중", "앱을 열어 새로고침해 주세요.", "", null)

        val bitmap = row.newsIndex?.let { BriefingRepository.readNewsBitmap(context, it) }
        val layoutId = if (bitmap != null) {
            R.layout.widget_news_item_image
        } else {
            R.layout.widget_news_item_text
        }

        val views = RemoteViews(context.packageName, layoutId)
        views.setTextViewText(R.id.news_item_title, row.title)
        views.setTextViewText(R.id.news_item_subtitle, row.subtitle)

        if (bitmap != null) {
            views.setImageViewBitmap(R.id.news_item_image, bitmap)
        }

        val fillInIntent = if (row.url.startsWith("http")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(row.url))
        } else {
            Intent(context, MainActivity::class.java)
        }
        views.setOnClickFillInIntent(R.id.news_item_root, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    private fun loadRows() {
        val data = BriefingRepository.read(context)
        val result = mutableListOf<WidgetNewsRow>()

        data.news.forEachIndexed { index, item ->
            if (item.title.isBlank()) return@forEachIndexed
            val parts = listOf(item.category, item.source)
                .filter { it.isNotBlank() && it != "뉴스" }
            val prefix = if (parts.isNotEmpty()) parts.joinToString(" · ") + " · " else ""
            val subtitleBody = item.summary.ifBlank { "자세한 내용은 전체 브리핑에서 확인" }
            result += WidgetNewsRow(
                title = item.title,
                subtitle = prefix + subtitleBody,
                url = item.url.ifBlank { data.briefingUrl },
                newsIndex = index
            )
        }

        val fallbackTitles = when {
            data.newsTitles.isNotEmpty() -> data.newsTitles
            data.top3.isNotEmpty() -> data.top3
            else -> emptyList()
        }

        fallbackTitles.forEachIndexed { index, title ->
            if (title.isBlank()) return@forEachIndexed
            if (result.any { it.title == title }) return@forEachIndexed
            val subtitle = when (index) {
                0 -> "오늘의 핵심 포인트"
                1 -> "시장과 산업 흐름에서 확인할 내용"
                else -> "자세한 내용은 전체 브리핑에서 확인"
            }
            result += WidgetNewsRow(title, subtitle, data.briefingUrl, null)
        }

        if (result.isEmpty()) {
            result += WidgetNewsRow(
                title = "브리핑 데이터를 불러오는 중",
                subtitle = "앱을 열어 지금 새로고침을 눌러주세요.",
                url = "",
                newsIndex = null
            )
        }

        // 스크롤 가능한 위젯이므로 더 많이 넣어도 된다. GitHub JSON에 뉴스가 늘어나면 자동 반영된다.
        rows = result.take(12)
    }
}
