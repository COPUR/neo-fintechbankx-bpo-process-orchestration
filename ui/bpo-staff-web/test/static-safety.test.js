import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';

const root = new URL('..', import.meta.url);
const sources = readdirSync(new URL('src/', root)).map((f) => [f, readFileSync(new URL(`src/${f}`, root), 'utf8')]);
const html = readFileSync(new URL('index.html', root), 'utf8');

test('no source writes markup, evaluates strings or keeps tokens in web storage', () => {
  for (const [file, text] of sources) {
    for (const banned of ['innerHTML', 'outerHTML', 'insertAdjacentHTML', 'document.write', 'eval(', 'new Function', 'localStorage']) {
      assert.ok(!text.includes(banned), `${file} uses ${banned}`);
    }
    assert.ok(!/sessionStorage\.setItem\([^)]*(token|access)/i.test(text), `${file} stores a token in sessionStorage`);
  }
});

test('the page has a language, a skip link, landmarks, live regions and a script-only-from-self policy', () => {
  assert.match(html, /<html lang="tr">/);
  assert.match(html, /class="skip-link" href="#main"/);
  assert.match(html, /<main id="main"/);
  assert.match(html, /role="alert" aria-live="assertive"/);
  assert.match(html, /role="status" aria-live="polite"/);
  assert.match(html, /script-src 'self'/);
  assert.ok(!/<script>(?!<)/.test(html), 'no inline script');
});
