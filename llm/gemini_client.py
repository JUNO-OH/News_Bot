from __future__ import annotations

import os
from typing import Any

from google import genai


class GeminiClient:
    def __init__(self, config: dict[str, Any]):
        api_key = os.getenv("GEMINI_API_KEY", "").strip()
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY is missing. Add it to .env or GitHub Secrets.")
        self.client = genai.Client(api_key=api_key)
        gemini_cfg = config.get("gemini", {})
        self.model = os.getenv("GEMINI_MODEL") or gemini_cfg.get("model", "gemini-3.5-flash")
        self.temperature = float(gemini_cfg.get("temperature", 0.2))
        self.max_output_tokens = int(gemini_cfg.get("max_output_tokens", 5000))

    def generate(self, prompt: str) -> str:
        response = self.client.models.generate_content(
            model=self.model,
            contents=prompt,
            config={
                "temperature": self.temperature,
                "max_output_tokens": self.max_output_tokens,
            },
        )
        text = getattr(response, "text", None)
        if not text:
            raise RuntimeError("Gemini returned an empty response.")
        return text.strip()
