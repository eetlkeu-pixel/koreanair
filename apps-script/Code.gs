/**
 * Ryff 심리적 안녕감 48문항 — 채점·저장 백엔드 (Apps Script 웹 앱)
 *
 * 배포 설정: 실행 사용자 = 나(운영자), 액세스 = 모든 사용자(익명 포함).
 * 참가 화면(GitHub Pages)이 이 웹 앱의 /exec 주소로 POST 하면,
 * 서버에서 채점해 운영자의 비공개 스프레드시트에 저장하고 결과를 돌려준다.
 * 참가자는 Google 로그인이 필요 없고, 시트는 운영자만 열 수 있다.
 */

var SHEET_ID = '160pAR0LIP-IlQoPRvGMVMSALZvHu_gTgycIOa8gR1I4';
var SHEET_NAME = '응답';
var VERSION = 'ryff48-v1';

var DOMAIN_IDS = ['AU', 'EM', 'PG', 'PR', 'PL', 'SA'];
var DOMAIN_NAMES = { AU: '자율성', EM: '환경 통제력', PG: '개인적 성장', PR: '긍정적 대인관계', PL: '삶의 목적', SA: '자아수용' };

// 문항 1~48의 [영역, 역채점 여부]. ryff/items.js 와 같아야 한다(tests/check.mjs 가 검사).
// 역채점 문항: 6 - 응답값.
var KEY = [
  ['AU', 0], ['EM', 1], ['PG', 0], ['PR', 1], ['PL', 0], ['SA', 1],
  ['AU', 1], ['EM', 0], ['PG', 1], ['PR', 0], ['PL', 1], ['SA', 0],
  ['AU', 0], ['EM', 1], ['PG', 0], ['PR', 1], ['PL', 0], ['SA', 1],
  ['AU', 1], ['EM', 0], ['PG', 1], ['PR', 0], ['PL', 1], ['SA', 0],
  ['AU', 0], ['EM', 1], ['PG', 0], ['PR', 1], ['PL', 0], ['SA', 1],
  ['AU', 1], ['EM', 0], ['PG', 1], ['PR', 0], ['PL', 1], ['SA', 0],
  ['AU', 0], ['EM', 1], ['PG', 0], ['PR', 1], ['PL', 0], ['SA', 1],
  ['AU', 1], ['EM', 0], ['PG', 1], ['PR', 0], ['PL', 1], ['SA', 0]
];

var CODE_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

function doGet() {
  return json_({ ok: true, service: 'ryff48', version: VERSION });
}

function doPost(e) {
  var body;
  try {
    body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return json_({ ok: false, error: '요청 형식이 올바르지 않습니다.' });
  }
  try {
    if (body.action === 'withdraw') return json_(withdraw_(body));
    return json_(submit_(body));
  } catch (err) {
    console.error(err);
    return json_({ ok: false, error: '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.' });
  }
}

/** 응답 48개(1~5 정수)를 받아 채점한다. 잘못된 입력이면 예외. */
function score(answers) {
  if (!Array.isArray(answers) || answers.length !== 48) throw new Error('48개 응답이 필요합니다.');
  var sums = {};
  DOMAIN_IDS.forEach(function (d) { sums[d] = 0; });
  var scored = [];
  for (var i = 0; i < 48; i++) {
    var v = answers[i];
    if (typeof v !== 'number' || v % 1 !== 0 || v < 1 || v > 5) throw new Error((i + 1) + '번 응답이 올바르지 않습니다.');
    var s = KEY[i][1] ? 6 - v : v;
    scored.push(s);
    sums[KEY[i][0]] += s;
  }
  var total = 0;
  var domains = DOMAIN_IDS.map(function (d) {
    total += sums[d];
    return { id: d, name: DOMAIN_NAMES[d], score: sums[d], max: 40, mean: Math.round(sums[d] / 8 * 100) / 100, level: level_(sums[d] / 8) };
  });
  return { total: total, totalMax: 240, totalMean: Math.round(total / 48 * 100) / 100, totalLevel: level_(total / 48), domains: domains, scored: scored };
}

