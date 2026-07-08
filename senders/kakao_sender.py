from __future__ import annotations

import json
import logging
import os
import time
from pathlib import Path
from typing import Any

import requests

from utils import chunk_text

LOGGER = logging.getLogger(__name__)
TOKEN_URL = "https://kauth.kakao.com/oauth/token"
SEND_ME_URL = "https://kapi.kakao.com/v2/api/talk/memo/default/send"
DEFAULT_LINK = "https://developers.kakao.com"


class KakaoSender:
    def __init__(self, config: dict[str, Any]):
        self.rest_api_key = os.getenv("KAKAO_REST_API_KEY", "").strip()
        self.refresh_token = os.getenv("KAKAO_REFRESH_TOKEN", "").strip()
        self.client_secret = os.getenv("KAKAO_CLIENT_SECRET", "").strip()
        self.chunk_size = int(config.get("briefing", {}).get("kakao_chunk_size", 190))
        self.link_url = os.getenv("KAKAO_MESSAGE_LINK", DEFAULT_LINK).strip() or DEFAULT_LINK
        if not self.rest_api_key:
            raise RuntimeError("KAKAO_REST_API_KEY is missing.")
        if not self.refresh_token:
            raise RuntimeError("KAKAO_REFRESH_TOKEN is missing. Run scripts/kakao_get_tokens.py first.")

    def refresh_access_token(self) -> str:
        data = {
            "grant_type": "refresh_token",
            "client_id": self.rest_api_key,
            "refresh_token": self.refresh_token,
        }
        if self.client_secret:
            data["client_secret"] = self.client_secret

        resp = requests.post(TOKEN_URL, data=data, timeout=15)
        if resp.status_code >= 400:
            raise RuntimeError(f"Kakao token refresh failed: {resp.status_code} {resp.text}")
        payload = resp.json()
        access_token = payload.get("access_token")
        if not access_token:
            raise RuntimeError(f"Kakao token refresh response has no access_token: {payload}")

        # Kakao may return a renewed refresh token. Save locally for local runs.
        if payload.get("refresh_token"):
            Path("data").mkdir(exist_ok=True)
            Path("data/kakao_tokens_runtime.json").write_text(
                json.dumps({"refresh_token": payload["refresh_token"]}, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            LOGGER.warning(
                "Kakao returned a renewed refresh token. For GitHub Actions, update KAKAO_REFRESH_TOKEN secret manually."
            )
        return access_token

    def send_text(self, text: str, access_token: str | None = None) -> None:
        access_token = access_token or self.refresh_access_token()
        if len(text) > 200:
            raise ValueError("Kakao text template allows max 200 characters. Use send_long_text().")

        template_object = {
            "object_type": "text",
            "text": text,
            "link": {
                "web_url": self.link_url,
                "mobile_web_url": self.link_url,
            },
            "button_title": "열기",
        }
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/x-www-form-urlencoded;charset=utf-8",
        }
        data = {"template_object": json.dumps(template_object, ensure_ascii=False)}
        resp = requests.post(SEND_ME_URL, headers=headers, data=data, timeout=15)
        if resp.status_code >= 400:
            raise RuntimeError(f"Kakao send failed: {resp.status_code} {resp.text}")

    def send_long_text(self, text: str) -> int:
        access_token = self.refresh_access_token()
        chunks = chunk_text(text, max_chars=self.chunk_size)
        for idx, chunk in enumerate(chunks, start=1):
            numbered = f"[{idx}/{len(chunks)}]\n{chunk}"
            if len(numbered) > 200:
                numbered = numbered[:199]
            self.send_text(numbered, access_token=access_token)
            time.sleep(0.35)
        return len(chunks)
