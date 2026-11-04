"""Append daily closing prices for 한진칼(180640) and 대한항공(003490) to LIVE in index.html.

Source: Naver fchart (primary), Yahoo Finance chart API (fallback).
Adds every trading day after the last LIVE row on which both closes exist.
Share counts are carried forward from the last row.
"""
import json, re, sys, urllib.request
from datetime import datetime, timedelta, timezone

PATH = "index.html"
CODES = {"hk": "180640", "kal": "003490"}
UA = {"User-Agent": "Mozilla/5.0 (koreanair-ratio updater)"}
KST = timezone(timedelta(hours=9))


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read()


def naver(code):
    raw = get(f"https://fchart.stock.naver.com/sise.nhn?symbol={code}&timeframe=day&count=30&requestType=0")
    text = raw.decode("euc-kr", errors="replace")
    out = {}
    for m in re.finditer(r'data="(\d{8})\|[^|]*\|[^|]*\|[^|]*\|(\d+)\|', text):
        d = m.group(1)
        out[f"{d[:4]}-{d[4:6]}-{d[6:]}"] = int(m.group(2))
    return out


def yahoo(code):
    raw = get(f"https://query1.finance.yahoo.com/v8/finance/chart/{code}.KS?range=1mo&interval=1d")
    res = json.loads(raw)["chart"]["result"][0]
    out = {}
    for ts, c in zip(res["timestamp"], res["indicators"]["quote"][0]["close"]):
        if c:
            out[datetime.fromtimestamp(ts, KST).strftime("%Y-%m-%d")] = int(round(c))
    return out


def closes(code):
    for name, fn in (("네이버", naver), ("Yahoo", yahoo)):
        try:
            data = fn(code)
            if data:
                return data, name
        except Exception as e:  # noqa: BLE001
            print(f"{name} {code} 실패: {e}", file=sys.stderr)
    raise SystemExit(f"{code} 시세를 가져오지 못함")


def main():
    html = open(PATH, encoding="utf-8").read()
    # tmp:1 행은 장중 임시값이라 확정 종가가 나오면 대체한다
    tmp_re = r'\{d:"(\d{4}-\d{2}-\d{2})",hk:\d+,kal:\d+,hkSh:(\d+),kalSh:(\d+),tmp:1,[^\n]*\n'
    final = re.sub(tmp_re, "", html)
    rows = re.findall(r'\{d:"(\d{4}-\d{2}-\d{2})",hk:\d+,kal:\d+,hkSh:(\d+),kalSh:(\d+)', final)
    last_d, hk_sh, kal_sh = max(rows)
    hk, hk_src = closes(CODES["hk"])
    kal, kal_src = closes(CODES["kal"])
    today = datetime.now(KST).strftime("%Y-%m-%d")
    new_days = sorted(d for d in set(hk) & set(kal) if last_d < d <= today)
    if not new_days:
        print(f"새 거래일 없음 (마지막 {last_d})")
        return
    for d in new_days:  # 같은 날짜의 임시 행 제거
        html = re.sub(r'\{d:"' + d + r'",[^\n]*tmp:1,[^\n]*\n', "", html)
    src = f"한진칼·대한항공 종가({hk_src})" if hk_src == kal_src else f"한진칼 종가({hk_src}), 대한항공 종가({kal_src})"
    lines = "".join(
        f'{{d:"{d}",hk:{hk[d]},kal:{kal[d]},hkSh:{hk_sh},kalSh:{kal_sh},src:"{src}"}},\n' for d in new_days
    )
    html = html.replace("/*ROWS_END*/", lines + "/*ROWS_END*/", 1)
    open(PATH, "w", encoding="utf-8").write(html)
    for d in new_days:
        print(f"추가 {d}: 한진칼 {hk[d]:,} / 대한항공 {kal[d]:,} / 비율 {hk[d]*int(hk_sh)/(kal[d]*int(kal_sh)):.3f}")


if __name__ == "__main__":
    main()
