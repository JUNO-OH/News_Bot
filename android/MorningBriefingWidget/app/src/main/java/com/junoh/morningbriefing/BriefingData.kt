package com.junoh.morningbriefing

data class BriefingData(
    val date: String = "아직 업데이트 전",
    val title: String = "오늘의 모닝 브리핑",
    val top3: List<String> = listOf(
        "앱을 열어 새로고침하거나, GitHub Pages latest.json 주소를 설정하세요."
    ),
    val keywords: List<String> = emptyList(),
    val newsTitles: List<String> = emptyList(),
    val briefingUrl: String = "",
    val generatedAt: String = ""
)
