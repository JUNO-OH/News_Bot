from __future__ import annotations

import re
from difflib import SequenceMatcher
from urllib.parse import urlparse

from models import Article


def _normalize_title(title: str) -> str:
    title = title.lower()
    title = re.sub(r"[^0-9a-z가-힣\s]", " ", title)
    title = re.sub(r"\s+", " ", title).strip()
    return title


def _domain(url: str) -> str:
    try:
        return urlparse(url).netloc.replace("www.", "")
    except Exception:  # noqa: BLE001
        return ""


def deduplicate_articles(articles: list[Article], similarity_threshold: float = 0.86) -> list[Article]:
    """Remove obvious duplicates while preserving source diversity.

    This is intentionally simple for MVP. Later, replace with embedding-based
    event clustering if you want stronger cross-language grouping.
    """
    unique: list[Article] = []
    seen_urls: set[str] = set()
    normalized_titles: list[str] = []

    for article in articles:
        if not article.url or article.url in seen_urls:
            continue
        seen_urls.add(article.url)

        normalized = _normalize_title(article.title)
        if not normalized:
            continue

        duplicate_idx: int | None = None
        for idx, existing_norm in enumerate(normalized_titles):
            if SequenceMatcher(None, normalized, existing_norm).ratio() >= similarity_threshold:
                duplicate_idx = idx
                break

        if duplicate_idx is None:
            normalized_titles.append(normalized)
            unique.append(article)
        else:
            # Keep the more informative summary and append source signal.
            existing = unique[duplicate_idx]
            if len(article.summary) > len(existing.summary):
                existing.summary = article.summary
            existing.source = _merge_sources(existing.source, article.source or _domain(article.url))
    return unique


def _merge_sources(a: str, b: str) -> str:
    parts = []
    for value in [a, b]:
        for piece in value.split(","):
            piece = piece.strip()
            if piece and piece not in parts:
                parts.append(piece)
    return ", ".join(parts[:4])
