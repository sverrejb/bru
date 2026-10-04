// @ts-check
/** @typedef {import('../util.js').Message} Message */
/** @typedef {import('../util.js').Threads} Threads */

import init, { Bru } from '../pkg/bru_web.js';
import { initEmojiPalette } from './emoji.js';
import {
  asPng, byRecency, clearAll, displayNameOf, formatDate, fromBase64, groupByThread, newestOf, loadMessageCache,
  loadOrCreateKey, loadPhone, loadRelay, mergeTempThreads, reencode, relayReady, saveMessageCache, sessionName, toBase64,
  withTimeout,
} from '../util.js';

const PAGE_SIZE = 500;
/** @type {Record<string, string>} display text for client-side-only statuses, keyed by Message.status */
const PENDING_STATUS_LABEL = { sending: 'Sending…', pending: 'Sending…', failed: 'Failed to send' };

/** @type {<T extends HTMLElement = HTMLElement>(id: string) => T} */
const $ = (id) => /** @type {any} */(document.getElementById(id));

/**
 * @param {ParentNode} node
 * @param {string} selector
 */
const $$ = (node, selector) => /** @type {HTMLElement} */(node.querySelector(selector));

/** @param {Element | null} el */
const asRow = (el) => /** @type {HTMLElement | null} */(el);

/** @type {HTMLElement} */
const threadListEl = $('threadList');
const messageListEl = $('messageList');
/** @type {HTMLTextAreaElement} */
const smsBody = $('smsBody');
/** @type {HTMLTemplateElement} */
const threadRowTpl = $('threadRowTpl');
/** @type {HTMLTemplateElement} */
const messageRowTpl = $('messageRowTpl');
/** @type {HTMLDialogElement} */
const newMsgModal = $('newMsgModal');
/** @type {HTMLInputElement} */
const newMsgInput = $('newMsgInput');
/** @type {HTMLInputElement} */
const notifyToggle = $('notifyToggle');
/** @type {HTMLTextAreaElement} */
const clipboardText = $('clipboardText');
/** @type {HTMLButtonElement} */
const copyClipboardBtn = $('copyClipboardBtn');
/** @type {HTMLButtonElement} */
const sendClipboardBtn = $('sendClipboardBtn');
/** @type {HTMLButtonElement} */
const clearClipboardBtn = $('clearClipboardBtn');
/** @type {HTMLImageElement} */
const clipboardImage = $('clipboardImage');
/** @type {HTMLElement} */
const clipboardImageBox = $('clipboardImageBox');
/** @type {HTMLElement} */
const clipboardStatus = $('clipboardStatus');
/** @type {HTMLElement} */
const liveAnnounce = $('liveAnnounce');
/** @type {HTMLInputElement} */
const fileInput = $('fileInput');
/** @type {HTMLElement} */
const dropZone = $('dropZone');
/** @type {HTMLElement} */
const dropPrompt = $('dropPrompt');
/** @type {HTMLElement} */
const dropProgress = $('dropProgress');
/** @type {HTMLElement} */
const fileList = $('fileList');
/** @type {HTMLTemplateElement} */
const fileRowTpl = $('fileRowTpl');
/** @type {HTMLElement} */
const fileStatus = $('fileStatus');

// 8 MiB frame limit, minus base64 inflation and JSON overhead
const IMAGE_CAP = 5 * 1024 * 1024;
const JPEG_QUALITY = 0.8;
// longer timeout in case of large images TODO: test some scenarios, maybe this is too generous
const IMAGE_TIMEOUT_MS = 60_000;

/** @type {Blob | null} */
let clipboardBlob = null;
let fileQueue = Promise.resolve();

const phone = loadPhone() ?? goPair();

await init();
const key = loadOrCreateKey();
const relay = loadRelay();
const bru = await Bru.open(key, relay.url, relay.token);

$('clientName').textContent = sessionName(key);
$('phoneName').textContent = phone.name;

$('clearBtn').onclick = clearData;
$('sendBtn').onclick = sendMessage;
$('newBtn').onclick = newMessage;
copyClipboardBtn.onclick = copyClipboard;
sendClipboardBtn.onclick = sendClipboard;
clearClipboardBtn.onclick = () => showImage(null);

