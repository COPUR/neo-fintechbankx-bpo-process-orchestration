// Start-up and navigation. Configuration comes from config.json next to index.html (see
// config.example.json); the deployment fills it with the staff realm and the API origin.

import { createApi, ApiError } from './api.js';
import { authorizeUrl, createPkce, exchangeCode, jwkThumbprint } from './auth.js';
import { createDpopKey } from './dpop.js';
import { LANGUAGES, t } from './i18n.js';
import { errorMessage, newTaskCount, unknownOutcomeMessage } from './view-model.js';
import { renderActorNav, renderDetail, renderList } from './views.js';
import { el, clear } from './dom.js';
import { forgetKey, loadOrCreateKey } from './key-store.js';

const state = { lang: 'tr', actors: [], actor: null, filter: 'mine', tasks: [], poll: null };
const POLL_MS = 60 * 1000;
const $ = (id) => document.getElementById(id);

function announce(message, kind = 'status') {
  const region = $(kind === 'error' ? 'alerts' : 'status');
  region.textContent = '';
  // A new text node after clearing makes screen readers announce repeated messages too.
  queueMicrotask(() => { region.textContent = message; });
}

function applyLanguage() {
  document.documentElement.lang = state.lang;
  document.title = t(state.lang, 'app.title');
  $('app-title').textContent = t(state.lang, 'app.title');
  $('skip-link').textContent = t(state.lang, 'app.skip');
  $('language-label').textContent = t(state.lang, 'app.language');
}

const codeOf = (e) => (e instanceof ApiError ? e.code : 'UNKNOWN');

async function failed(e) {
  const code = codeOf(e);
  announce(errorMessage(state.lang, code), 'error');
  if (code === 'SESSION') {
    sessionStorage.clear();
    await forgetKey().catch(() => {});
  }
}

async function guarded(action, success) {
  try {
    const result = await action();
    if (success) announce(t(state.lang, success));
    return result;
  } catch (e) {
    await failed(e);
    return undefined;
  }
}

function stopPolling() {
  clearInterval(state.poll);
  state.poll = null;
}

// Every minute, while the tab is visible, look for new tasks and say how many; the table is redrawn only when
// the user asks, so a row never moves under the pointer. A failed poll stays silent; the next one tries again.
function startPolling(api, showNewTasks) {
  stopPolling();
  const actor = state.actor;
  state.poll = setInterval(async () => {
    if (document.visibilityState !== 'visible' || state.actor !== actor) return;
    try {
      const fresh = await api.list(actor);
      if (state.actor === actor && state.poll) showNewTasks(newTaskCount(state.tasks, fresh));
    } catch { /* shown on the next user action */ }
  }, POLL_MS);
}

// {@code done} is the status of the action that led here; it is announced once the list is in, so the
// loading message does not replace it.
async function showList(api, config, done) {
  stopPolling();
  announce(t(state.lang, 'status.loading'));
  const tasks = await guarded(() => api.list(state.actor), done);
  if (tasks === undefined) return;
  state.tasks = tasks;
  const showNewTasks = renderList($('main'), {
    lang: state.lang, actor: state.actor, tasks, filter: state.filter,
    onFilter: (f) => { state.filter = f; showList(api, config); },
    onRefresh: () => showList(api, config),
    onOpen: (key) => showDetail(api, config, key),
    onClaim: async (key) => { if (await guarded(() => api.claim(state.actor, key), 'status.claimed')) showDetail(api, config, key); },
  });
  startPolling(api, showNewTasks);
}

// A decision whose answer was lost may or may not be recorded. Read the task once to tell which, show what
// that means, and never send the decision again on the user's behalf.
async function afterUnknownOutcome(api, config, key) {
  let result;
  try {
    result = { task: await api.read(state.actor, key) };
  } catch (e) {
    result = { code: codeOf(e) };
  }
  announce(t(state.lang, unknownOutcomeMessage(result)), 'error');
  if (result.task?.assignment === 'MINE') return showDetail(api, config, key);
  return showList(api, config);
}

async function showDetail(api, config, key) {
  stopPolling();
  const task = await guarded(() => api.read(state.actor, key));
  if (!task) return showList(api, config);
  renderDetail($('main'), {
    lang: state.lang, actor: state.actor, task, referenceViews: config.referenceViews,
    onBack: () => showList(api, config),
    onClaim: async () => { if (await guarded(() => api.claim(state.actor, key), 'status.claimed')) showDetail(api, config, key); },
    onRelease: async () => {
      const released = await guarded(() => api.release(state.actor, key).then(() => true));
      showList(api, config, released ? 'status.released' : undefined);
    },
    onDecide: async (decision, reason) => {
      try {
        await api.decide(state.actor, key, decision, reason);
      } catch (e) {
        if (codeOf(e) === 'OUTCOME_UNKNOWN') return afterUnknownOutcome(api, config, key);
        return failed(e);
      }
      return showList(api, config, 'status.saved');
    },
  });
}

function selectActor(api, config, actor) {
  state.actor = actor;
  renderActorNav($('actors'), { lang: state.lang, actors: state.actors, current: actor, onSelect: (a) => selectActor(api, config, a) });
  showList(api, config);
}

async function signIn(config, key) {
  const pkce = await createPkce();
  const stateParam = crypto.randomUUID();
  sessionStorage.setItem('bpo.pkce', JSON.stringify({ verifier: pkce.verifier, state: stateParam }));
  location.assign(authorizeUrl(config.oidc, { state: stateParam, challenge: pkce.challenge, jkt: await jwkThumbprint(key.publicJwk) }));
}

async function main() {
  const config = await (await fetch('config.json', { credentials: 'omit' })).json();
  state.lang = LANGUAGES.includes(navigator.language?.slice(0, 2)) ? navigator.language.slice(0, 2) : 'tr';
  applyLanguage();
  $('language').value = state.lang;

  // The same key signs the authorization request (dpop_jkt), the token request and every API call, so it is
  // kept in IndexedDB across the redirect; its private half is non-extractable.
  const key = await loadOrCreateKey(() => createDpopKey());
  const params = new URLSearchParams(location.search);
  const pending = JSON.parse(sessionStorage.getItem('bpo.pkce') ?? 'null');
  if (!params.get('code') || !pending || params.get('state') !== pending.state) {
    const button = el('button', { type: 'button' }, t(state.lang, 'app.signIn'));
    button.addEventListener('click', () => signIn(config, key));
    clear($('main'));
    $('main').append(button);
    return;
  }
  sessionStorage.removeItem('bpo.pkce');
  history.replaceState(null, '', location.pathname);
  const session = await exchangeCode(config.oidc, key, { code: params.get('code'), verifier: pending.verifier });
  const api = createApi({ baseUrl: config.apiBaseUrl, key, accessToken: () => session.accessToken });

  const me = await guarded(() => api.me());
  if (!me) return;
  $('user').textContent = t(state.lang, 'app.signedInAs', { user: me.user });
  state.actors = me.actors;
  $('language').addEventListener('change', (e) => {
    state.lang = e.target.value;
    applyLanguage();
    selectActor(api, config, state.actor);
  });
  if (state.actors.length === 0) {
    clear($('main'));
    $('main').append(el('p', { class: 'empty' }, t(state.lang, 'app.noActor')));
    return;
  }
  selectActor(api, config, state.actors[0]);
}

main().catch(() => announce(errorMessage(state.lang, 'UNKNOWN'), 'error'));
