package com.junoh.morningbriefing

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BriefingUpdateWorker.schedulePeriodic(this)

        val data = BriefingRepository.read(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }

        val title = TextView(this).apply {
            text = "🌅 Morning Briefing"
            textSize = 24f
            gravity = Gravity.START
        }
        val status = TextView(this).apply {
            text = buildString {
                append("위젯은 GitHub Pages의 latest.json을 읽어옵니다.

")
                append("현재 데이터: ${data.date}
")
                if (data.top3.isNotEmpty()) {
                    append(data.top3.take(3).joinToString("
") { "• $it" })
                }
            }
            textSize = 16f
            setPadding(0, 28, 0, 28)
        }
        val button = Button(this).apply {
            text = "지금 새로고침"
            setOnClickListener {
                BriefingUpdateWorker.refreshNow(this@MainActivity)
                status.text = "새로고침 작업을 시작했습니다. 잠시 뒤 위젯을 확인하세요."
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(button)
        setContentView(root)
    }
}