fileInput.onchange = () => {
  queueFiles(Array.from(fileInput.files ?? []));
  fileInput.value = '';
};
dropZone.ondragover = (e) => {
  e.preventDefault();
  dropZone.classList.add('dragging');
};
dropZone.ondragleave = () => dropZone.classList.remove('dragging');
dropZone.ondrop = (e) => {
  e.preventDefault();
  dropZone.classList.remove('dragging');
  queueFiles(Array.from(e.dataTransfer?.files ?? []));
};
/** @param {DragEvent} e */
const ignoreStrayFileDrop = (e) => {
  if (e.dataTransfer?.types.includes('Files')) e.preventDefault();
};
document.addEventListener('dragover', ignoreStrayFileDrop);
document.addEventListener('drop', ignoreStrayFileDrop);

notifyToggle.checked = Notification.permission === 'granted' && localStorage.getItem('bru.notify') === 'on';
notifyToggle.onchange = askNotificationPermission;


initEmojiPalette(smsBody);

smsBody.onkeydown = (e) => {
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) sendMessage();
};

clipboardText.onkeydown = (e) => {
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) sendClipboard();
};

document.addEventListener('paste', async (e) => {
  const pasted = e.target === smsBody ? undefined : e.clipboardData?.files[0];
  if (!pasted?.type.startsWith('image/')) return;
  e.preventDefault();
  // the clipboard hands over a decoded PNG, so a photo arrives many times its original size
  const image = pasted.size > IMAGE_CAP ? await reencode(pasted, 'image/jpeg', JPEG_QUALITY) : pasted;
  if (image.size > IMAGE_CAP) {
    showStatus(clipboardStatus, 'That image is too large.');
    return;
  }
  showStatus(clipboardStatus, '');
  showImage(image);
  announce('Image pasted');
});
threadListEl.onclick = (e) => {
  const row = asRow(/** @type {Element} */(e.target).closest('.thread-row'));
  if (row) {
    selectThread(row);
    smsBody.focus();
  }
};

reportHealth();

const cache = await loadMessageCache();
/** @type {Map<string, string>} unsent text per thread, so a re-render cannot lose it */
const drafts = new Map();
/** @type {Map<string, Message>} outgoing messages not yet reflected in a synced fetch, keyed by clientId */
const pending = new Map();

/** @type {Threads} */
let threads = groupByThread(await syncMessages());
renderThreads();

acceptLoop();

/** @returns {never} */
function goPair() {
  location.replace('/');
  throw new Error('not paired');
}

async function acceptLoop() {
  const error = $('healthError');
  while (true) {
    try {
      /** @type {Uint8Array<ArrayBuffer>[]} */
      const parts = [];
      const { message, error: failure } = JSON.parse(await bru.accept_incoming(phone.id, (/** @type {Uint8Array<ArrayBuffer>} */ chunk) => {
        if (!parts.length) showProgress('Receiving a file…');
        parts.push(chunk);
      }));
      if (failure) {
        console.warn('[bru] push failed', failure);
        if (parts.length) {
          showProgress('');
          showStatus(fileStatus, 'A file from your phone did not arrive in full.');
        }
        continue;
      }

      if (message.op === 'file') {
        showProgress('');
        addReceivedFile(message.name, new Blob(parts, { type: message.mime }));
        announce(`File received: ${message.name}`);
        continue;
      }
      if (message.op === 'clipboard') {
        showImage(message.data ? fromBase64(message.data, message.mime) : null);
        if (message.text) clipboardText.value = message.text;
        announce(message.data ? 'Image received from your phone' : 'Text received from your phone');
        continue;
      }
      if (message.op !== 'wake') {
        console.warn('[bru] ignoring unknown push op', message.op);
        continue;
      }

      const seen = cache.messages.length;
      threads = groupByThread(await syncMessages());
      renderThreads();
      cache.messages.slice(seen)
        .filter((message) => message.direction === 'in')
        .forEach((message) => {
          const text = `New message from ${displayNameOf([message])}`;
          announce(text);
          notify(text);
        });
    } catch (e) {
      console.error('[bru] accept-loop died', e);
      error.textContent = 'Lost the connection to your phone. Reload window to try again.';
      error.hidden = false;
      return;
    }
  }
}

