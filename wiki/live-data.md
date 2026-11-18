# 종가 데이터·배포

## 흐름

`pages.yml`이 평일 16:20 / 21:47 / 다음날 08:47 KST에 `python3 scripts/update_live.py` 실행 → `index.html`의 `LIVE` 배열 끝 `/*ROWS_END*/` 앞에 행 추가 → 변경 시 `main`에 "종가 자동 갱신 YYYY-MM-DD" 커밋 → Pages 배포. `main` push 때는 갱신 없이 배포만.

## 사실

- 시세: Yahoo chart API, KRX 정규장 종가. 네이버는 NXT 애프터마켓이 섞여 쓰지 않는다.
- 당일 종가는 KST 15:40 이후만 인정.
- 행 형식: `{d:"YYYY-MM-DD",hk:종가,kal:종가,hkSh:주식수,kalSh:주식수,src:"..."}`. `tmp:1` 행은 임시값으로 다음 실행이 덮어쓴다.
- 주식 수는 마지막 행에서 이월. 한진칼 66,762,279주. 대한항공 3.682억 주 → 아시아나 합병 신주 상장(2026.12.16 이후) 시 약 3.885억 주로 수동 갱신 필요.

## 구간 찾기

- `grep -n 'const LIVE\|ROWS_END' index.html` 후 그 사이만 읽는다. 최근 행만 보려면 `grep -n 'ROWS_END' index.html` 위 5~10줄.
- `update_live.py`는 3KB라 통째로 읽어도 된다.
