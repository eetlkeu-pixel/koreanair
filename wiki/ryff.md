# Ryff 48문항 설문

참가 주소: https://eetlkeu-pixel.github.io/koreanair/ryff/

| 파일 | 역할 |
|---|---|
| `ryff/items.js` | 48문항, 6개 차원 배정, 역채점 표시 |
| `ryff/interpret.js` | 점수 해석 문구 |
| `ryff/app.js` | 화면 진행(한 페이지 6문항), sessionStorage 임시 저장, `?test=1` 테스트 모드 |
| `ryff/config.js` | `RYFF_API` = Apps Script 웹 앱 URL |
| `apps-script/Code.gs` | `doPost` 저장, `score` 채점, `setup` 시트 머리글 |
| `apps-script/README.md` | 백엔드 배포 절차 (응답 시트 링크 포함) |
| `tests/check.mjs` | 문항·역채점·채점 로직 검증, `--browser`로 Playwright 끝까지 진행 |

## 규칙

- 채점을 바꾸면 `ryff/`와 `apps-script/Code.gs`를 같이 바꾸고 `node tests/check.mjs`로 확인.
- `Code.gs` 수정 후에는 Apps Script에서 기존 배포를 새 버전으로 바꿔야 반영된다(사용자 몫). URL은 유지된다.