function renderThreads() {
  if (threads.size === 0) {
    messageListEl.textContent =
      'No messages yet. Try refreshing in a little while. If that does not work, delete data and try again.';
    return;
  }

  const previous = selectedRow();
  if (previous) drafts.set(String(previous.dataset.address), smsBody.value);
  const previousAddress = previous?.dataset.address;

  threadListEl.innerHTML = '';
  renderList(threadListEl, threadRowTpl, byRecency(threads), (node, messages) => {
    const last = newestOf(messages);
    const row = $$(node, '.thread-row');
    row.dataset.threadId = String(last.threadId);
    row.dataset.address = last.address;
    $$(node, '.thread-name').textContent = displayNameOf(messages);
    $$(node, '.thread-preview').textContent = last.body;
  });

  const rows = /** @type {HTMLElement[]} */ (Array.from(threadListEl.querySelectorAll('.thread-row')));
  const row = rows.find((r) => r.dataset.address === previousAddress) ?? rows[0];
  if (row) selectThread(row);
}

/** @param {HTMLElement} row */
function selectThread(row) {
  const previous = selectedRow();
  if (previous) {
    drafts.set(String(previous.dataset.address), smsBody.value);
  }
  previous?.removeAttribute('aria-current');
  row.setAttribute('aria-current', 'true');

  const address = String(row.dataset.address);
  const messages = messagesOf(row);
  smsBody.placeholder = smsBody.ariaLabel = `Send SMS to ${messages.length ? displayNameOf(messages) : address}`;
  smsBody.value = drafts.get(address) ?? '';
  renderMessages(messages);
}

/** @param {Message[]} messages */
function renderMessages(messages) {
  messageListEl.innerHTML = '';
  renderList(messageListEl, messageRowTpl, messages, (node, message) => {
    $$(node, '.message-row').classList.add(message.direction, message.status);
    $$(node, '.message-body').append(find_links(message.body));
    $$(node, '.message-meta').textContent =
      PENDING_STATUS_LABEL[message.status] ?? formatDate(message.date);
  });
  requestAnimationFrame(() => { messageListEl.scrollTop = messageListEl.scrollHeight; });
}

async function sendMessage() {
  const row = selectedRow();
  if (!row || !smsBody.value) return;

  const to = String(row.dataset.address);
  const body = smsBody.value;
  const clientId = crypto.randomUUID();
  /** @type {Message} */
  const optimistic = {
    seq: -1, threadId: Number(row.dataset.threadId), address: to, displayName: null,
    body, date: Date.now(), direction: 'out', status: 'sending', clientId,
  };
  pending.set(clientId, optimistic);
  smsBody.value = '';
  drafts.delete(to);
  renderMessages(messagesOf(row));

  try {
    const response = await callBru(bru.send_message(phone.id, to, body, clientId));
    optimistic.status = response.status;
  } catch (e) {
    console.error('[bru] send failed', { to, clientId }, e);
    optimistic.status = 'failed';
  }
  renderMessages(messagesOf(row));
}

/** @param {Blob | null} blob */
function showImage(blob) {
  const refocus = document.activeElement === (blob ? clipboardText : clearClipboardBtn);
  URL.revokeObjectURL(clipboardImage.src);
  clipboardBlob = blob;
  if (blob) clipboardImage.src = URL.createObjectURL(blob);
  else clipboardImage.removeAttribute('src');
  clipboardImageBox.hidden = !blob;
  clipboardText.hidden = blob !== null;
  if (refocus) (blob ? clearClipboardBtn : clipboardText).focus();
}

/**
 * @param {HTMLElement} el
 * @param {string} message
 */
function showStatus(el, message) {
  el.textContent = '';
  el.hidden = !message;
  requestAnimationFrame(() => { el.textContent = message; });
}

async function copyClipboard() {
  if (!clipboardBlob && !clipboardText.value) return;
  try {
    if (clipboardBlob) {
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': asPng(clipboardBlob) })]);
    } else {
      await navigator.clipboard.writeText(clipboardText.value);
    }
    flashButton(copyClipboardBtn, 'Copied!');
  } catch (e) {
    console.error('[bru] clipboard copy failed', e);
    showStatus(clipboardStatus, clipboardBlob ? "Couldn't copy the image. Right-click it and choose Save instead." : 'Could not copy.');
  }
}

