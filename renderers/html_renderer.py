
from __future__ import annotations

from datetime import datetime, timezone
from html import escape
from pathlib import Path


def render_briefing_html(briefing_text: str, date_label: str, title: str = "오늘의 모닝 브리핑") -> str:
    """Render the Gemini briefing into a mobile-friendly static HTML page.

    The briefing is already structured text. We intentionally avoid a heavy Markdown
    dependency and preserve line breaks for predictable mobile rendering.
    """
    safe_text = escape(briefing_text)
    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    return f"""<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta name="color-scheme" content="light dark" />
  <title>{escape(title)} - {escape(date_label)}</title>
  <style>
    :root {{
      --bg: #f6f7f9;
      --card: #ffffff;
      --text: #171717;
      --muted: #666;
      --line: #e5e7eb;
      --accent: #1f4fd7;
    }}
    @media (prefers-color-scheme: dark) {{
      :root {{
        --bg: #101114;
        --card: #181a20;
        --text: #f4f4f5;
        --muted: #a1a1aa;
        --line: #30333d;
        --accent: #8fb3ff;
      }}
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans KR", sans-serif;
      line-height: 1.65;
    }}
    .wrap {{ max-width: 760px; margin: 0 auto; padding: 18px 14px 36px; }}
    .hero {{
      background: linear-gradient(135deg, rgba(31,79,215,.13), rgba(31,79,215,.03));
      border: 1px solid var(--line);
      border-radius: 22px;
      padding: 18px 18px 14px;
      margin-bottom: 14px;
    }}
    h1 {{ margin: 0 0 6px; font-size: 1.35rem; letter-spacing: -0.02em; }}
    .date {{ color: var(--muted); font-size: .92rem; }}
    .card {{
      background: var(--card);
      border: 1px solid var(--line);
      border-radius: 22px;
      padding: 18px;
      box-shadow: 0 8px 24px rgba(0,0,0,.04);
    }}
    pre {{
      margin: 0;
      white-space: pre-wrap;
      word-break: keep-all;
      overflow-wrap: anywhere;
      font: inherit;
    }}
    .footer {{ color: var(--muted); font-size: .82rem; margin-top: 14px; text-align: center; }}
    a {{ color: var(--accent); }}
  </style>
</head>
<body>
  <main class="wrap">
    <section class="hero">
      <h1>🌅 {escape(title)}</h1>
      <div class="date">{escape(date_label)}</div>
    </section>
    <section class="card">
      <pre>{safe_text}</pre>
    </section>
    <div class="footer">Generated at {escape(generated_at)} UTC</div>
  </main>
</body>
</html>"""


def write_html(path: str | Path, briefing_text: str, date_label: str, title: str = "오늘의 모닝 브리핑") -> None:
    out = Path(path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render_briefing_html(briefing_text, date_label, title), encoding="utf-8")
