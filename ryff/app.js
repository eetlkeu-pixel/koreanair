(function () {
  'use strict';
  var R = window.RYFF, T = window.RYFF_TEXT;
  var PER_PAGE = 6;
  var PAGES = Math.ceil(R.ITEMS.length / PER_PAGE);
  var STORE = 'ryff48-answers';
  var answers = load();
  var page = 0;
  var isTest = /[?&]test=1\b/.test(location.search);

  function $(id) { return document.getElementById(id); }
  function load() {
    try {
      var a = JSON.parse(sessionStorage.getItem(STORE) || 'null');
      if (Array.isArray(a) && a.length === 48) return a;
    } catch (e) {}
    return new Array(48).fill(0);
  }
  function save() { try { sessionStorage.setItem(STORE, JSON.stringify(answers)); } catch (e) {} }
  function show(id) {
    ['intro', 'survey', 'result'].forEach(function (s) { $(s).hidden = s !== id; });
    window.scrollTo(0, 0);
  }
  function apiReady() { return /^https:\/\/script\.google\.com\/macros\/s\/[\w-]+\/exec$/.test(window.RYFF_API || ''); }

  function renderPage() {
    var start = page * PER_PAGE, html = '';
    R.ITEMS.slice(start, start + PER_PAGE).forEach(function (it) {
      var i = it.n - 1;
      html += '<div class="q" id="q' + it.n + '"><div class="qt"><b>' + it.n + '</b>' + it.t + '</div><div class="opts" role="radiogroup" aria-label="' + it.n + '번 문항">';
      R.SCALE.forEach(function (s) {
        html += '<label><input type="radio" name="a' + i + '" value="' + s.v + '"' + (answers[i] === s.v ? ' checked' : '') +
          '><span class="n">' + s.v + '</span>' + s.label + '</label>';
      });
      html += '</div></div>';
    });
    $('qs').innerHTML = html;
    $('pageLabel').textContent = (page + 1) + ' / ' + PAGES + ' 페이지';
    $('prev').disabled = page === 0;
    $('next').textContent = page === PAGES - 1 ? '제출하고 결과 보기' : '다음';
    $('pageErr').hidden = true;
    updateProgress();
  }
  function updateProgress() {
    var done = answers.filter(function (v) { return v > 0; }).length;
    $('countLabel').textContent = done + ' / 48 응답';
    $('barFill').style.width = (done / 48 * 100) + '%';
  }

  $('qs').addEventListener('change', function (e) {
    var m = /^a(\d+)$/.exec(e.target.name || '');
    if (!m) return;
    answers[+m[1]] = +e.target.value;
    save();
    var q = $('q' + (+m[1] + 1));
    if (q) q.classList.remove('missing');
    updateProgress();
  });

  $('start').addEventListener('click', function () {
    if (!apiReady()) { $('cfgErr').hidden = false; return; }
    page = 0; show('survey'); renderPage();
  });
  $('prev').addEventListener('click', function () { if (page > 0) { page--; renderPage(); window.scrollTo(0, 0); } });
  $('next').addEventListener('click', function () {
    var start = page * PER_PAGE, missing = [];
    for (var i = start; i < Math.min(start + PER_PAGE, 48); i++) if (!answers[i]) missing.push(i + 1);
    if (missing.length) {
      missing.forEach(function (n) { $('q' + n).classList.add('missing'); });
      $('pageErr').textContent = missing.join(', ') + '번 문항에 응답해 주세요.';
      $('pageErr').hidden = false;
      $('q' + missing[0]).scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    if (page < PAGES - 1) { page++; renderPage(); window.scrollTo(0, 0); return; }
    submit();
  });

  function submit() {
    var btn = $('next');
    btn.disabled = true; $('prev').disabled = true;
    btn.textContent = '제출 중…';
    // text/plain 으로 보내 CORS 사전 요청을 피하고, 쿠키를 보내지 않아(credentials: omit)
    // 기기에 여러 Google 계정이 로그인돼 있어도 익명 요청으로 처리되게 한다.
    fetch(window.RYFF_API, {
      method: 'POST', redirect: 'follow', credentials: 'omit',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify({ answers: answers, test: isTest })
    }).then(function (r) { return r.json(); }).then(function (res) {
      if (!res.ok) throw new Error(res.error || '제출에 실패했습니다.');
      try { sessionStorage.removeItem(STORE); } catch (e) {}
      try { sessionStorage.setItem('ryff48-last', JSON.stringify({ code: res.code, withdrawKey: res.withdrawKey })); } catch (e) {}
      renderResult(res);
    }).catch(function (err) {
      $('pageErr').textContent = (err && err.message && !/fetch|network|JSON/i.test(err.message) ? err.message : '네트워크 문제로 제출하지 못했습니다.') + ' 응답은 그대로 남아 있으니 다시 눌러 주세요.';
      $('pageErr').hidden = false;
      btn.disabled = false; $('prev').disabled = false;
      btn.textContent = '제출하고 결과 보기';
    });
  }

  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function pct(mean) { return (mean - 1) / 4 * 100; }

  function renderResult(res) {
    var r = res.result;
    $('rCode').textContent = res.code;
    $('rTotal').textContent = r.total;
    $('rTotalTag').textContent = r.totalLevel;
    $('rTotalTag').className = 'tag ' + r.totalLevel;
    $('rTotalText').textContent = T.TOTAL[r.totalLevel];
    var chart = '';
    r.domains.forEach(function (d) {
      chart += '<div class="crow"><div>' + esc(d.name) + '</div><div class="ctrack">' +
        '<div class="cfill ' + d.level + '" style="width:' + pct(d.mean) + '%"></div>' +
        '<div class="cmark" style="left:' + pct(2.5) + '%"></div><div class="cmark" style="left:' + pct(3.5) + '%"></div>' +
        '</div><div class="cval">' + d.score + '점</div></div>';
    });
    chart += '<div class="caxis"><div></div><div>' + [1, 2, 3, 4, 5].map(function (v) {
      return '<span style="left:' + pct(v) + '%">' + v + '</span>';
    }).join('') + '</div><div></div></div>';
    $('rChart').innerHTML = chart;
    $('rDomains').innerHTML = r.domains.map(function (d) {
      var def = R.DOMAINS.filter(function (x) { return x.id === d.id; })[0];
      return '<div class="dom"><h3>' + esc(d.name) + ' <span class="muted">' + d.score + ' / 40점 · 평균 ' + d.mean.toFixed(2) +
        '</span><span class="tag ' + d.level + '">' + d.level + '</span></h3><p class="muted">' + esc(def.desc) + '</p><p>' +
        esc(T.TEXT[d.id][d.level]) + '</p></div>';
    }).join('');
    show('result');
  }

  if (apiReady() === false && window.RYFF_API.indexOf('__') === 0) $('cfgErr').hidden = false;
})();
