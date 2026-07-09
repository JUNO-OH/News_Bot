package com.junoh.morningbriefing

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var summary: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        BriefingUpdateWorker.schedulePeriodic(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 60, 42, 42)
            setBackgroundColor(Color.rgb(9, 15, 28))
        }

        val title = TextView(this).apply {
            text = "🌅 Morning Briefing"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.START
        }

        summary = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(215, 224, 238))
            setPadding(0, 30, 0, 30)
        }

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(154, 164, 178))
            setPadding(0, 10, 0, 24)
        }

        val refreshButton = Button(this).apply {
            text = "지금 새로고침"
            setOnClickListener { refreshImmediately() }
        }

        val openButton = Button(this).apply {
            text = "전체 브리핑 열기"
            setOnClickListener {
                val data = BriefingRepository.read(this@MainActivity)
                val url = data.briefingUrl.ifBlank { getString(R.string.briefing_page_url) }
                if (url.startsWith("http")) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        root.addView(title)
        root.addView(summary)
        root.addView(status)
        root.addView(refreshButton)
        root.addView(openButton)
        setContentView(root)

        renderCurrentData()
    }

    private fun renderCurrentData() {
        val data = BriefingRepository.read(this)
        val error = BriefingRepository.lastError(this)

        summary.text = buildString {
            append(data.date.ifBlank { "아직 저장된 브리핑이 없습니다." })
            append("\n\n")
            append("날씨: ${data.weather.location} ${data.weather.temperature}° ${data.weather.condition}")
            append(" / 최고 ${data.weather.high}° · 최저 ${data.weather.low}°")
            append("\n\n")

            if (data.market.isNotEmpty()) {
                append("시장: ")
                append(data.market.take(5).joinToString(" · ") { "${it.label} ${it.value} ${it.changePercent}" })
                append("\n\n")
            } else {
                append("시장 데이터가 아직 없습니다. GitHub Actions가 새 latest.json을 만들었는지 확인하세요.\n\n")
            }

            when {
                data.news.isNotEmpty() -> data.news.take(3).forEach { append("• ${it.title}\n") }
                data.top3.isNotEmpty() -> data.top3.take(3).forEach { append("• $it\n") }
                else -> append("아직 표시할 브리핑 데이터가 없습니다.\n아래의 지금 새로고침을 눌러주세요.")
            }
        }

        status.text = if (error.isNotBlank()) {
            "최근 오류: $error"
        } else {
            "브리핑 주소: ${getString(R.string.briefing_json_url)}"
        }
    }

    private fun refreshImmediately() {
        status.text = "브리핑 데이터를 불러오는 중입니다..."

        thread {
            try {
                val json = BriefingRepository.fetchLatestJson(this)
                val data = BriefingRepository.parse(json)
                BriefingRepository.save(this, json)
                BriefingRepository.downloadNewsImages(this, data)
                MorningBriefingWidgetReceiver.updateAll(this)

                runOnUiThread {
                    renderCurrentData()
                    Toast.makeText(this, "브리핑을 업데이트했습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                BriefingRepository.saveError(this, e.message ?: e.javaClass.simpleName)
                MorningBriefingWidgetReceiver.updateAll(this)

                runOnUiThread {
                    renderCurrentData()
                    Toast.makeText(this, "업데이트 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
