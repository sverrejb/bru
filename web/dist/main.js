// @ts-check
/** @typedef {import('./util.js').Phone} Phone */

import init, { Bru } from './pkg/bru_web.js';
import { clearAll, loadOrCreateKey, loadPhone, savePhone, sessionName } from './util.js';

const RELAY_TIMEOUT_MS = 15_000;

/** @type {<T extends HTMLElement = HTMLElement>(id: string) => T} */
const $ = (id) => /** @type {any} */ (document.getElementById(id));

/**
 * @param {number} ms
 * @param {string} message
 * @returns {Promise<never>}
 */
const rejectAfter = (ms, message) =>
  new Promise((_, reject) => setTimeout(() => reject(new Error(message)), ms));

if (location.hostname === 'localhost') reloadOnRebuild();

const phone = loadPhone();
if (phone) {
  $('getStarted').hidden = true;
  $('yourPhone').hidden = false;
  $('pairedPhoneName').textContent = phone.name;
} else {
  await pair();
}

async function pair() {
  /** @type {HTMLDialogElement} */
  const dialog = $('pairDialog');
  dialog.addEventListener('close', () => {
    if (dialog.returnValue === 'ok') location.href = '/phone';
    if (dialog.returnValue === 'cancel') {
      clearAll();
      location.reload();
    }
  });

  try {
    await init();
    const key = loadOrCreateKey();
    const name = sessionName(key);
    const bru = await Bru.open(key);

    await Promise.race([
      bru.online(),
      rejectAfter(RELAY_TIMEOUT_MS, 'Could not reach a relay server. Check your connection.'),
    ]);

    $('qr').insertAdjacentHTML('beforeend', bru.pairing_code(name));
    $('pairUrl').textContent = bru.pair_url(name);
    $('sessionName').textContent = name;

    const { id, message } = JSON.parse(await bru.accept_incoming());
    /** @type {Phone} */
    const paired = { id, name: message.name };
    savePhone(paired);

    $('phoneId').textContent = paired.id;
    $('phoneName').textContent = paired.name;
    dialog.showModal();
  } catch (e) {
    const error = $('pairError');
    error.textContent = e instanceof Error ? e.message : String(e);
    error.hidden = false;
  }
}

async function reloadOnRebuild() {
  const stamp = async () =>
    (await fetch('./pkg/bru_web_bg.wasm', { method: 'HEAD', cache: 'no-store' }))
      .headers.get('last-modified');

  const built = await stamp();
  setInterval(async () => {
    if (await stamp() !== built) location.reload();
  }, 1000);
}
