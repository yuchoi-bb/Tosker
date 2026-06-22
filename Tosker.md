# Tosker

google tasker와 연동하여, 간단하게 할일을 입력하는 android app이다.
https://github.com/yuchoi-bb/Tosker
위 repo에 관련된 코드를 업로드하고
빌드후 release에 반영한다.

---

## 앱 기능 설명

tosker app은 구글 계정과 연동된다.
구글 tasks에 작성된 목록중 한가지를 선택하고
해야할일을 작성한다.
날짜는 기본 : 오늘날짜로 작성되어있다. 우측에는 달력표시가 있으며, 선택할수있다.
아래 upload버튼이나 엔터를 입력하면 upload된다.

---

## 로그인 과정

### 1. 앱 최초 실행
- 앱을 실행하면 로그인 화면이 표시된다.
- **"Google로 로그인"** 버튼이 화면 중앙에 표시된다.

### 2. Google 계정 선택
- 버튼을 탭하면 Google 계정 선택 다이얼로그가 나타난다.
- 기기에 등록된 Google 계정 목록이 표시된다.
- 사용할 계정을 선택한다.

### 3. 권한 승인
- Google Tasks 접근 권한 요청 화면이 표시된다.
- 요청 권한 항목:
  - Google Tasks 목록 읽기
  - Google Tasks에 할일 추가
- **"허용"** 버튼을 탭하여 권한을 승인한다.

### 4. 로그인 완료 및 메인 화면 진입
- 인증이 완료되면 자동으로 메인 화면으로 이동한다.
- 이후 앱 재실행 시에는 로그인 상태가 유지되어 자동으로 메인 화면으로 진입한다.

### 5. 로그아웃
- 설정 또는 메뉴에서 로그아웃이 가능하다.
- 로그아웃 시 로그인 화면으로 돌아간다.

### 인증 방식
- Google OAuth 2.0 기반 인증 사용
- `google-services.json` 파일에 Firebase / OAuth 클라이언트 정보 포함
- Android Credential Manager 또는 Google Sign-In SDK 활용
