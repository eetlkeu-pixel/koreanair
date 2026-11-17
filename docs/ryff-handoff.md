# 작업 요청서: Ryff 48문항 심리적 안녕감 설문, 백엔드 배포와 실환경 검증 마무리

## 1. 목표

운영자(저장소 소유자) Google 계정에 Ryff 심리적 안녕감 48문항 설문을 배포하고, 실제 환경에서 끝까지 검증한다.

- 한국어 48문항, 6개 영역 × 8문항, 5점 척도. 역채점 문항은 `6 - 응답값`.
- 참가자는 Google 로그인 없이 응답한다. 제출 직후 총점, 영역별 점수와 그래프, 영역별 해석을 본다.
- 응답은 무작위 응답 코드로 관리하고, 운영자만 열 수 있는 Google Sheets에 저장한다.
- 안드로이드 일반 탭에서 링크를 누르면 바로 열려야 한다. 예전 Apps Script 직접 링크는 주소에 `/u/1`이 붙고 Drive 파일 열기 오류가 났다. 그래서 참가 화면은 GitHub Pages에 따로 두고, 채점과 저장만 Apps Script에서 한다.
- 배포한 최종 주소를 비로그인 세션에서 열어 가상 응답 1건으로 제출 → 결과 표시 → 시트 저장을 확인한다. 확인 후 가상 응답을 지운다. 마지막에 참가자용 URL, 관리자용 시트 URL, 실제로 검증한 환경을 보고한다.

## 2. 현재 상태 요약

| 항목 | 상태 |
|---|---|
| 참가 화면 (GitHub Pages) | **배포됨**: https://eetlkeu-pixel.github.io/koreanair/ryff/ (HTTP 200 확인). 백엔드 주소가 아직 없어 시작 버튼을 누르면 "서버 주소 미설정" 안내가 뜬다 |
| 응답 시트 | **생성됨** (Drive MCP로 생성, 공유 없음): https://docs.google.com/spreadsheets/d/160pAR0LIP-IlQoPRvGMVMSALZvHu_gTgycIOa8gR1I4/edit . 현재 빈 시트(`시트1`) |
| Apps Script 백엔드 | **코드 완료, 미배포**. `apps-script/Code.gs`에 시트 ID가 이미 들어 있음 |
| 로컬 검증 | `node tests/check.mjs --browser` 통과 (가짜 SpreadsheetApp + Playwright Pixel 7 에뮬레이션) |
| 실환경 검증 | **미실시** |
| 임시 파일 | 저장소 루트 `go.html`: OAuth 승인 화면으로 넘기는 임시 이동 페이지. **작업이 끝나면 삭제** |

## 3. 저장소 구조 (`eetlkeu-pixel/koreanair`)

- `ryff/items.js`: 문항, 영역, 역채점 여부의 **단일 기준**. 영역 순서 AU→EM→PG→PR→PL→SA가 반복되고, 각 영역은 정채점 4 · 역채점 4.
- `ryff/interpret.js`: 영역별·수준별 해석 문구. 수준은 문항 평균 기준으로 2.5 이하 낮음, 3.5 이하 보통, 그 위 높음.
- `ryff/config.js`: `window.RYFF_API`. 현재 값 `'__APPS_SCRIPT_EXEC_URL__'`. **배포 후 `/exec` URL로 교체한다.**
- `ryff/index.html`, `ryff/app.js`: 참가 화면. 한 페이지에 6문항씩 8페이지. 제출은 `fetch(POST, Content-Type: text/plain, credentials: 'omit')`로 보내 CORS 사전 요청을 피하고, 다중 로그인 쿠키도 보내지 않는다. 주소에 `?test=1`을 붙이면 시트 '구분' 열에 '테스트'로 저장된다.
- `apps-script/Code.gs`: `doPost`가 채점, 응답 코드(`R-XXXXXX`) 발급, `appendRow`를 처리한다. `{action:'withdraw', code, withdrawKey}`로 해당 행을 삭제한다(철회 키는 SHA-256 해시로만 저장). 편집기에서 `setup()`을 실행하면 머리글이 생긴다. `KEY` 표는 `items.js`와 일치해야 한다(테스트가 검사).
- `apps-script/appsscript.json`: V8, `spreadsheets` 스코프만 사용, webapp `USER_DEPLOYING` / `ANYONE_ANONYMOUS`.
- `apps-script/README.md`: 사람이 편집기에 붙여넣어 배포하는 절차.
- `tests/check.mjs`: 문항 구성, KEY 일치, 무작위 200건 채점 비교, 경계값(240/48/144), 잘못된 입력 거부, 저장·철회, 브라우저 흐름을 검사한다.
- `.github/workflows/pages.yml`: **`main` push 때만** Pages에 배포한다. 평일 종가 자동 갱신 커밋이 `main`에 계속 올라온다.

