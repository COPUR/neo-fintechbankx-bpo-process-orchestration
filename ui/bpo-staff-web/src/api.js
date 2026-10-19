// Client of bpo-process-api. Every call carries "Authorization: DPoP <token>" and a fresh proof. The only body
// the client ever sends is {"decision": "...", "reason": "..."}, both codes the server listed: ids come from the
// URL the server listed, never from a form, and the server refuses any other field (BODY_NOT_ALLOWED).

import { createProof } from './dpop.js';

export const ACTORS = ['compliance-analyst', 'compliance-senior', 'payments-operations', 'payments-supervisor'];
const TASK_KEY = /^[0-9]{1,19}$/;
const LIST_FILTERS = ['mine', 'open', 'all'];
const CURSOR = /^[A-Za-z0-9_-]{1,96}$/;
const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/;

export class ApiError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

export function createApi({ baseUrl, key, accessToken, fetchImpl = globalThis.fetch }) {
  const base = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;

  async function call(method, path, body) {
    const url = new URL(path, base).toString();
    const token = accessToken();
    const headers = {
      Authorization: `DPoP ${token}`,
      DPoP: await createProof(key, method, url, { accessToken: token }),
      Accept: 'application/json',
    };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try {
      response = await fetchImpl(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        credentials: 'omit', redirect: 'error' });
    } catch {
      // A read that failed changed nothing; a write may have reached the server before the answer was lost.
      throw new ApiError(0, method === 'GET' ? 'ENGINE_UNAVAILABLE' : 'OUTCOME_UNKNOWN');
    }
    if (response.status === 401) throw new ApiError(401, 'SESSION');
    if (!response.ok) {
      let code = 'UNKNOWN';
      try {
        const problem = await response.json();
        if (typeof problem.code === 'string' && /^[A-Z_]{1,40}$/.test(problem.code)) code = problem.code;
      } catch { /* not a problem body */ }
      // A gateway in front of the API may answer a write with a bare 5xx after the write went through.
      if (code === 'UNKNOWN' && method !== 'GET' && response.status >= 500) code = 'OUTCOME_UNKNOWN';
      throw new ApiError(response.status, code);
    }
    return response.status === 204 ? null : response.json();
  }

  function actorPath(actor, suffix = '') {
    if (!ACTORS.includes(actor)) throw new ApiError(0, 'NOT_YOUR_ACTOR');
    return `api/v1/actors/${actor}/tasks${suffix}`;
  }

  function taskPath(actor, taskKey, suffix = '') {
    const k = String(taskKey);
    if (!TASK_KEY.test(k)) throw new ApiError(0, 'TASK_NOT_FOUND');
    return actorPath(actor, `/${k}${suffix}`);
  }

  return {
    me: () => call('GET', 'api/v1/me'),
    // One page: {items, next, counts, asOf}. Only values the server handed out (next, asOf) go back to it.
    list: (actor, { filter = 'all', limit, cursor, createdAfter } = {}) => {
      const query = new URLSearchParams();
      if (!LIST_FILTERS.includes(filter)) throw new ApiError(0, 'QUERY_NOT_ALLOWED');
      query.set('filter', filter);
      if (limit !== undefined) {
        if (!Number.isInteger(limit) || limit < 1 || limit > 100) throw new ApiError(0, 'QUERY_NOT_ALLOWED');
        query.set('limit', String(limit));
      }
      if (cursor !== undefined && cursor !== null) {
        if (typeof cursor !== 'string' || !CURSOR.test(cursor)) throw new ApiError(0, 'QUERY_NOT_ALLOWED');
        query.set('cursor', cursor);
      }
      if (createdAfter !== undefined) {
        if (typeof createdAfter !== 'string' || !INSTANT.test(createdAfter)) throw new ApiError(0, 'QUERY_NOT_ALLOWED');
        query.set('createdAfter', createdAfter);
      }
      return call('GET', actorPath(actor, `?${query}`));
    },
    read: (actor, taskKey) => call('GET', taskPath(actor, taskKey)),
    claim: (actor, taskKey) => call('POST', taskPath(actor, taskKey, '/claim')),
    release: (actor, taskKey) => call('POST', taskPath(actor, taskKey, '/release')),
    decide: (actor, taskKey, decision, reason) => {
      if (typeof decision !== 'string') throw new ApiError(0, 'DECISION_NOT_ALLOWED');
      if (typeof reason !== 'string') throw new ApiError(0, 'REASON_NOT_ALLOWED');
      return call('POST', taskPath(actor, taskKey, '/decision'), { decision, reason });
    },
  };
}
