import { test } from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { accessTokenHash, createDpopKey, createProof, htuOf } from '../src/dpop.js';
import { authorizeUrl, createPkce, exchangeCode, jwkThumbprint } from '../src/auth.js';

const subtle = webcrypto.subtle;
const decode = (part) => JSON.parse(Buffer.from(part, 'base64url').toString('utf8'));

test('the private key cannot be exported', async () => {
  const key = await createDpopKey(subtle);
  assert.equal(key.privateKey.extractable, false);
  assert.deepEqual(Object.keys(key.publicJwk).sort(), ['crv', 'kty', 'x', 'y']);
  await assert.rejects(subtle.exportKey('jwk', key.privateKey));
});

test('a proof names the method, the URL without query and the token hash, and verifies with the public key', async () => {
  const key = await createDpopKey(subtle);
  const proof = await createProof(key, 'post', 'https://api.example/api/v1/actors/compliance-analyst/tasks/7/decision?x=1#f',
    { accessToken: 'token-123', now: 1_760_000_000_000, jti: 'j-1' }, subtle);
  const [h, p, s] = proof.split('.');
  assert.deepEqual(decode(h), { typ: 'dpop+jwt', alg: 'ES256', jwk: key.publicJwk });
  assert.deepEqual(decode(p), {
    jti: 'j-1', htm: 'POST', htu: 'https://api.example/api/v1/actors/compliance-analyst/tasks/7/decision',
    iat: 1_760_000_000, ath: await accessTokenHash('token-123', subtle),
  });
  const publicKey = await subtle.importKey('jwk', key.publicJwk, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify']);
  const ok = await subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, publicKey, Buffer.from(s, 'base64url'),
    new TextEncoder().encode(`${h}.${p}`));
  assert.equal(ok, true);
});

test('the token hash is base64url SHA-256 (RFC 9449 example)', async () => {
  assert.equal(await accessTokenHash('Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU', subtle),
    'fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo');
  assert.equal(htuOf('https://a.example:8443/p?q#f'), 'https://a.example:8443/p');
});

test('PKCE uses S256 and the authorization request binds the DPoP key', async () => {
  const { verifier, challenge } = await createPkce(subtle);
  assert.match(verifier, /^[A-Za-z0-9_-]{43}$/);
  const expected = Buffer.from(await subtle.digest('SHA-256', new TextEncoder().encode(verifier))).toString('base64url');
  assert.equal(challenge, expected);
  const url = new URL(authorizeUrl({ authorizationEndpoint: 'https://kc.example/auth', clientId: 'bpo-staff-web',
    redirectUri: 'https://staff.example/', scope: 'openid' }, { state: 's', challenge, jkt: 'jkt-1' }));
  assert.equal(url.searchParams.get('code_challenge_method'), 'S256');
  assert.equal(url.searchParams.get('dpop_jkt'), 'jkt-1');
});

test('the RFC 7638 thumbprint matches the known example', async () => {
  // RFC 7638 section 3.1 uses an RSA key; this EC vector is from RFC 9449 section 6.1 (jkt of its proof key).
  const jwk = { kty: 'EC', crv: 'P-256', x: 'l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs', y: '9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA' };
  assert.equal(await jwkThumbprint(jwk, subtle), '0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I');
});

test('a token the realm did not bind to the key is refused', async () => {
  const key = await createDpopKey(subtle);
  const oidc = { tokenEndpoint: 'https://kc.example/token', redirectUri: 'https://staff.example/', clientId: 'bpo-staff-web' };
  let sent;
  const fetchImpl = async (url, init) => {
    sent = init;
    return { ok: true, json: async () => ({ access_token: 'a', token_type: 'Bearer', expires_in: 300 }) };
  };
  await assert.rejects(exchangeCode(oidc, key, { code: 'c', verifier: 'v' }, fetchImpl), /not DPoP-bound/);
  assert.equal(decode(sent.headers.DPoP.split('.')[1]).htu, 'https://kc.example/token');
  const bound = async () => ({ ok: true, json: async () => ({ access_token: 'a', token_type: 'DPoP', expires_in: 300 }) });
  assert.equal((await exchangeCode(oidc, key, { code: 'c', verifier: 'v' }, bound)).accessToken, 'a');
});
