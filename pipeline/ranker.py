from __future__ import annotations

import re
from urllib.parse import urlparse

from models import Article

IMPORTANT_DOMAINS = {
    "reuters.com": 2.0,
    "apnews.com": 2.0,
    "bbc.co.uk": 1.6,
    "bbc.com": 1.6,
    "cnbc.com": 1.5,
    "ft.com": 1.7,
    "bloomberg.com": 1.7,
    "wsj.com": 1.6,
    "technologyreview.com": 1.4,
    "techcrunch.com": 1.2,
    "theverge.com": 1.0,
}

CATEGORY_HINTS = {
    "경제": ["economy", "inflation", "interest", "rate", "fed", "bond", "gdp", "물가", "금리", "환율", "연준", "국채"],
    "IT/AI": ["ai", "artificial intelligence", "semiconductor", "chip", "nvidia", "tsmc", "openai", "반도체", "엔비디아", "데이터센터", "hbm"],
    "국제정세": ["china", "tariff", "war", "middle east", "oil", "geopolitic", "중국", "관세", "전쟁", "중동", "유가"],
    "한국경제": ["korea", "samsung", "sk hynix", "export", "한국", "삼성전자", "sk하이닉스", "수출"],
}


def rank_articles(articles: list[Article], config: dict, limit: int | None = None) -> list[Article]:
    ranking_cfg = config.get("ranking", {})
    keywords = ranking_cfg.get("keywords", {})
    high = keywords.get("high", [])
    medium = keywords.get("medium", [])
    low = keywords.get("low", [])

    for article in articles:
        text = f"{article.title} {article.summary}".lower()
        score = 0.0
        score += _keyword_score(text, high, 4.0)
        score += _keyword_score(text, medium, 2.0)
        score -= _keyword_score(text, low, 2.5)
        score += _domain_score(article.url, article.source)
        score += _source_diversity_score(article.source)
        score += 1.0 if article.collected_from == "gdelt" else 0.0
        article.score = score
        article.category = _infer_category(text)

    ranked = sorted(articles, key=lambda item: item.score, reverse=True)
    if limit:
        return ranked[:limit]
    return ranked


def _keyword_score(text: str, keywords: list[str], weight: float) -> float:
    score = 0.0
    for keyword in keywords:
        if re.search(re.escape(str(keyword).lower()), text):
            score += weight
    return score


def _domain_score(url: str, source: str) -> float:
    candidates = [source.lower()]
    try:
        candidates.append(urlparse(url).netloc.lower().replace("www.", ""))
    except Exception:  # noqa: BLE001
        pass
    joined = " ".join(candidates)
    return sum(weight for domain, weight in IMPORTANT_DOMAINS.items() if domain in joined)


def _source_diversity_score(source: str) -> float:
    return min(len([x for x in source.split(",") if x.strip()]) * 0.5, 2.0)


def _infer_category(text: str) -> str:
    best_category = "경제"
    best_hits = -1
    for category, hints in CATEGORY_HINTS.items():
        hits = sum(1 for hint in hints if hint.lower() in text)
        if hits > best_hits:
            best_hits = hits
            best_category = category
    return best_category
