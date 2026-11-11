// 문항 배정·역채점 검증, Code.gs 채점/저장 로직 검증(가짜 SpreadsheetApp),
// 그리고 Playwright 로 참가 화면을 끝까지 진행해 결과 표시를 확인한다.
// 실행: node tests/check.mjs [--browser]
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import crypto from 'node:crypto';
import http from 'node:http';
import path from 'node:path';
import assert from 'node:assert/strict';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const R = require(path.join(root, 'ryff/items.js'));
const T = require(path.join(root, 'ryff/interpret.js'));

// 1) 문항 구성
assert.equal(R.ITEMS.length, 48, '48문항');
R.ITEMS.forEach((it, i) => assert.equal(it.n, i + 1, '번호 연속'));
assert.equal(new Set(R.ITEMS.map((i) => i.t)).size, 48, '문구 중복 없음');
for (const d of R.DOMAINS) {
  const its = R.ITEMS.filter((i) => i.d === d.id);
  assert.equal(its.length, 8, d.id + ' 8문항');
  assert.equal(its.filter((i) => i.rev).length, 4, d.id + ' 역채점 4');
  for (const lv of ['낮음', '보통', '높음']) assert.ok(T.TEXT[d.id][lv], d.id + lv + ' 해석');
}
assert.equal(R.SCALE.length, 5);

// 2) Code.gs 를 가짜 Apps Script 환경에서 실행
function makeGas() {
  const rows = [];
  const sheet = {
    getLastRow: () => rows.length,
    getRange: (r, c, nr, nc) => ({
      setValues: (v) => { v.forEach((row, k) => { rows[r - 1 + k] = row.slice(); }); return { setFontWeight() {} }; },
      getValues: () => rows.slice(r - 1, r - 1 + nr).map((row) => row.slice(c - 1, c - 1 + nc)),
    }),
    setFrozenRows() {}, setFrozenColumns() {},
    appendRow: (row) => rows.push(row.slice()),
    deleteRow: (i) => rows.splice(i - 1, 1),
    getParent: () => ({ getUrl: () => 'fake' }),
  };
  const ctx = {
    console,
    SpreadsheetApp: { openById: () => ({ getSheetByName: () => sheet, insertSheet: () => sheet }) },
    LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
    Utilities: {
      getUuid: () => crypto.randomUUID(),
      DigestAlgorithm: { SHA_256: 'sha256' },
      computeDigest: (_a, s) => [...crypto.createHash('sha256').update(s).digest()].map((b) => (b > 127 ? b - 256 : b)),
      formatDate: (d) => d.toISOString(),
    },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: (s) => ({ setMimeType() { return { body: s }; } }),
    },
  };
  vm.createContext(ctx);
  vm.runInContext(readFileSync(path.join(root, 'apps-script/Code.gs'), 'utf8'), ctx);
  const post = (obj) => JSON.parse(ctx.doPost({ postData: { contents: JSON.stringify(obj) } }).body);
  return { ctx, rows, post };
}
const gas = makeGas();

// KEY 표가 items.js 와 일치
const key = vm.runInContext('KEY', gas.ctx);
R.ITEMS.forEach((it, i) => assert.deepEqual([key[i][0], !!key[i][1]], [it.d, it.rev], `${it.n}번 KEY 일치`));

// 독립 구현으로 채점 비교
function refScore(a) {
  const sums = Object.fromEntries(R.DOMAINS.map((d) => [d.id, 0]));
  R.ITEMS.forEach((it, i) => { sums[it.d] += it.rev ? 6 - a[i] : a[i]; });
  return sums;
}
for (let k = 0; k < 200; k++) {
  const a = Array.from({ length: 48 }, () => 1 + Math.floor(Math.random() * 5));
  const s = vm.runInContext('score', gas.ctx)(a);
  const ref = refScore(a);
  s.domains.forEach((d) => assert.equal(d.score, ref[d.id]));
  assert.equal(s.total, Object.values(ref).reduce((x, y) => x + y));
}
// 경계값: 모두 5 → 정채점 5, 역채점 1 → 영역 24, 총 144
const all5 = vm.runInContext('score', gas.ctx)(Array(48).fill(5));
assert.equal(all5.total, 144); all5.domains.forEach((d) => assert.equal(d.score, 24));
// 최고점 응답: 정채점 5, 역채점 1 → 240
const best = R.ITEMS.map((i) => (i.rev ? 1 : 5));
const bs = vm.runInContext('score', gas.ctx)(best);
assert.equal(bs.total, 240); assert.equal(bs.totalLevel, '높음');
const worst = vm.runInContext('score', gas.ctx)(R.ITEMS.map((i) => (i.rev ? 5 : 1)));
assert.equal(worst.total, 48); assert.equal(worst.totalLevel, '낮음');

