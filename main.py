from __future__ import annotations

import json
import logging
import os
from pathlib import Path

import yaml
from dotenv import load_dotenv

from collectors.gdelt_collector import fetch_gdelt_articles
from collectors.naver_collector import fetch_naver_articles
from collectors.rss_collector import fetch_rss_articles
from llm.gemini_client import GeminiClient
from pipeline.deduplicate import deduplicate_articles
from pipeline.prompt_builder import build_briefing_prompt
from pipeline.ranker import rank_articles
from senders.kakao_sender import KakaoSender
from renderers.html_renderer import write_html
from renderers.widget_json_renderer import write_widget_json
from utils import today_korean_date

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
LOGGER = logging.getLogger("morning_routine_bot")


def load_config(path: str = "config.yaml") -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def collect_articles(config: dict) -> list:
    articles = []
    articles.extend(fetch_gdelt_articles(config))
    articles.extend(fetch_rss_articles(config))
    articles.extend(fetch_naver_articles(config))
    return articles


def save_debug_payload(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    load_dotenv()
    config = load_config()
    output_dir = Path("out")
    output_dir.mkdir(exist_ok=True)

    LOGGER.info("Collecting articles...")
    raw_articles = collect_articles(config)
    LOGGER.info("Collected raw articles: %d", len(raw_articles))

    if not raw_articles:
        raise RuntimeError("No articles collected. Check network/API settings.")

    deduped = deduplicate_articles(raw_articles)
    LOGGER.info("After deduplication: %d", len(deduped))

    max_items = int(config.get("briefing", {}).get("max_news_items", 7))
    # Give Gemini a little more than final count so it can merge/choose.
    ranked = rank_articles(deduped, config, limit=max_items * 3)
    save_debug_payload(output_dir / "selected_articles.json", [a.as_dict() for a in ranked])

    LOGGER.info("Generating briefing with Gemini...")
    prompt = build_briefing_prompt(ranked, config)
    briefing = GeminiClient(config).generate(prompt)

    date_label = today_korean_date(config.get("schedule", {}).get("timezone", "Asia/Seoul"))
    briefing_path = output_dir / "briefing.md"
    briefing_path.write_text(briefing, encoding="utf-8")
    LOGGER.info("Briefing saved: %s", briefing_path)

    # Widget/web outputs. These files are what GitHub Pages and the Android widget use.
    docs_dir = Path(config.get("output", {}).get("docs_dir", "docs"))
    docs_dir.mkdir(exist_ok=True)
    title = config.get("briefing", {}).get("title", "오늘의 모닝 브리핑")
    base_url = config.get("output", {}).get("base_url", "") or os.getenv("PAGES_BASE_URL", "")
    write_html(docs_dir / "latest.html", briefing, date_label, title=title)
    widget_payload = write_widget_json(docs_dir / "latest.json", briefing, date_label, base_url=base_url)
    (docs_dir / "latest.md").write_text(briefing, encoding="utf-8")

    archive_dir = docs_dir / "archive"
    archive_dir.mkdir(exist_ok=True)
    safe_date = date_label.replace(" ", "_").replace(".", "-").replace("년", "").replace("월", "").replace("일", "")
    write_html(archive_dir / f"{safe_date}.html", briefing, date_label, title=title)
    LOGGER.info("Web outputs saved: %s", docs_dir)
    LOGGER.info("Widget top3: %s", widget_payload.get("top3"))

    if os.getenv("DRY_RUN", "false").lower() == "true":
        print("\n===== DRY RUN BRIEFING =====\n")
        print(briefing)
        print("\n===== WIDGET JSON =====\n")
        print(json.dumps(widget_payload, ensure_ascii=False, indent=2))
        return

    if os.getenv("SEND_KAKAO", "false").lower() == "true":
        LOGGER.info("Sending briefing to KakaoTalk Send to Me...")
        intro = f"🌅 {date_label} 모닝 브리핑을 보냅니다."
        full_message = f"{intro}\n\n{briefing}"
        sent_count = KakaoSender(config).send_long_text(full_message)
        LOGGER.info("Sent KakaoTalk chunks: %d", sent_count)
    else:
        LOGGER.info("SEND_KAKAO=false, skip sending.")


if __name__ == "__main__":
    main()
