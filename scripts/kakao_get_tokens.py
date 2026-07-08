from __future__ import annotations

import os
import sys
import urllib.parse
import json
from pathlib import Path

import requests
from dotenv import load_dotenv

TOKEN_URL = "https://kauth.kakao.com/oauth/token"
AUTH_URL = "https://kauth.kakao.com/oauth/authorize"


def main() -> None:
    load_dotenv()
    rest_api_key = os.getenv("KAKAO_REST_API_KEY", "").strip()
    redirect_uri = os.getenv("KAKAO_REDIRECT_URI", "").strip() or "http://localhost:8080/oauth"
    client_secret = os.getenv("KAKAO_CLIENT_SECRET", "").strip()

    if not rest_api_key:
        print("KAKAO_REST_API_KEY가 .env에 없습니다.", file=sys.stderr)
        sys.exit(1)

    params = {
        "client_id": rest_api_key,
        "redirect_uri": redirect_uri,
        "response_type": "code",
        "scope": "talk_message",
        "prompt": "consent",
    }
    auth_url = f"{AUTH_URL}?{urllib.parse.urlencode(params)}"

    print("아래 URL을 브라우저에서 열고 카카오 로그인/동의를 완료하세요.\n")
    print(auth_url)
    print("\n리다이렉트된 주소의 code= 뒤 값을 복사해서 붙여넣으세요.")
    code = input("authorization code: ").strip()
    if not code:
        print("authorization code가 비어 있습니다.", file=sys.stderr)
        sys.exit(1)

    data = {
        "grant_type": "authorization_code",
        "client_id": rest_api_key,
        "redirect_uri": redirect_uri,
        "code": code,
    }
    if client_secret:
        data["client_secret"] = client_secret

    resp = requests.post(TOKEN_URL, data=data, timeout=15)
    if resp.status_code >= 400:
        print(f"토큰 발급 실패: {resp.status_code} {resp.text}", file=sys.stderr)
        sys.exit(1)

    payload = resp.json()
    refresh_token = payload.get("refresh_token")
    access_token = payload.get("access_token")

    Path("data").mkdir(exist_ok=True)
    Path("data/kakao_tokens_initial.json").write_text(
        json.dumps({"access_token": access_token, "refresh_token": refresh_token}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("\n토큰 발급 성공")
    print("아래 값을 .env 또는 GitHub Secrets에 저장하세요. 절대 공개 저장소에 올리면 안 됩니다.\n")
    print(f"KAKAO_REFRESH_TOKEN={refresh_token}")


if __name__ == "__main__":
    main()
