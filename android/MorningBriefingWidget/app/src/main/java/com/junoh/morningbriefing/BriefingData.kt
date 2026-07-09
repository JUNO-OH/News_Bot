package com.junoh.morningbriefing

data class WeatherData(
    val location: String = "서울",
    val temperature: String = "-",
    val condition: String = "날씨 대기",
    val high: String = "-",
    val low: String = "-"
)

data class MarketItem(
    val label: String = "",
    val subLabel: String = "",
    val value: String = "-",
    val change: String = "-",
    val changePercent: String = "-",
    val direction: String = "flat"
)

data class NewsItem(
    val title: String = "",
    val summary: String = "",
    val source: String = "",
    val category: String = "뉴스",
    val url: String = "",
    val imageUrl: String = ""
)

data class BriefingData(
    val date: String = "아직 업데이트 전",
    val title: String = "모닝 브리핑",
    val subtitle: String = "오늘의 핵심",
    val weather: WeatherData = WeatherData(),
    val market: List<MarketItem> = emptyList(),
    val top3: List<String> = listOf(
        "앱을 열어 새로고침하거나, GitHub Pages latest.json 주소를 설정하세요."
    ),
    val keywords: List<String> = emptyList(),
    val newsTitles: List<String> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val briefingUrl: String = "",
    val generatedAt: String = ""
)