async function sendClipboard() {
  if (!clipboardBlob && !clipboardText.value) return;
  try {
    const data = clipboardBlob ? await toBase64(clipboardBlob) : undefined;
    await callBru(
      bru.send_clipboard(phone.id, clipboardBlob ? '' : clipboardText.value, clipboardBlob?.type, data),
      clipboardBlob ? IMAGE_TIMEOUT_MS : undefined,
    );
    flashButton(sendClipboardBtn, 'Sent!');
    clipboardText.value = '';
    showImage(null);
    showStatus(clipboardStatus, '');
  } catch (e) {
    console.error('[bru] clipboard send failed', e);
    const failure = e instanceof Error ? e.message : 'Could not reach phone.';
    showStatus(clipboardStatus, clipboardBlob && failure === 'Something went wrong. Sorry!'
      ? 'Phone app might be too old to receive images. Update it and try again.'
      : failure);
  }
}

/** @param {File[]} files */
function queueFiles(files) {
  fileQueue = fileQueue.then(() => sendFiles(files));
}

/** @param {File[]} files */
async function sendFiles(files) {
  showStatus(fileStatus, '');
  for (const file of files) {
    try {
      await sendFile(file);
      announce(`Sent ${file.name}`);
    } catch (e) {
      console.error('[bru] file send failed', e);
      const failure = e instanceof Error ? e.message : 'Could not reach phone.';
      showStatus(fileStatus, failure === 'unsupported op'
        ? 'Phone app is too old to receive files. Update it and try again.'
        : failure);
      break;
    }
  }
  showProgress('');
}

/** @param {File} file */
async function sendFile(file) {
  const reader = file.stream().getReader();
  let sent = 0;
  const next = async () => {
    const { done, value } = await reader.read();
    if (done) return null;
    sent += value.length;
    showProgress(`Sending ${file.name}… ${Math.floor((sent / file.size) * 100)}%`);
    return value;
  };
  showProgress(`Sending ${file.name}…`);
  const mime = file.type || 'application/octet-stream';
  const response = JSON.parse(await bru.send_file(phone.id, file.name, mime, next));
  if (response.error) throw new Error(response.error);
}

/** @param {string} text empty shows the drop prompt again */
function showProgress(text) {
  dropProgress.textContent = text;
  dropProgress.hidden = !text;
  dropPrompt.hidden = Boolean(text);
}

/**
 * @param {string} name
 * @param {Blob} blob
 */
function addReceivedFile(name, blob) {
  const node = /** @type {DocumentFragment} */ (fileRowTpl.content.cloneNode(true));
  const row = $$(node, '.file-row');
  $$(node, '.file-name').textContent = name;
  const link = /** @type {HTMLAnchorElement} */ ($$(node, 'a'));
  link.href = URL.createObjectURL(blob);
  link.download = name;
  link.ariaLabel = `Download ${name}`;
  const clear = $$(node, 'button');
  clear.ariaLabel = `Clear ${name}`;
  clear.onclick = () => {
    const refocus = document.activeElement === clear;
    URL.revokeObjectURL(link.href);
    row.remove();
    if (refocus) fileInput.focus();
    announce(`Cleared ${name}`);
  };
  fileList.prepend(node);
}

/**
 * @param {Promise<string>} promise
 * @param {number} [timeoutMs]
 */
async function callBru(promise, timeoutMs) {
  const response = JSON.parse(await withTimeout(promise, () => new Error('Timed out reaching phone'), timeoutMs));
  if (response.error) throw new Error(response.error);
  return response;
}

/**
 * @param {HTMLButtonElement} btn
 * @param {string} text
 */
function flashButton(btn, text) {
  const original = btn.textContent;
  btn.textContent = text;
  announce(text);
  setTimeout(() => { btn.textContent = original; }, 1200);
}

/** removes pending sends once the real synced message with the same clientId shows up */
function reconcilePending() {
  if (pending.size === 0) return;
  const confirmedIds = new Set(cache.messages.map((m) => m.clientId));
  for (const clientId of pending.keys()) {
    if (confirmedIds.has(clientId)) {
      pending.delete(clientId);
    }
  }
}

