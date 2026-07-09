package com.junoh.morningbriefing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.util.Locale

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

    private fun normalizedTitle(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .replace("…", "")
            .trim()
    }

    private fun MutableList<WidgetNewsRow>.addUnique(row: WidgetNewsRow, seen: MutableSet<String>) {
        val key = normalizedTitle(row.title)
        if (key.isBlank()) return
        if (seen.add(key)) add(row)
    }

    private fun loadRows() {
        val data = BriefingRepository.read(context)
        val result = mutableListOf<WidgetNewsRow>()
        val seen = mutableSetOf<String>()

        // 1) 실제 news 배열을 전부 우선 표시한다. 이미지가 있는 항목은 이미지 카드, 없는 항목은 텍스트 카드로 자동 표시된다.
        data.news.forEachIndexed { index, item ->
            if (item.title.isBlank()) return@forEachIndexed
            val parts = listOf(item.category, item.source)
                .filter { it.isNotBlank() && it != "뉴스" }
            val prefix = if (parts.isNotEmpty()) parts.joinToString(" · ") + " · " else ""
            val subtitleBody = item.summary.ifBlank { "자세한 내용은 전체 브리핑에서 확인" }
            result.addUnique(
                WidgetNewsRow(
                    title = item.title,
                    subtitle = prefix + subtitleBody,
                    url = item.url.ifBlank { data.briefingUrl },
                    newsIndex = index
                ),
                seen
            )
        }

        // 2) news 배열이 적은 날에는 Gemini 브리핑에서 뽑힌 제목과 핵심 3줄도 추가해 빈 공간을 줄인다.
        data.newsTitles.forEachIndexed { index, title ->
            result.addUnique(
                WidgetNewsRow(
                    title = title,
                    subtitle = when (index) {
                        0 -> "전체 브리핑에서 자세한 배경 확인"
                        else -> "중요 뉴스 · 자세한 내용은 전체 브리핑에서 확인"
                    },
                    url = data.briefingUrl,
                    newsIndex = null
                ),
                seen
            )
        }

        data.top3.forEachIndexed { index, title ->
            result.addUnique(
                WidgetNewsRow(
                    title = title,
                    subtitle = when (index) {
                        0 -> "오늘의 핵심 포인트"
                        1 -> "시장과 산업 흐름에서 확인할 내용"
                        else -> "경제 공부용 핵심 요약"
                    },
                    url = data.briefingUrl,
                    newsIndex = null
                ),
                seen
            )
        }

        if (result.isEmpty()) {
            result += WidgetNewsRow(
                title = "브리핑 데이터를 불러오는 중",
                subtitle = "앱을 열어 지금 새로고침을 눌러주세요.",
                url = "",
                newsIndex = null
            )
        }

        // collection widget이므로 충분히 많이 넣는다. 화면 크기에 따라 스크롤된다.
        rows = result.take(15)
    }
}