## 4. 남은 작업 (순서대로)

### 4-1. Apps Script 배포 (둘 중 하나)

**A. 운영자가 편집기에서 직접 배포.** 가장 확실한 방법이다.
`apps-script/README.md` 절차대로 한다: script.new → `Code.gs` 붙여넣기 → `setup` 실행과 권한 승인 → 배포(웹 앱, 실행 사용자 나, 액세스 모든 사용자) → `/exec` URL 전달.

**B. clasp로 에이전트가 배포.** 이번 세션에서 진행하던 방식이다.
- 운영자가 https://script.google.com/home/usersettings 에서 Apps Script API를 **사용**으로 바꿨다고 했다. 다만 확인은 못 했다.
- clasp 3.x 기본 OAuth 클라이언트 ID: `1072944905499-vm2v2i5dvn0a0d2o4ca36i1vge8cvbn0.apps.googleusercontent.com`. 시크릿은 공개값이며 `node_modules/@google/clasp/build/src/auth/oauth_client.js`에 있다. redirect URI는 `http://localhost:8888`만 허용된다.
- 이번 세션의 PKCE verifier는 이 세션 스크래치패드에만 있으므로 **새 세션에서는 쓸 수 없다.** 새로 PKCE를 만들고 `go.html`의 대상 URL을 새 `state` / `code_challenge`로 바꿔 `main`에 push한다. 그다음 운영자에게 **짧은 링크** https://eetlkeu-pixel.github.io/koreanair/go.html 를 준다. 휴대폰에서 긴 링크를 누르면 잘려서 `response_type` 누락 400 오류가 났다.
- 필요한 스코프: `script.deployments script.projects script.webapp.deploy drive.file userinfo.email`. `access_type=offline&prompt=consent`.
- 운영자가 승인하면 `localhost:8888` 연결 실패 화면이 뜬다. 그 주소창의 **전체 주소를 글자로** 받는다. 사진으로는 `code`가 보이지 않는다. `code`를 꺼내 `https://oauth2.googleapis.com/token`에 `grant_type=authorization_code`, `code_verifier`와 함께 교환한다.
- 받은 토큰을 `~/.clasprc.json`에 `{"tokens":{"default":{"client_id":…,"client_secret":…,"type":"authorized_user","refresh_token":…,"access_token":…,"expiry_date":…}}}` 형식으로 저장한다. 그 뒤 `clasp create --type standalone --title "Ryff 48문항 설문 백엔드"`, `apps-script/` 파일 push, `clasp deploy`를 실행한다.
- **API로 배포한 뒤에도** 운영자가 운영자 계정으로 `/exec` 주소를 한 번 열어 스크립트 권한(spreadsheets)을 승인해야 익명 요청이 동작할 가능성이 높다. 승인 전에 익명 POST가 로그인/권한 페이지를 돌려주는지 확인하고, 그렇다면 운영자에게 그 주소를 열어 달라고 요청한다.
- `clasp login --no-localhost`를 백그라운드로 띄워 두는 방식은 2시간 한도에 걸려 끊겼다. 다시 쓰지 말 것.

### 4-2. 참가 화면 연결

`ryff/config.js`의 `RYFF_API`를 `https://script.google.com/macros/s/<ID>/exec`로 바꾼다. 다른 형식이면 `app.js`의 `apiReady()` 정규식에 걸려 시작이 막힌다. 그 뒤 `node tests/check.mjs --browser`를 실행하고 `main`에 push한다.

### 4-3. 실환경 검증

