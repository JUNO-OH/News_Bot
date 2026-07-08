from __future__ import annotations

import logging
import os
from typing import Any

import requests

from models import Article
from utils import clean_text

LOGGER = logging.getLogger(__name__)
NAVER_NEWS_URL = "https://openapi.naver.com/v1/search/news.json"


def fetch_naver_articles(config: dict[str, Any]) -> list[Article]:
    naver_cfg = config.get("collectors", {}).get("naver", {})
    if not naver_cfg.get("enabled", True):
        return []

    client_id = os.getenv("NAVER_CLIENT_ID", "").strip()
    client_secret = os.getenv("NAVER_CLIENT_SECRET", "").strip()
    if not client_id or not client_secret:
        LOGGER.info("NAVER_CLIENT_ID/SECRET not set. Skipping Naver collector.")
        return []

    display = int(naver_cfg.get("display_per_query", 10))
    articles: list[Article] = []
    headers = {
        "X-Naver-Client-Id": client_id,
        "X-Naver-Client-Secret": client_secret,
    }

    for query in naver_cfg.get("queries", []):
        params = {"query": query, "display": display, "sort": "date"}
        try:
            resp = requests.get(NAVER_NEWS_URL, headers=headers, params=params, timeout=15)
            resp.raise_for_status()
            payload = resp.json()
        except Exception as exc:  # noqa: BLE001
            LOGGER.warning("Naver request failed for query=%s: %s", query, exc)
            continue

        for item in payload.get("items", []):
            title = clean_text(item.get("title"))
            url = item.get("originallink") or item.get("link") or ""
            if not title or not url:
                continue
            articles.append(
                Article(
                    title=title,
                    url=url,
                    source="Naver News",
                    published_at=item.get("pubDate") or "",
                    summary=clean_text(item.get("description")),
                    language="ko",
                    country="KR",
                    collected_from="naver",
                )
            )
    return articles
