package com.junoh.morningbriefing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.dp

class MorningBriefingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = BriefingRepository.read(context)
        val error = BriefingRepository.lastError(context)
        provideContent {
            val clickAction = if (data.briefingUrl.startsWith("http")) {
                actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.briefingUrl)))
            } else {
                actionStartActivity<MainActivity>()
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(R.color.widget_bg))
                    .padding(14.dp)
                    .clickable(clickAction)
            ) {
                Text(
                    text = "🌅 ${data.title}",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(R.color.widget_text)
                    )
                )
                Text(
                    text = data.date.ifBlank { "업데이트 대기 중" },
                    style = TextStyle(color = ColorProvider(R.color.widget_muted))
                )
                Spacer(modifier = GlanceModifier.height(8.dp))

                data.top3.take(3).forEachIndexed { index, line ->
                    Text(
                        text = "${index + 1}. $line",
                        style = TextStyle(color = ColorProvider(R.color.widget_text))
                    )
                }

                if (data.keywords.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = "키워드: ${data.keywords.take(5).joinToString(" · ")}",
                        style = TextStyle(color = ColorProvider(R.color.widget_muted))
                    )
                }

                if (error.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = "업데이트 오류: $error",
                        style = TextStyle(color = ColorProvider(R.color.widget_muted))
                    )
                }
            }
        }
    }
}

class MorningBriefingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MorningBriefingWidget()
}
