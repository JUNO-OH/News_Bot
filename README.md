# Morning Routine Bot

한경 코리아마켓 모닝루틴 같은 느낌으로, 매일 아침 세계 경제·IT·산업 뉴스를 수집해서 Gemini API로 한국어 브리핑을 만들고 카카오톡 `나에게 보내기`로 전송하는 MVP입니다.

## 1. 현재 MVP 기능

- GDELT로 해외 뉴스 수집
- RSS로 해외 경제/IT 뉴스 보강
- 네이버 뉴스 검색 API로 한국어 뉴스 보강, API 키가 없으면 자동 건너뜀
- 단순 중복 제거 및 중요도 랭킹
- Gemini API로 경제 공부용 브리핑 생성
- 어려운 용어/개념 설명 포함
- 카카오톡 나에게 보내기 전송
- GitHub Actions 매일 오전 7시 실행

## 2. 설치

```bash
python -m venv .venv
.venv\Scripts\activate  # Windows
# source .venv/bin/activate  # macOS/Linux
pip install -r requirements.txt
copy .env.example .env
```

`.env`에 최소한 아래 값을 채우세요.

```env
GEMINI_API_KEY=...
GEMINI_MODEL=gemini-3.5-flash
KAKAO_REST_API_KEY=...
KAKAO_REDIRECT_URI=http://localhost:8080/oauth
KAKAO_REFRESH_TOKEN=...
```

## 3. 카카오톡 토큰 받기

1. Kakao Developers에서 앱 생성
2. 플랫폼 Web 등록
3. Redirect URI 등록: `http://localhost:8080/oauth`
4. 카카오 로그인 활성화
5. 동의 항목에서 `talk_message` 권한 설정
6. `.env`에 `KAKAO_REST_API_KEY`, `KAKAO_REDIRECT_URI` 입력
7. 아래 실행

```bash
python scripts/kakao_get_tokens.py
```

브라우저에서 로그인 후 리다이렉트된 주소의 `code=` 값을 복사해 붙여넣으면 `KAKAO_REFRESH_TOKEN`이 출력됩니다. 이 값을 `.env` 또는 GitHub Secrets에 넣으세요.

## 4. 로컬 테스트

카카오톡 전송 없이 브리핑 생성만 확인하려면:

```bash
DRY_RUN=true SEND_KAKAO=false python main.py
```

Windows PowerShell에서는:

```powershell
$env:DRY_RUN="true"
$env:SEND_KAKAO="false"
python main.py
```

카카오톡까지 보내려면:

```bash
DRY_RUN=false SEND_KAKAO=true python main.py
```

## 5. GitHub Actions 자동 실행

Repository Settings → Secrets and variables → Actions에 아래 Secrets를 추가하세요.

- `GEMINI_API_KEY`
- `GEMINI_MODEL` 예: `gemini-3.5-flash`
- `KAKAO_REST_API_KEY`
- `KAKAO_REFRESH_TOKEN`
- 선택: `NAVER_CLIENT_ID`
- 선택: `NAVER_CLIENT_SECRET`

`.github/workflows/morning_briefing.yml`은 매일 `Asia/Seoul` 오전 7시에 실행되도록 되어 있습니다.

## 6. 중요한 제약

카카오톡 기본 Text template의 `text` 필드는 최대 200자입니다. 그래서 이 MVP는 브리핑을 여러 조각으로 나눠서 보냅니다. 나중에 GitHub Pages나 Notion/블로그 링크를 만들면 카톡에는 핵심 3줄과 링크만 보내는 구조로 개선할 수 있습니다.

## 7. 다음 개선 방향

- Gemini에게 기사 URL 직접 읽기 기능을 쓰기보다, 안정성을 위해 기사 본문 추출기를 별도 추가
- 같은 사건을 여러 출처로 묶는 embedding 기반 클러스터링 추가
- GitHub Pages에 전체 브리핑 HTML 저장 후 카톡에는 요약과 링크만 전송
- 경제지표 캘린더, 주요 실적 발표 일정, 한국 시장 개장 전 체크포인트 추가


## Android 위젯 버전

카카오톡 전송 대신 Android 홈 화면 위젯으로 볼 수 있도록 다음 파일을 생성합니다.

- `docs/latest.html`: 전체 브리핑 웹 페이지
- `docs/latest.json`: Android 위젯용 핵심 3줄/키워드 데이터
- `docs/latest.md`: 원문 브리핑 텍스트

GitHub Actions가 매일 아침 브리핑을 생성한 뒤 GitHub Pages로 배포합니다. Android 앱은 `latest.json`을 읽어서 홈 화면 위젯에 표시합니다.

Android 프로젝트 위치:

```text
android/MorningBriefingWidget
```

`android/MorningBriefingWidget/app/src/main/res/values/strings.xml`의 `briefing_json_url`을 자신의 GitHub Pages 주소로 바꾸면 됩니다.

```xml
<string name="briefing_json_url">https://YOUR_GITHUB_ID.github.io/morning_routine_bot/latest.json</string>
```

GitHub 저장소 설정에서 Pages source를 GitHub Actions로 바꾸고, repository variable `GITHUB_PAGES_BASE_URL`에 아래처럼 넣으면 `latest.json` 안의 `briefing_url`도 자동 설정됩니다.

```text
https://YOUR_GITHUB_ID.github.io/morning_routine_bot
```
