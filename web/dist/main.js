// @ts-check
/** @typedef {import('./util.js').Phone} Phone */

import init, { Bru } from './pkg/bru_web.js';
import {
  clearAll, loadOrCreateKey, loadPhone, loadRelay, relayReady, savePhone, saveRelay, sessionName,
} from './util.js';

/** @type {<T extends HTMLElement = HTMLElement>(id: string) => T} */
const $ = (id) => /** @type {any} */ (document.getElementById(id));

/** @type {HTMLDialogElement} */
const optionsDialog = $('optionsDialog');
/** @type {HTMLInputElement} */
const relayInput = $('relayInput');
/** @type {HTMLInputElement} */
const tokenInput = $('tokenInput');

$('optionsBtn').addEventListener('click', () => {
  const relay = loadRelay();
  relayInput.value = relay.url ?? '';
  tokenInput.value = relay.token ?? '';
  optionsDialog.showModal();
});

/** @type {HTMLElement} */
const relayStatus = $('relayStatus');
/** @type {HTMLButtonElement} */
const relaySaveBtn = $('relaySaveBtn');

/** @type {HTMLFormElement} */
const relayForm = $('relayForm');

relayForm.addEventListener('submit', async (e) => {
  if (e.submitter !== relaySaveBtn) return;
  e.preventDefault();
  const url = relayInput.value.trim() || null;
  const token = tokenInput.value.trim() || null;
  relaySaveBtn.disabled = true;
  relayStatus.hidden = false;
  relayStatus.classList.remove('error');
  relayStatus.textContent = 'Checking…';
  /** @type {Bru | undefined} */
  let bru;
  try {
    await init();
    bru = await Bru.open(loadOrCreateKey(), url, token);
    await relayReady(bru);
    saveRelay({ url, token });
    location.reload();
  } catch (err) {
    relayStatus.classList.add('error');
    relayStatus.textContent = /** @type {Error} */ (err).message;
    relaySaveBtn.disabled = false;
    await bru?.close();
  }
});

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
  dialog.addEventListener('close', async () => {
    if (dialog.returnValue === 'ok') location.href = '/phone';
    if (dialog.returnValue === 'cancel') {
      await clearAll();
      location.reload();
    }
  });

  try {
    await init();
    const key = loadOrCreateKey();
    const name = sessionName(key);
    const relay = loadRelay();
    const bru = await Bru.open(key, relay.url, relay.token);

    await relayReady(bru);

    $('qr').insertAdjacentHTML('beforeend', bru.pairing_code(name));
    $('pairUrl').textContent = bru.pair_url(name);
    $('sessionName').textContent = name.replaceAll('-', ' ');

    const { id, message } = JSON.parse(await bru.accept_incoming());
    /** @type {Phone} */
    const paired = { id, name: message.name };
    savePhone(paired);

    $('phoneId').textContent = paired.id;
    $('phoneName').textContent = paired.name;
    dialog.showModal();
  } catch (e) {
    const error = $('pairError');
    error.textContent = /** @type {Error} */ (e).message;
    error.hidden = false;
  }
}