// 잘못된 입력 거부
assert.equal(gas.post({ answers: Array(47).fill(3) }).ok, false);
assert.equal(gas.post({ answers: [...Array(47).fill(3), 6] }).ok, false);
assert.equal(gas.post({ answers: [...Array(47).fill(3), 2.5] }).ok, false);
assert.equal(gas.rows.length, 0, '거부된 요청은 저장 안 됨');

// 제출 → 저장 → 철회
const sub = gas.post({ answers: best, test: true });
assert.ok(sub.ok && /^R-[A-Z2-9]{6}$/.test(sub.code), '코드 형식');
assert.equal(gas.rows.length, 2, '머리글 + 1행');
const hdr = gas.rows[0], row = gas.rows[1];
assert.equal(hdr.length, row.length, '열 수 일치');
assert.equal(row[0], sub.code); assert.equal(row[2], '테스트'); assert.equal(row[3], 240);
assert.equal(hdr[5 + 6 + 1], 'Q2(R)'); assert.equal(hdr[5 + 6 + 10], 'Q11(R)');
assert.ok(!row.includes(sub.withdrawKey), '철회 키 원문은 저장 안 함');
assert.equal(gas.post({ action: 'withdraw', code: sub.code, withdrawKey: 'wrong' }).ok, false);
assert.equal(gas.post({ action: 'withdraw', code: sub.code, withdrawKey: sub.withdrawKey }).ok, true);
assert.equal(gas.rows.length, 1, '철회 후 머리글만 남음');
console.log('문항·역채점·채점·저장 검증 통과');

// 3) 브라우저 흐름 (가짜 API 로 Code.gs 를 그대로 사용)
if (process.argv.includes('--browser')) {
  const { chromium, devices } = require('playwright');
  const server = http.createServer((req, res) => {
    const f = path.join(root, decodeURIComponent(req.url.split('?')[0]));
    try {
      const body = readFileSync(f.endsWith('/') ? f + 'index.html' : f);
      res.writeHead(200, { 'Content-Type': f.endsWith('.js') ? 'text/javascript' : 'text/html; charset=utf-8' });
      res.end(body);
    } catch { res.writeHead(404); res.end(); }
  }).listen(0);
  const port = server.address().port;
  const g2 = makeGas();
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ ...devices['Pixel 7'] });
  const page = await ctx.newPage();
  const EXEC = 'https://script.google.com/macros/s/FAKE_ID-123/exec';
  await page.route('**/config.js', (r) => r.fulfill({ contentType: 'text/javascript', body: `window.RYFF_API='${EXEC}';` }));
  let sawCookie = null;
  await page.route(EXEC, async (r) => {
    sawCookie = r.request().headers()['cookie'] || '';
    r.fulfill({ contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify(g2.post(JSON.parse(r.request().postData()))) });
  });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(`http://localhost:${port}/ryff/?test=1`);
  await page.click('#start');
  // 1페이지에서 하나 빠뜨리고 다음 → 경고
  for (let i = 1; i < 6; i++) await page.click(`#q${i + 1} label:nth-child(3)`);
  await page.click('#next');
  assert.ok(await page.isVisible('#pageErr'), '미응답 경고');
  const pick = (n) => (R.ITEMS[n - 1].rev ? 2 : 4); // 정채점 4, 역채점 2 → 전부 4점
  for (let p = 0; p < 8; p++) {
    for (let n = p * 6 + 1; n <= p * 6 + 6; n++) await page.click(`#q${n} label:nth-child(${pick(n)})`);
    await page.click('#next');
  }
  await page.waitForSelector('#result:not([hidden])');
  const total = await page.textContent('#rTotal');
  assert.equal(total, '192');
  assert.equal(await page.locator('.crow').count(), 6);
  assert.equal(await page.locator('.dom').count(), 6);
  assert.equal(g2.rows.length, 2); assert.equal(g2.rows[1][3], 192);
  assert.equal(sawCookie, '', '쿠키 없이 요청');
  const noHScroll = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  assert.ok(noHScroll, '가로 스크롤 없음');
  assert.deepEqual(errors, []);
  await page.screenshot({ path: process.env.SHOT || path.join(root, 'tests/result.png'), fullPage: true });
  await browser.close(); server.close();
  console.log('브라우저(Pixel 7 에뮬레이션) 흐름 통과: 총점', total);
}
