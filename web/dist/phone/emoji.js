// @ts-check

/** @type {Promise<Record<string, string>>} */
let loading;

const loadEmojis = () => loading ??= fetch(new URL('./emoji.json', import.meta.url))
  .then((r) => r.json())
  .catch(() => ({}));

const colonEmojiRegex = /(?<=^|\s):([a-z0-9_+-]{2,}):?$/;

/**
 * @param {Record<string, string>} emoji
 * @param {string} query
 * @returns {[string, string][]}
 */
const search = (emoji, query) => Object.entries(emoji)
  .filter(([name]) => name.includes(query))
  .sort(([a], [b]) => a.indexOf(query) - b.indexOf(query))
  .slice(0, 5);

/** @param {HTMLTextAreaElement} input */
export function initEmojiPalette(input) {
  const list = document.createElement('ul');
  list.id = 'emojiPalette';
  list.className = 'emoji-palette card';
  list.setAttribute('role', 'listbox');
  list.setAttribute('aria-label', 'Emoji suggestions');
  list.hidden = true;
  input.insertAdjacentElement('afterend', list);
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('aria-controls', list.id);

  const status = document.createElement('p');
  status.className = 'visually-hidden';
  status.setAttribute('aria-live', 'polite');
  list.insertAdjacentElement('afterend', status);

  /** @type {number} */
  let announcing;

  /** @param {string} message */
  const announce = (message) => {
    clearTimeout(announcing);
    announcing = setTimeout(() => status.textContent = message, 500);
  };

  const close = () => {
    list.replaceChildren();
    list.hidden = true;
    input.removeAttribute('aria-activedescendant');
    announce('');
  };

  /** @param {Element} option */
  const select = (option) => {
    list.querySelector('[aria-selected="true"]')?.setAttribute('aria-selected', 'false');
    option.setAttribute('aria-selected', 'true');
    input.setAttribute('aria-activedescendant', option.id);
  };

  /** @param {string | null | undefined} char */
  const insertEmoji = (char) => {
    const match = colonEmojiRegex.exec(input.value.slice(0, input.selectionStart));
    if (!match || !char) return;
    const head = input.value.slice(0, input.selectionStart - match[0].length) + char;
    input.value = head + input.value.slice(input.selectionStart);
    input.setSelectionRange(head.length, head.length);
    close();
  };

  input.addEventListener('input', async () => {
    const emojis = await loadEmojis();
    const match = colonEmojiRegex.exec(input.value.slice(0, input.selectionStart));
    if (!match) return close();
    if (match[0].endsWith(':') && emojis[match[1]]) return insertEmoji(emojis[match[1]]);
    list.replaceChildren(...search(emojis, match[1]).map(([name, char], index) => {
      const option = document.createElement('li');
      option.id = `emojiOption${index}`;
      option.dataset.char = char;
      option.setAttribute('role', 'option');
      option.textContent = `${char} :${name}:`;
      return option;
    }));
    if (!list.firstElementChild) return close();
    select(list.firstElementChild);
    list.hidden = false;
    announce(`${list.children.length} emoji`);
  });

  input.addEventListener('keydown', (e) => {
    const active = list.querySelector('[aria-selected="true"]');
    if (!active || e.metaKey || e.ctrlKey) return;
    if (e.key === 'Escape') {
      e.preventDefault();
      return close();
    }
    if (e.key === 'Enter' || e.key === 'Tab') {
      e.preventDefault();
      return insertEmoji(active.getAttribute('data-char'));
    }
    const next = e.key === 'ArrowDown' ? active.nextElementSibling ?? list.firstElementChild
      : e.key === 'ArrowUp' ? active.previousElementSibling ?? list.lastElementChild : null;
    if (!next) return;
    e.preventDefault();
    select(next);
  });

  list.onmousedown = (e) => {
    e.preventDefault();
    if (e.target instanceof Element) insertEmoji(e.target.closest('li')?.getAttribute('data-char'));
  };

  input.addEventListener('blur', close);
}
