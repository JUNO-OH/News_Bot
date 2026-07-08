from __future__ import annotations

from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from typing import Any


@dataclass
class Article:
    title: str
    url: str
    source: str
    published_at: str = ""
    summary: str = ""
    language: str = ""
    country: str = ""
    category: str = ""
    collected_from: str = ""
    score: float = 0.0

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()
