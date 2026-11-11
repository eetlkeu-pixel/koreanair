# Ryff 48문항 설문 백엔드 배포 (한 번만)

1. https://script.new 를 운영자 계정으로 엽니다. 다중 로그인이면 주소의 `/u/N`이 운영자 계정인지 확인합니다.
2. 기본 `Code.gs` 내용을 지우고 이 폴더의 `Code.gs` 전체를 붙여넣습니다.
3. 프로젝트 설정(톱니바퀴)에서 "편집기에 appsscript.json 매니페스트 파일 표시"를 켜고, `appsscript.json`을 이 폴더 내용으로 바꿉니다.
4. 함수 선택에서 `setup`을 고르고 실행 → 권한을 승인합니다. 응답 시트에 머리글이 생깁니다.
5. 배포 → 새 배포 → 유형 "웹 앱" → 실행 사용자 "나", 액세스 권한 "모든 사용자" → 배포.
6. 웹 앱 URL(`https://script.google.com/macros/s/…/exec`)을 `ryff/config.js`의 `RYFF_API`에 넣고 `main`에 push 합니다.

참가자에게는 Apps Script 주소가 아니라 `https://eetlkeu-pixel.github.io/koreanair/ryff/`를 보냅니다.
코드를 고친 뒤에는 배포 → 배포 관리 → 기존 배포 편집 → 새 버전으로 바꾸면 URL이 그대로 유지됩니다.

응답 시트: https://docs.google.com/spreadsheets/d/160pAR0LIP-IlQoPRvGMVMSALZvHu_gTgycIOa8gR1I4/edit