// 문항 평균 기준 해석 구간: 2.5 이하 낮음, 3.5 이하 보통, 그 위 높음.
function level_(mean) {
  if (mean <= 2.5) return '낮음';
  if (mean <= 3.5) return '보통';
  return '높음';
}

function submit_(body) {
  var answers = (body.answers || []).map(function (x) { return Number(x); });
  var result;
  try {
    result = score(answers);
  } catch (err) {
    return { ok: false, error: err.message };
  }
  var isTest = body.test === true;
  var withdrawKey = Utilities.getUuid();
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sheet = sheet_();
    var code = newCode_(sheet);
    var now = Utilities.formatDate(new Date(), 'Asia/Seoul', 'yyyy-MM-dd HH:mm:ss');
    var row = [code, now, isTest ? '테스트' : '실제', result.total, result.totalLevel]
      .concat(result.domains.map(function (d) { return d.score; }))
      .concat(answers)
      .concat([hash_(withdrawKey), VERSION]);
    sheet.appendRow(row);
  } finally {
    lock.releaseLock();
  }
  delete result.scored;
  return { ok: true, code: code, withdrawKey: withdrawKey, result: result };
}

/** 응답 코드와 제출 때 받은 철회 키가 맞으면 해당 응답 행을 삭제한다. */
function withdraw_(body) {
  var code = String(body.code || '').toUpperCase();
  var key = String(body.withdrawKey || '');
  if (!code || !key) return { ok: false, error: '코드와 키가 필요합니다.' };
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sheet = sheet_();
    var last = sheet.getLastRow();
    if (last < 2) return { ok: false, error: '해당 응답이 없습니다.' };
    var width = header_().length;
    var data = sheet.getRange(2, 1, last - 1, width).getValues();
    var target = hash_(key);
    for (var i = data.length - 1; i >= 0; i--) {
      if (data[i][0] === code && data[i][width - 2] === target) {
        sheet.deleteRow(i + 2);
        return { ok: true, deleted: code };
      }
    }
    return { ok: false, error: '해당 응답이 없습니다.' };
  } finally {
    lock.releaseLock();
  }
}

function header_() {
  var h = ['응답코드', '제출시각(KST)', '구분', '총점(48-240)', '총점 수준'];
  DOMAIN_IDS.forEach(function (d) { h.push(DOMAIN_NAMES[d] + '(8-40)'); });
  for (var i = 1; i <= 48; i++) h.push('Q' + i + (KEY[i - 1][1] ? '(R)' : ''));
  h.push('철회키해시', '버전');
  return h;
}

function sheet_() {
  var ss = SpreadsheetApp.openById(SHEET_ID);
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME, 0);
  }
  if (sheet.getLastRow() === 0) {
    var h = header_();
    sheet.getRange(1, 1, 1, h.length).setValues([h]).setFontWeight('bold');
    sheet.setFrozenRows(1);
    sheet.setFrozenColumns(1);
  }
  return sheet;
}

function newCode_(sheet) {
  var existing = {};
  var last = sheet.getLastRow();
  if (last >= 2) {
    sheet.getRange(2, 1, last - 1, 1).getValues().forEach(function (r) { existing[r[0]] = true; });
  }
  for (var attempt = 0; attempt < 20; attempt++) {
    var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, Utilities.getUuid());
    var code = 'R-';
    for (var i = 0; i < 6; i++) code += CODE_CHARS.charAt(((bytes[i] % 256) + 256) % 256 % CODE_CHARS.length);
    if (!existing[code]) return code;
  }
  throw new Error('코드 생성 실패');
}

function hash_(s) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, s);
  return bytes.map(function (b) { return ('0' + ((b + 256) % 256).toString(16)).slice(-2); }).join('');
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** 편집기에서 한 번 실행하면 시트 머리글을 만들고 권한 승인을 받는다. */
function setup() {
  var sheet = sheet_();
  console.log('시트 준비 완료: ' + sheet.getParent().getUrl());
}
