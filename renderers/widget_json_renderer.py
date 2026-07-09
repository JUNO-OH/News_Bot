from __future__ import annotations

import json
import os
import re
from urllib.parse import urljoin

import requests
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from models import Article


def _extract_section(text: str, heading: str) -> str:
    pattern = rf"\[{re.escape(heading)}\](.*?)(?:\n\s*━━━━━━━━|\n\[[^\]]+\]|\Z)"
    match = re.search(pattern, text, flags=re.S)
    return match.group(1).strip() if match else ""


def _extract_numbered_lines(section: str, max_items: int) -> list[str]:
    lines: list[str] = []
    for raw in section.splitlines():
        line = raw.strip()
        if not line:
            continue
        line = re.sub(r"^[-•]\s*", "", line)
        line = re.sub(r"^\d+[.)]\s*", "", line)
        if line:
            lines.append(line)
        if len(lines) >= max_items:
            break
    return lines


def _shorten(text: str, limit: int) -> str:
    text = re.sub(r"\s+", " ", text or "").strip()
    if len(text) <= limit:
        return text
    return text[: limit - 1].rstrip() + "…"


def _guess_category(article: Article) -> str:
    blob = f"{article.title} {article.summary}".lower()
    if any(k in blob for k in ["ai", "semiconductor", "chip", "nvidia", "tsmc", "samsung", "sk hynix", "반도체", "엔비디아", "삼성전자", "하이닉스"]):
        return "산업"
    if any(k in blob for k in ["fed", "rate", "inflation", "bond", "interest", "연준", "금리", "물가", "환율"]):
        return "경제"
    if any(k in blob for k in ["oil", "energy", "crude", "유가", "원유", "에너지"]):
        return "에너지"
    if any(k in blob for k in ["china", "japan", "europe", "middle east", "중국", "일본", "유럽", "중동"]):
        return "국제"
    return article.category or "뉴스"


def _extract_og_image(url: str) -> str:
    """Try to find a representative thumbnail from article HTML.

    Many feeds, especially GDELT/Naver-style links, do not include image_url directly.
    This lightweight fallback checks common Open Graph/Twitter image tags.
    It is best-effort and silently returns an empty string on failures.
    """
    if not url or not url.startswith("http"):
        return ""
    try:
        resp = requests.get(
            url,
            timeout=4,
            headers={
                "User-Agent": "Mozilla/5.0 MorningBriefingBot/1.0",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            },
        )
        if resp.status_code >= 400:
            return ""
        html = resp.text[:300_000]
        patterns = [
            r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)["\']',
            r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image["\']',
            r'<meta[^>]+name=["\']twitter:image["\'][^>]+content=["\']([^"\']+)["\']',
            r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+name=["\']twitter:image["\']',
        ]
        for pattern in patterns:
            match = re.search(pattern, html, flags=re.I)
            if match:
                found = match.group(1).strip()
                if found:
                    return urljoin(url, found)
    except Exception:
        return ""
    return ""


def _article_payload(article: Article) -> dict[str, str]:
    image_url = article.image_url or _extract_og_image(article.url)
    return {
        "title": _shorten(article.title, 72),
        "summary": _shorten(article.summary, 96),
        "source": article.source or "뉴스",
        "published_at": article.published_at or "",
        "url": article.url,
        "image_url": image_url or "",
        "category": _guess_category(article),
    }


def extract_widget_payload(
    briefing_text: str,
    date_label: str,
    base_url: str = "",
    articles: list[Article] | None = None,
    market: list[dict[str, Any]] | None = None,
    weather: dict[str, Any] | None = None,
) -> dict[str, Any]:
    top3_section = _extract_section(briefing_text, "오늘의 핵심 3줄")
    top3 = _extract_numbered_lines(top3_section, 3)

    keyword_section = _extract_section(briefing_text, "오늘 기억할 키워드")
    raw_keywords = _extract_numbered_lines(keyword_section, 5)
    keywords = []
    for item in raw_keywords:
        keyword = re.split(r"\s[-–:]\s|:", item, maxsplit=1)[0].strip()
        if keyword:
            keywords.append(keyword[:16])

    titles = []
    for match in re.finditer(r"(?:^|\n)\s*━━━━━━━━[^\n]*\n\s*\d+[.)]\s+([^\n\[]+)", briefing_text):
        title = match.group(1).strip()
        if title and "..." not in title:
            titles.append(title)
        if len(titles) >= 12:
            break

    selected_articles = articles or []
    news = [_article_payload(a) for a in selected_articles[:12]]

    # 기사 배열이 3개 정도로 부족하면, Gemini가 만든 브리핑 제목/핵심 줄을 추가해서
    # Android 스크롤 위젯이 빈 공간으로 남지 않게 한다.
    existing_titles = {item.get("title", "").strip() for item in news}
    fallback_pool = list(titles) + list(top3)
    for title in fallback_pool:
        title = _shorten(title, 72)
        if not title or title in existing_titles:
            continue
        news.append({
            "title": title,
            "summary": "자세한 내용은 전체 브리핑에서 확인",
            "source": "브리핑",
            "published_at": "",
            "url": "",
            "image_url": "",
            "category": "핵심",
        })
        existing_titles.add(title)
        if len(news) >= 12:
            break

    if not news:
        news = [{"title": t, "summary": "", "source": "", "published_at": "", "url": "", "image_url": "", "category": "뉴스"} for t in (titles or top3)[:8]]

    base_url = (base_url or os.getenv("PAGES_BASE_URL", "") or os.getenv("GITHUB_PAGES_BASE_URL", "")).rstrip("/")
    briefing_url = f"{base_url}/latest.html" if base_url else ""

    return {
        "date": date_label,
        "title": "모닝 브리핑",
        "subtitle": "오늘의 핵심",
        "weather": weather or {},
        "market": market or [],
        "top3": top3,
        "keywords": keywords[:5],
        "news_titles": titles,
        "news": news,
        "briefing_url": briefing_url,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }


def write_widget_json(
    path: str | Path,
    briefing_text: str,
    date_label: str,
    base_url: str = "",
    articles: list[Article] | None = None,
    market: list[dict[str, Any]] | None = None,
    weather: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload = extract_widget_payload(
        briefing_text,
        date_label,
        base_url,
        articles=articles,
        market=market,
        weather=weather,
    )
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload
