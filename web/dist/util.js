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
 * @returns {string}
 */
const pick = (list, byte) => list[byte % list.length];

/** @returns {Uint8Array} the 32-byte secret key, persisted in localStorage */
export const key = () => {
  const stored = localStorage.getItem('bru.key');
  if (stored) return Uint8Array.from(atob(stored), (c) => c.charCodeAt(0));
  const fresh = crypto.getRandomValues(new Uint8Array(32));
  localStorage.setItem('bru.key', btoa(String.fromCharCode(...fresh)));
  return fresh;
};

/** @returns {string | null} the paired phone's endpoint id, if paired */
export const phoneKey = () => {
  const stored = localStorage.getItem('bru.phoneKey');
  if (!stored) {
    return null;
  }
  return stored;
}

/** @returns {string | null} */
export const phoneName = () => localStorage.getItem('bru.phoneName');

/** @returns {string | null} this browser's endpoint id */
export const clientId = () => localStorage.getItem('bru.clientId');

/** @returns {string} human-readable session name derived from the secret key */
export const name = () => {
  const keyBytes = key();
  return `${pick(ADJECTIVES, keyBytes[0])}-${pick(COLORS, keyBytes[1])}-${pick(NOUNS, keyBytes[2])}`;
}