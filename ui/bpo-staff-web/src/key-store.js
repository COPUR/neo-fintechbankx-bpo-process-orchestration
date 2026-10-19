// Keeps the DPoP key pair across the sign-in redirect. IndexedDB stores the CryptoKey objects themselves; the
// private key stays non-extractable, so neither the page nor anything reading the database can export it.

const DB = 'bpo-staff-web';
const STORE = 'dpop';

function open(indexedDb) {
  return new Promise((resolve, reject) => {
    const request = indexedDb.open(DB, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function run(db, mode, work) {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, mode);
    const request = work(tx.objectStore(STORE));
    tx.oncomplete = () => resolve(request?.result);
    tx.onerror = () => reject(tx.error);
  });
}

/** The stored key, or a new one from {@code create} that is stored first. */
export async function loadOrCreateKey(create, indexedDb = globalThis.indexedDB) {
  const db = await open(indexedDb);
  const existing = await run(db, 'readonly', (s) => s.get('key'));
  if (existing) return existing;
  const key = await create();
  await run(db, 'readwrite', (s) => s.put(key, 'key'));
  return key;
}

/** Sign-out: the key goes, so a token left anywhere cannot be used from this browser again. */
export async function forgetKey(indexedDb = globalThis.indexedDB) {
  const db = await open(indexedDb);
  await run(db, 'readwrite', (s) => s.delete('key'));
}
