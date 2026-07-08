from __future__ import annotations

import logging
from typing import Any
from urllib.parse import urlencode

import requests

from models import Article
from utils import clean_text

LOGGER = logging.getLogger(__name__)
GDELT_DOC_URL = "https://api.gdeltproject.org/api/v2/doc/doc"


def fetch_gdelt_articles(config: dict[str, Any]) -> list[Article]:
    gdelt_cfg = config.get("collectors", {}).get("gdelt", {})
    if not gdelt_cfg.get("enabled", True):
        return []

    timespan = gdelt_cfg.get("timespan", "24h")
    maxrecords = int(gdelt_cfg.get("maxrecords_per_query", 10))
    queries = gdelt_cfg.get("queries", [])
    articles: list[Article] = []

    for query in queries:
        params = {
            "query": query,
            "mode": "artlist",
            "format": "json",
            "maxrecords": maxrecords,
            "sort": "hybridrel",
            "timespan": timespan,
        }
        url = f"{GDELT_DOC_URL}?{urlencode(params)}"
        try:
            resp = requests.get(url, timeout=20)
            resp.raise_for_status()
            payload = resp.json()
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("GDELT request failed for query=%s: %s", query, exc)
            continue

        for item in payload.get("articles", []):
            title = clean_text(item.get("title"))
            article_url = item.get("url") or ""
            if not title or not article_url:
                continue
            articles.append(
                Article(
                    title=title,
                    url=article_url,
                    source=item.get("domain") or item.get("source") or "GDELT",
                    published_at=item.get("seendate") or "",
                    summary=clean_text(item.get("snippet") or item.get("description")),
                    language=item.get("language") or "",
                    country=item.get("sourcecountry") or "",
                    collected_from="gdelt",
                )
            )
    return articles