/** @param {Message["body"]} body */
function find_links(body) {
  const fragment = document.createDocumentFragment();
  body.split(/(https?:\/\/[^\s<]+)/).forEach((part, i) => {
    if (i % 2 === 0) {
      fragment.append(part);
      return;
    }
    const url = part.replace(/[.,;:!?)\]]+$/, '');
    const a = document.createElement('a');
    a.href = a.textContent = url;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    fragment.append(a, part.slice(url.length));
  });
  return fragment;
}

/** @returns {Promise<Message[]>} */
async function syncMessages() {
  const { messages } = cache;
  const newMessages = [];
  let hasMore = true;

  while (hasMore) {
    const page = JSON.parse(await bru.messages(phone.id, cache.cursor, PAGE_SIZE));
    messages.push(...page.messages);
    newMessages.push(...page.messages);
    cache.cursor = page.cursor;
    hasMore = page.hasMore;
  }

  mergeTempThreads(messages);

  try {
    await saveMessageCache(newMessages, cache.cursor);
  } catch (e) {
    console.warn('could not cache messages locally', e);
    const warning = $('cacheWarning');
    warning.textContent =
      'Unable to save message history. Messages will be re-fetched from your phone on every visit. Sorry about that!';
    warning.hidden = false;
  }

  reconcilePending();
  return messages;
}

async function reportHealth() {
  const error = $('healthError');
  try {
    await relayReady(bru);
  } catch (e) {
    error.textContent = /** @type {Error} */ (e).message;
    error.hidden = false;
    return;
  }
  try {
    const { status } = JSON.parse(await bru.health(phone.id));
    if (status === 'ok') return;
    error.textContent = `Phone reported status: ${status}`;
  } catch {
    error.textContent = 'Could not reach phone.';
  }
  error.hidden = false;
}

async function clearData() {
  if (confirm('Clear all Bru-related data from this browser?')) {
    await clearAll();
    location.href = '/';
  }
}

function selectedRow() {
  return asRow(threadListEl.querySelector('.thread-row[aria-current]'));
}

/** @param {HTMLElement} row */
function messagesOf(row) {
  const confirmed = threads.get(Number(row.dataset.threadId)) ?? [];
  const address = row.dataset.address;
  const optimistic = [...pending.values()].filter((m) => m.address === address);
  return [...confirmed, ...optimistic].sort((a, b) => a.date - b.date);
}

/**
 * @template T
 * @param {HTMLElement} container
 * @param {HTMLTemplateElement} template
 * @param {T[]} items
 * @param {(node: DocumentFragment, item: T) => void} populate
 */
function renderList(container, template, items, populate) {
  items.forEach((item) => {
    const node = /** @type {DocumentFragment} */ (template.content.cloneNode(true));
    populate(node, item);
    container.appendChild(node);
  });
}

function newMessage() {

  newMsgInput.value = '';
  newMsgModal.returnValue = '';
  newMsgModal.showModal();
}

/** @param {string} address */
function addDraftRow(address) {
  const node = /** @type {DocumentFragment} */ (threadRowTpl.content.cloneNode(true));
  const row = $$(node, '.thread-row');
  row.dataset.address = address;
  $$(node, '.thread-name').textContent = address;
  threadListEl.prepend(node);
  selectThread(row);
  smsBody.focus();
}

newMsgModal.onclose = () => {
  const address = newMsgInput.value.trim();
  if (!newMsgModal.returnValue || !address) return;
  addDraftRow(address);
};

async function askNotificationPermission() {
  if (notifyToggle.checked && Notification.permission === 'default') {
    await Notification.requestPermission();
  }
  notifyToggle.checked = notifyToggle.checked && Notification.permission === 'granted';
  $('notifyDenied').hidden = Notification.permission !== 'denied';
  localStorage.setItem('bru.notify', notifyToggle.checked ? 'on' : 'off');
  notify('Notifications looks like this');
}

/** @param {string} text */
function notify(text) {
  if (!notifyToggle.checked) return;
  new Notification(text, { icon: '../favicon.svg' });
}

/** @param {string} text */
function announce(text) {
  liveAnnounce.textContent = '';
  requestAnimationFrame(() => { liveAnnounce.textContent = text; });
}