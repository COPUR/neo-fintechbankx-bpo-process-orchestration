// Staff sign-in: OpenID Connect authorization code with PKCE (S256) against the staff realm, and a
// DPoP-bound token request. Tokens live in memory only (no web storage, no cookies); a reload signs in again.

import { base64url, createProof } from './dpop.js';

const encoder = new TextEncoder();

export async function createPkce(subtle = globalThis.crypto.subtle) {
  const verifier = base64url(globalThis.crypto.getRandomValues(new Uint8Array(32)));
  const challenge = base64url(await subtle.digest('SHA-256', encoder.encode(verifier)));
  return { verifier, challenge };
}

/** The authorization URL; {@code dpop_jkt} binds the code to the browser's DPoP key. */
export function authorizeUrl(oidc, { state, challenge, jkt }) {
  const u = new URL(oidc.authorizationEndpoint);
  u.searchParams.set('response_type', 'code');
  u.searchParams.set('client_id', oidc.clientId);
  u.searchParams.set('redirect_uri', oidc.redirectUri);
  u.searchParams.set('scope', oidc.scope);
  u.searchParams.set('state', state);
  u.searchParams.set('code_challenge', challenge);
  u.searchParams.set('code_challenge_method', 'S256');
  if (jkt) u.searchParams.set('dpop_jkt', jkt);
  return u.toString();
}

/** RFC 7638 thumbprint of the public EC key. */
export async function jwkThumbprint(publicJwk, subtle = globalThis.crypto.subtle) {
  const canonical = JSON.stringify({ crv: publicJwk.crv, kty: publicJwk.kty, x: publicJwk.x, y: publicJwk.y });
  return base64url(await subtle.digest('SHA-256', encoder.encode(canonical)));
}

/** Exchanges the code for a DPoP-bound access token. Refuses a token the server did not bind (token_type). */
export async function exchangeCode(oidc, key, { code, verifier }, fetchImpl = globalThis.fetch) {
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: oidc.redirectUri,
    client_id: oidc.clientId,
    code_verifier: verifier,
  });
  const response = await fetchImpl(oidc.tokenEndpoint, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded', DPoP: await createProof(key, 'POST', oidc.tokenEndpoint) },
    body,
  });
  if (!response.ok) throw new Error('token request refused');
  const token = await response.json();
  if (typeof token.access_token !== 'string' || String(token.token_type).toLowerCase() !== 'dpop') {
    throw new Error('the token is not DPoP-bound');
  }
  return { accessToken: token.access_token, expiresAt: Date.now() + Number(token.expires_in ?? 0) * 1000 };
}
