
# Morning Briefing Android Widget

이 Android 프로젝트는 `latest.json`을 읽어서 홈 화면 위젯에 표시합니다.

## 1. GitHub Pages 주소 넣기

`app/src/main/res/values/strings.xml`에서 아래 값을 자신의 GitHub Pages 주소로 바꿉니다.

```xml
<string name="briefing_json_url">https://YOUR_GITHUB_ID.github.io/morning_routine_bot/latest.json</string>
```

## 2. Android Studio에서 열기

1. Android Studio 실행
2. `android/MorningBriefingWidget` 폴더 열기
3. Gradle Sync
4. 휴대폰 연결 후 Run

## 3. 위젯 추가

앱 설치 후 홈 화면을 길게 누르고, 위젯 목록에서 `Morning Briefing`을 추가합니다.

## 4. 업데이트 방식

- 앱 실행 시 즉시 새로고침을 한 번 요청합니다.
- 이후 WorkManager가 6시간마다 최신 JSON을 확인합니다.
- 실제 브리핑 생성은 GitHub Actions가 매일 오전 7시에 처리합니다.

Android 위젯의 백그라운드 업데이트는 시스템 정책의 영향을 받습니다. 정확히 매일 7시 정각에 위젯이 바뀌는 구조라기보다, 서버 쪽에서는 7시에 브리핑을 만들어두고 앱 위젯이 주기적으로 가져오는 방식입니다.
