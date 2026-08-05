// @ts-check
/** @typedef {import('../util.js').Message} Message */
/** @typedef {import('../util.js').Threads} Threads */

import init, { Bru } from '../pkg/bru_web.js';
import {
  byRecency, clearAll, displayNameOf, groupByThread, lastOf, loadMessageCache, loadOrCreateKey,
  loadPhone, saveMessageCache, sessionName,
} from '../util.js';

const PAGE_SIZE = 500;

/** @type {<T extends HTMLElement = HTMLElement>(id: string) => T} */
const $ = (id) => /** @type {any} */ (document.getElementById(id));

/**
 * @param {ParentNode} node
 * @param {string} selector
 */
const $$ = (node, selector) => /** @type {HTMLElement} */ (node.querySelector(selector));

/** @param {Element | null} el */
const asRow = (el) => /** @type {HTMLElement | null} */ (el);

/**
 * @param {HTMLElement} el
 * @param {string} id
 */
const showId = (el, id) => {
  el.textContent = el.title = id;
};

/** @type {HTMLElement} */
const threadListEl = $('threadList');
const messageListEl = $('messageList');
/** @type {HTMLTextAreaElement} */
const smsBody = $('smsBody');
/** @type {HTMLTemplateElement} */
const threadRowTpl = $('threadRowTpl');
/** @type {HTMLTemplateElement} */
const messageRowTpl = $('messageRowTpl');

const phone = loadPhone() ?? goPair();

await init();
const key = loadOrCreateKey();
const bru = await Bru.open(key);

showId($('clientId'), bru.id());
showId($('phoneId'), phone.id);
$('clientName').textContent = sessionName(key);
$('phoneName').textContent = phone.name;

$('clearBtn').onclick = clearData;
$('sendBtn').onclick = sendMessage;
smsBody.onkeydown = (e) => {
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) sendMessage();
};
threadListEl.onclick = (e) => {
  const row = asRow(/** @type {Element} */ (e.target).closest('.thread-row'));
  if (row) selectThread(row);
};

reportHealth();

const threads = groupByThread(await syncMessages());
renderThreads(threads);

/** @returns {never} */
function goPair() {
  location.replace('/');
  throw new Error('not paired');
}

/** @param {Threads} threads */
function renderThreads(threads) {
  if (threads.size === 0) {
    messageListEl.textContent =
      'No messages yet. Try refreshing in a little while. If that does not work, delete data and try again.';
    return;
  }

  renderList(threadListEl, threadRowTpl, byRecency(threads), (node, messages) => {
    const last = lastOf(messages);
    $$(node, '.thread-row').dataset.threadId = String(last.threadId);
    $$(node, '.thread-name').textContent = displayNameOf(messages);
    $$(node, '.thread-preview').textContent = last.body;
  });

  const first = asRow(threadListEl.querySelector('.thread-row'));
  if (first) selectThread(first);
}

/** @param {HTMLElement} row */
function selectThread(row) {
  const previous = selectedRow();
  if (previous) previous.dataset.draft = smsBody.value;
  previous?.classList.remove('selected');
  row.classList.add('selected');

  const messages = messagesOf(row);
  smsBody.placeholder = `Send SMS to ${displayNameOf(messages)}`;
  smsBody.value = row.dataset.draft ?? '';
  renderMessages(messages);
}

/** @param {Message[]} messages */
function renderMessages(messages) {
  messageListEl.innerHTML = '';
  renderList(messageListEl, messageRowTpl, messages, (node, message) => {
    $$(node, '.message-row').classList.add(message.direction);
    $$(node, '.message-body').textContent = message.body;
    $$(node, '.message-meta').textContent = new Date(message.date).toLocaleString();
  });
  messageListEl.scrollTop = messageListEl.scrollHeight;
}

async function sendMessage() {
  const row = selectedRow();
  if (!row || !smsBody.value) return;

  const to = lastOf(messagesOf(row)).address;
  await bru.send_message(phone.id, to, smsBody.value, crypto.randomUUID());
  smsBody.value = '';
  delete row.dataset.draft;
  //TODO: either fetch new messages on OK or do optimistic UI-thing and insert it temporary (do not persist).
  // TODO: also add visual indicator if message was not sent, and do not clear smsBody in that case
}

/** @returns {Promise<Message[]>} */
async function syncMessages() {
  const { messages, cursor } = loadMessageCache();
  let since = cursor;
  let hasMore = true;

  while (hasMore) {
    const page = JSON.parse(await bru.messages(phone.id, since, PAGE_SIZE));
    messages.push(...page.messages);
    since = page.cursor;
    hasMore = page.hasMore;
  }

  saveMessageCache(messages, since);
  return messages;
}

async function reportHealth() {
  const error = $('healthError');
  try {
    const { status } = JSON.parse(await bru.health(phone.id));
    if (status === 'ok') return;
    error.textContent = `Phone reported status: ${status}`;
  } catch {
    error.textContent = 'Could not reach phone.';
  }
  error.hidden = false;
}

function clearData() {
  if (confirm('Clear all Bru-related data off this browser?')) {
    clearAll();
    location.href = '/';
  }
}

function selectedRow() {
  return asRow(threadListEl.querySelector('.thread-row.selected'));
}

/** @param {HTMLElement} row */
function messagesOf(row) {
  return threads.get(Number(row.dataset.threadId)) ?? [];
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
