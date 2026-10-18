// DPoP (RFC 9449) for the staff web. The key pair is created in the browser with WebCrypto as
// non-extractable: the private key never leaves the crypto store, so a token copied out of the page is useless
// without it. Every API call and the token request carry a fresh proof bound to the method and URL.

const encoder = new TextEncoder();

export function base64url(bytes) {
  const b = bytes instanceof ArrayBuffer ? new Uint8Array(bytes) : bytes;
  let s = '';
  for (const x of b) s += String.fromCharCode(x);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function base64urlJson(value) {
  return base64url(encoder.encode(JSON.stringify(value)));
}

/** A new ES256 key pair; only the public half can be exported. */
export async function createDpopKey(subtle = globalThis.crypto.subtle) {
  const pair = await subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign', 'verify']);
  const { kty, crv, x, y } = await subtle.exportKey('jwk', pair.publicKey);
  return { privateKey: pair.privateKey, publicJwk: { kty, crv, x, y } };
}

/** The proof's htu: scheme, host and path of the request, without query or fragment (RFC 9449 §4.2). */
export function htuOf(url) {
  const u = new URL(url);
  return `${u.origin}${u.pathname}`;
}

/** SHA-256 of the access token, base64url: the proof's ath claim. */
export async function accessTokenHash(token, subtle = globalThis.crypto.subtle) {
  return base64url(await subtle.digest('SHA-256', encoder.encode(token)));
}

/**
 * A signed DPoP proof for one request. {@code accessToken} is omitted on the token request and present on
 * every resource call (ath).
 */
export async function createProof(key, method, url, { accessToken, now = Date.now(), jti, nonce } = {},
  subtle = globalThis.crypto.subtle) {
  const header = { typ: 'dpop+jwt', alg: 'ES256', jwk: key.publicJwk };
  const payload = {
    jti: jti ?? globalThis.crypto.randomUUID(),
    htm: method.toUpperCase(),
    htu: htuOf(url),
    iat: Math.floor(now / 1000),
  };
  if (accessToken) payload.ath = await accessTokenHash(accessToken, subtle);
  if (nonce) payload.nonce = nonce;
  const input = `${base64urlJson(header)}.${base64urlJson(payload)}`;
  // WebCrypto ECDSA returns r||s (IEEE P1363), which is the JWS ES256 signature format.
  const signature = await subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key.privateKey, encoder.encode(input));
  return `${input}.${base64url(signature)}`;
}
