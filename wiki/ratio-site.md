# 시총 비율 사이트 (`index.html`)

한진칼 시총 ÷ 대한항공 시총 비율의 추이와 비율별 가격 계산기. GitHub Pages: https://eetlkeu-pixel.github.io/koreanair/

## 구간 찾기

줄 번호는 매일 바뀌므로 앵커로 찾는다: `grep -n '<앵커>' index.html` 후 그 구간만 `sed -n`으로 읽는다.

| 섹션 | 앵커 |
|---|---|
| 상단 바·종가 테이프 | `class="topbar"`, `class="tape"` |
| 분쟁별 비율 사실 | `id="h-facts"` |
| 장기 차트 / 최근 일별 차트 | `id="h-chart"`, `id="h-daily"` |
| 비율→가격 계산기 | `id="h-calc"` (로직: `// 계산기`) |
| 주가×비율 조합표 | `id="h-grid"` (로직: `// 조합표`) |
| 읽는 법 | `id="h-read"` |
| 분쟁주 되돌림 사례 | `id="h-lesson"` |
| 다음 액션 타임라인 | `id="h-time"` |
| 차트 로직 | `// 차트` |
| 스타일 | `<style>` (11행 부근) |

## 규칙

- 데이터 배열 `LIVE`는 [live-data.md](live-data.md) 소관. 화면 작업에서 건드리지 않는다.
- 새 해설·전망 문구는 `tail-scenarios` 스킬 형식(시나리오·확률·극단값)을 따른다.
- 외부 스크립트는 Chart.js 4.4.1(cdnjs) 하나뿐이다.
