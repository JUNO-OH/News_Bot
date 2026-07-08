from __future__ import annotations

import logging
from typing import Any

import feedparser

from models import Article
from utils import clean_text

LOGGER = logging.getLogger(__name__)


def fetch_rss_articles(config: dict[str, Any]) -> list[Article]:
    rss_cfg = config.get("collectors", {}).get("rss", {})
    if not rss_cfg.get("enabled", True):
        return []

    max_entries = int(rss_cfg.get("max_entries_per_feed", 8))
    articles: list[Article] = []

    for feed in rss_cfg.get("feeds", []):
        name = feed.get("name", "RSS")
        url = feed.get("url")
        if not url:
            continue
        try:
            parsed = feedparser.parse(url)
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("RSS parse failed for %s: %s", name, exc)
            continue

        for entry in parsed.entries[:max_entries]:
            title = clean_text(getattr(entry, "title", ""))
            link = getattr(entry, "link", "")
            if not title or not link:
                continue
            summary = clean_text(getattr(entry, "summary", ""))
            published = getattr(entry, "published", "") or getattr(entry, "updated", "")
            articles.append(
                Article(
                    title=title,
                    url=link,
                    source=name,
                    published_at=published,
                    summary=summary,
                    collected_from="rss",
                )
            )
    return articles
