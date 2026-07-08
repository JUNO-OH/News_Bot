from __future__ import annotations

import json
from pathlib import Path

import yaml

from models import Article
from utils import today_korean_date


def load_prompt_template(path: str | Path) -> str:
    return Path(path).read_text(encoding="utf-8")


def load_glossary(path: str | Path = "data/glossary.yaml") -> dict:
    glossary_path = Path(path)
    if not glossary_path.exists():
        return {}
    return yaml.safe_load(glossary_path.read_text(encoding="utf-8")) or {}


def build_briefing_prompt(articles: list[Article], config: dict) -> str:
    template = load_prompt_template("prompts/morning_briefing_prompt.txt")
    article_payload = [article.as_dict() for article in articles]
    glossary = load_glossary()
    return template.format(
        date=today_korean_date(config.get("schedule", {}).get("timezone", "Asia/Seoul")),
        max_items=config.get("briefing", {}).get("max_news_items", 7),
        articles=json.dumps(article_payload, ensure_ascii=False, indent=2),
        glossary=json.dumps(glossary, ensure_ascii=False, indent=2),
    )
