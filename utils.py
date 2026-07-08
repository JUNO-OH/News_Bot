from __future__ import annotations

import html
import re
from datetime import datetime
from zoneinfo import ZoneInfo
from typing import Iterable


def clean_text(text: str | None) -> str:
    if not text:
        return ""
    text = html.unescape(text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def today_korean_date(tz: str = "Asia/Seoul") -> str:
    now = datetime.now(ZoneInfo(tz))
    weekdays = ["월", "화", "수", "목", "금", "토", "일"]
    return f"{now.year}년 {now.month}월 {now.day}일 {weekdays[now.weekday()]}요일"


def safe_join(items: Iterable[str], sep: str = "\n") -> str:
    return sep.join([x for x in items if x])


def chunk_text(text: str, max_chars: int = 190) -> list[str]:
    """Split Kakao text template content into <= max_chars chunks.

    Kakao default text template's `text` field is limited to 200 characters,
    so keep a safety margin.
    """
    text = text.strip()
    if not text:
        return []

    chunks: list[str] = []
    current = ""
    for line in text.splitlines():
        line = line.rstrip()
        if not line:
            candidate = current + "\n" if current else ""
        else:
            candidate = f"{current}\n{line}" if current else line

        if len(candidate) <= max_chars:
            current = candidate
            continue

        if current:
            chunks.append(current.strip())
            current = ""

        while len(line) > max_chars:
            chunks.append(line[:max_chars].strip())
            line = line[max_chars:]
        current = line

    if current:
        chunks.append(current.strip())
    return chunks