1. 컨테이너 Playwright로 **새 컨텍스트(쿠키 없음 = 비로그인)**를 만들고 `…/ryff/?test=1`을 연다. 48문항에 응답해 제출하고 결과 화면(총점, 6개 막대, 6개 해석, 응답 코드)을 확인한다. 기기 에뮬레이션은 `devices['Pixel 7']`.
2. Drive MCP `read_file_content`로 시트를 읽어 그 응답 코드의 행이 생겼는지, '구분'이 '테스트'인지, 총점이 맞는지 확인한다.
3. 다중 로그인 환경: 컨테이너에는 운영자 Google 세션이 없다. 쿠키가 없다는 점(`credentials: 'omit'`)과 응답이 `/u/N` 경로로 가지 않는다는 점까지만 확인할 수 있다. **실제 안드로이드 일반 탭과 실제 다중 로그인은 운영자 휴대폰에서 확인을 부탁해야 한다.** 보고할 때 직접 검증한 것과 에뮬레이션한 것을 구분한다.
4. 정리: 제출 응답으로 받은 `withdrawKey`로 `{action:'withdraw', code, withdrawKey}`를 POST해 행을 지운다. Drive MCP로 시트를 다시 읽어 행이 없어졌는지 확인한다.

### 4-4. 마무리

- `go.html`을 삭제하고 `main`에 push한다.
- 운영자에게 https://myaccount.google.com/permissions 에서 clasp 권한을 해제할 수 있다고 안내한다(B 방식을 쓴 경우).
- 보고에 넣을 것: 참가자용 URL `https://eetlkeu-pixel.github.io/koreanair/ryff/`, 관리자 시트 URL(위 표), 실제로 검증한 환경 목록, 가상 응답 정리 결과.

## 5. 문항 관련 사실 (보고할 때 그대로 밝힐 것)

- 처음 요청은 "첨부한 자료와 기존 코드, 원본 사진"을 참고하라고 했지만 **첨부가 비어 있었다.** Drive를 "Ryff", "심리적 안녕감"으로 검색해도 관련 파일이 없었다. 그래서 운영자 지시("뭐로든 하고 새로 짜서 배포해")에 따라 문항을 새로 작성했다.
- 문항은 Ryff 원 척도 문항과 영역 정의를 바탕으로 번역·각색한 것이다. **타당도가 검증된 한국어판이 아니다.** 해석 구간(2.5 / 3.5)도 규준이 아닌 임의 기준이다.
- 원본 사진 대조와 "기존 코드가 11번을 문장 의미로 역채점한 근거" 확인은 자료가 없어 **하지 못했다.** 새 문항의 11번은 "나는 미래를 깊이 생각하지 않고 그날그날 살아간다"(삶의 목적, 역채점)이고, `items.js`에 명시적으로 표시돼 있다.
- 운영자가 원본 문항표나 사진을 나중에 주면, `items.js`와 `Code.gs`의 `KEY`를 함께 바꾸고 테스트를 다시 돌린다.

## 6. 환경과 제약

- 세션 네트워크 환경 `koreanair-android`(개인)에 허용된 호스트: `script.google.com`, `script.googleusercontent.com`, `docs.google.com`, `sheets.googleapis.com`, `accounts.google.com`, `oauth2.googleapis.com`, `www.googleapis.com`, `script.googleapis.com`, `eetlkeu-pixel.github.io`. 새 세션은 이 환경으로 시작할 것.
- 작업 브랜치: `claude/ryff-survey-build-deploy-79jflb`. Pages 배포를 위해 운영자 허락을 받고 `main`에도 push해 왔다. `main`에는 자동 종가 커밋이 계속 올라오므로 **push 전에 `git fetch origin main && git merge origin/main`을 하고, force push나 rebase 후 강제 push는 하지 말 것**(권한 분류기가 거부함).
- Drive MCP로는 코드가 든 Apps Script 프로젝트를 만들 수 없다(`application/vnd.google-apps.script+json` 업로드 거부). 시트 읽기와 생성은 된다.
- gcloud에는 인증된 계정이 없다.
- 운영자는 모바일(Brave, 안드로이드)로 소통한다. 요청은 **누르면 되는 짧은 링크 하나**와 **최소 단계**로 하고, 긴 설명은 피할 것. 진행이 오래 걸려 불만이 큰 상태다.
- 커밋 메시지, 코드, 문서에 모델명을 넣지 말 것.
