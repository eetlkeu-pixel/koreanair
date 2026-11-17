// 공지 내용을 초대코드로 암호화해서 사이트 파일을 만든다.
//   node build.mjs            → site/index.html (배포용), dist/artifact.html (미리보기용)
//   node build.mjs --decrypt  → site/index.html 에서 content.json 복원 (초대코드 필요)
// content.json 과 codes.json 은 평문이므로 공개 저장소에 올리지 않는다.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { webcrypto as crypto } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const p = (f) => join(dir, f);
const ITER = 600000;
const b64 = (buf) => Buffer.from(buf).toString('base64');
const unb64 = (s) => new Uint8Array(Buffer.from(s, 'base64'));
const norm = (code) => code.toUpperCase().replace(/[\s-]/g, '');

async function codeKey(code, salt) {
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(norm(code)), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: ITER }, base,
    { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}

function newCode() {
  const abc = '23456789ABCDEFGHJKMNPQRSTVWXYZ';
  const r = crypto.getRandomValues(new Uint8Array(12));
  const s = [...r].map((x) => abc[x % abc.length]).join('');
  return `BINGO-${s.slice(0, 4)}-${s.slice(4, 8)}-${s.slice(8)}`;
}

async function encrypt(text, codes) {
  const raw = crypto.getRandomValues(new Uint8Array(32));
  const key = await crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt']);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(text));
  const slots = [];
  for (const code of codes) {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const siv = crypto.getRandomValues(new Uint8Array(12));
    const wrapped = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: siv }, await codeKey(code, salt), raw);
    slots.push({ salt: b64(salt), iv: b64(siv), key: b64(wrapped) });
  }
  return { v: 1, iter: ITER, slots, iv: b64(iv), ct: b64(ct) };
}

async function decrypt(payload, code) {
  for (const s of payload.slots) {
    try {
      const raw = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(s.iv) }, await codeKey(code, unb64(s.salt)), unb64(s.key));
      const key = await crypto.subtle.importKey('raw', raw, 'AES-GCM', false, ['decrypt']);
      const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(payload.iv) }, key, unb64(payload.ct));
      return new TextDecoder().decode(pt);
    } catch {}
  }
  throw new Error('초대코드가 맞지 않습니다.');
}

if (process.argv.includes('--decrypt')) {
  const code = process.argv[process.argv.indexOf('--decrypt') + 1];
  const html = readFileSync(p('site/index.html'), 'utf8');
  const payload = JSON.parse(html.match(/const PAYLOAD = (\{.*?\});/s)[1]);
  writeFileSync(p('content.json'), await decrypt(payload, code));
  console.log('content.json 복원 완료');
} else {
  if (!existsSync(p('codes.json'))) writeFileSync(p('codes.json'), JSON.stringify([newCode()], null, 2) + '\n');
  const codes = JSON.parse(readFileSync(p('codes.json'), 'utf8'));
  const content = JSON.stringify(JSON.parse(readFileSync(p('content.json'), 'utf8')));
  const payload = await encrypt(content, codes);
  if ((await decrypt(payload, codes[0])) !== content) throw new Error('검증 실패');
  const tpl = readFileSync(p('template.html'), 'utf8').replace('/*__PAYLOAD__*/null', JSON.stringify(payload));
  const [head, body] = tpl.split('<!--BODY-->');
  mkdirSync(p('site'), { recursive: true }); mkdirSync(p('dist'), { recursive: true });
  writeFileSync(p('dist/artifact.html'), head + body);
  writeFileSync(p('site/index.html'),
    `<!doctype html>\n<html lang="ko">\n<head>\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n<meta name="robots" content="noindex, nofollow">\n${head}</head>\n<body>\n${body}</body>\n</html>\n`);
  console.log('빌드 완료. 초대코드:', codes.join(', '));
}
