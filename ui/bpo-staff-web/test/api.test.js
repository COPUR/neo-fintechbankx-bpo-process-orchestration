import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDpopKey } from '../src/dpop.js';
import { ApiError, createApi } from '../src/api.js';

function recorder(responses) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, ...init });
    const r = responses.shift() ?? { status: 204 };
    return { status: r.status, ok: r.status >= 200 && r.status < 300, json: async () => r.body };
  };
  return { calls, fetchImpl };
}

async function api(responses) {
  const key = await createDpopKey();
  const rec = recorder(responses);
  return { rec, api: createApi({ baseUrl: 'https://api.example', key, accessToken: () => 'tok', fetchImpl: rec.fetchImpl }) };
}

test('every call sends the token with the DPoP scheme and a proof, never cookies', async () => {
  const { rec, api: client } = await api([{ status: 200, body: [] }]);
  await client.list('compliance-analyst');
  const [call] = rec.calls;
  assert.equal(call.url, 'https://api.example/api/v1/actors/compliance-analyst/tasks');
  assert.equal(call.headers.Authorization, 'DPoP tok');
  assert.equal(call.headers.DPoP.split('.').length, 3);
  assert.equal(call.credentials, 'omit');
});

test('a decision sends the decision alone', async () => {
  const { rec, api: client } = await api([{ status: 204 }]);
  await client.decide('compliance-senior', '2251799813685301', 'CLEAR');
  const [call] = rec.calls;
  assert.equal(call.method, 'POST');
  assert.equal(call.url, 'https://api.example/api/v1/actors/compliance-senior/tasks/2251799813685301/decision');
  assert.equal(call.body, '{"decision":"CLEAR"}');
});

test('a key or actor that is not plain never reaches the network', async () => {
  const { rec, api: client } = await api([]);
  assert.throws(() => client.read('compliance-analyst', '../me'), (e) => e instanceof ApiError && e.code === 'TASK_NOT_FOUND');
  assert.throws(() => client.list('admin'), (e) => e.code === 'NOT_YOUR_ACTOR');
  assert.throws(() => client.decide('compliance-analyst', '7', { decision: 'CLEAR', caseId: 'x' }), (e) => e.code === 'DECISION_NOT_ALLOWED');
  assert.equal(rec.calls.length, 0);
});

test('refusals come back as their code; 401 is a lost session; a network failure is the engine', async () => {
  const { api: client } = await api([
    { status: 409, body: { code: 'FOUR_EYES_REQUIRED' } },
    { status: 401, body: {} },
    { status: 500, body: { code: '<script>' } },
  ]);
  await assert.rejects(client.decide('compliance-senior', '7', 'CLEAR'), (e) => e.status === 409 && e.code === 'FOUR_EYES_REQUIRED');
  await assert.rejects(client.me(), (e) => e.code === 'SESSION');
  await assert.rejects(client.me(), (e) => e.code === 'UNKNOWN');
  const broken = createApi({ baseUrl: 'https://api.example/', key: await createDpopKey(), accessToken: () => 't',
    fetchImpl: async () => { throw new TypeError('offline'); } });
  await assert.rejects(broken.me(), (e) => e.code === 'ENGINE_UNAVAILABLE');
});
