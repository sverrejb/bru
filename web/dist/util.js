// @ts-check

/**
 * @typedef {object} Message
 * @property {number} seq
 * @property {number} threadId
 * @property {string} address
 * @property {string | null} displayName
 * @property {string} body
 * @property {number} date
 * @property {'in' | 'out'} direction
 * @property {string} status
 * @property {string} [clientId]
 */

/** @typedef {{ id: string, name: string }} Phone */
/** @typedef {Map<number, Message[]>} Threads */

const ADJECTIVES = [
  'brave', 'calm', 'clever', 'cozy', 'crisp', 'curious', 'daring', 'dizzy', 'eager', 'earnest',
  'fuzzy', 'gentle', 'giddy', 'glad', 'gleeful', 'grumpy', 'happy', 'humble', 'jolly', 'jovial',
  'keen', 'kind', 'lively', 'lucky', 'mellow', 'merry', 'mighty', 'nifty', 'noble', 'nimble',
  'perky', 'plucky', 'proud', 'quiet', 'quick', 'rowdy', 'sleepy', 'sly', 'smiley', 'snappy',
  'sparkly', 'spry', 'stout', 'sturdy', 'sunny', 'swift', 'tidy', 'trusty', 'witty', 'zesty',
];
const COLORS = [
  'amber', 'azure', 'beige', 'black', 'blue', 'bronze', 'brown', 'chartreuse', 'coral', 'crimson',
  'cyan', 'emerald', 'fuchsia', 'gold', 'gray', 'green', 'indigo', 'ivory', 'jade', 'khaki',
  'lavender', 'lilac', 'lime', 'magenta', 'maroon', 'mauve', 'mint', 'navy', 'olive', 'orange',
  'peach', 'pink', 'plum', 'purple', 'red', 'rose', 'ruby', 'rust', 'sage', 'sapphire',
  'scarlet', 'sienna', 'silver', 'tan', 'teal', 'turquoise', 'violet', 'white', 'yellow', 'copper',
];
const NOUNS = [
  'anchor', 'badger', 'beacon', 'beaver', 'bison', 'boulder', 'bramble', 'canyon', 'cedar', 'comet',
  'condor', 'cricket', 'dolphin', 'dune', 'eagle', 'ember', 'falcon', 'fern', 'firefly', 'fox',
  'glacier', 'grove', 'harbor', 'hawk', 'heron', 'hollow', 'lantern', 'lynx', 'maple', 'meadow',
  'moose', 'moth', 'otter', 'owl', 'panther', 'pebble', 'phoenix', 'pine', 'plateau', 'quail',
  'raven', 'reef', 'ridge', 'river', 'sparrow', 'stream', 'thicket', 'tiger', 'willow', 'wren',
];

/**
 * @param {string[]} list
 * @param {number} byte
 */
const pick = (list, byte) => list[byte % list.length];

/**
 * @param {Uint8Array} key
 * @returns {string} human-readable name derived from the secret key
 */
export const sessionName = (key) =>
  `${pick(ADJECTIVES, key[0])}-${pick(COLORS, key[1])}-${pick(NOUNS, key[2])}`;

/**
 * @param {Message[]} messages
 * @returns {Threads}
 */
export const groupByThread = (messages) => Map.groupBy(messages, (message) => message.threadId);

/** @param {Message[]} messages */
export const lastOf = (messages) => messages[messages.length - 1];

/** @param {Threads} threads @returns {Message[][]} newest conversation first */
export const byRecency = (threads) =>
  [...threads.values()].sort((a, b) => lastOf(b).date - lastOf(a).date);

/** @param {Message[]} messages @returns {string} the contact's name, falling back to their number */
export const displayNameOf = (messages) =>
  messages.findLast((m) => m.direction === 'in' && m.displayName)?.displayName
  ?? lastOf(messages).address;

/** @returns {Uint8Array} the 32-byte secret key, generating and persisting one on first use */
export const loadOrCreateKey = () => {
  const stored = localStorage.getItem('bru.key');
  if (stored) return Uint8Array.from(atob(stored), (c) => c.charCodeAt(0));
  const fresh = crypto.getRandomValues(new Uint8Array(32));
  localStorage.setItem('bru.key', btoa(String.fromCharCode(...fresh)));
  return fresh;
};

/** @returns {Phone | null} the paired phone, if any */
export const loadPhone = () => {
  const id = localStorage.getItem('bru.phoneKey');
  return id ? { id, name: localStorage.getItem('bru.phoneName') ?? id } : null;
};

/** @param {Phone} phone */
export const savePhone = ({ id, name }) => {
  localStorage.setItem('bru.phoneKey', id);
  localStorage.setItem('bru.phoneName', name);
};

/** @returns {{ messages: Message[], cursor: number }} */
export const loadMessageCache = () => ({
  messages: JSON.parse(localStorage.getItem('bru.messages') ?? '[]'),
  cursor: Number(localStorage.getItem('bru.messageCursor') ?? 0),
});

/**
 * @param {Message[]} messages
 * @param {number} cursor
 */
export const saveMessageCache = (messages, cursor) => {
  localStorage.setItem('bru.messages', JSON.stringify(messages));
  localStorage.setItem('bru.messageCursor', String(cursor));
};

export const clearAll = () => localStorage.clear();
