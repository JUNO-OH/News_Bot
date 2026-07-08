
from __future__ import annotations

import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path


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


def extract_widget_payload(briefing_text: str, date_label: str, base_url: str = "") -> dict:
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
        if len(titles) >= 5:
            break

    base_url = (base_url or os.getenv("GITHUB_PAGES_BASE_URL", "")).rstrip("/")
    briefing_url = f"{base_url}/latest.html" if base_url else ""

    return {
        "date": date_label,
        "title": "오늘의 모닝 브리핑",
        "top3": top3,
        "keywords": keywords[:5],
        "news_titles": titles,
        "briefing_url": briefing_url,
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }


def write_widget_json(path: str | Path, briefing_text: str, date_label: str, base_url: str = "") -> dict:
    payload = extract_widget_payload(briefing_text, date_label, base_url)
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return payload
