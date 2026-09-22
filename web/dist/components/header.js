// @ts-check

import { loadPhone } from '../util.js';

const LINKS = [
  ['/selfhost/', 'Self host'],
  ['/about/', 'About'],
  ['/privacy/', 'Privacy'],
];

customElements.define('site-header', class extends HTMLElement {
  connectedCallback() {
    const subtitle = this.getAttribute('subtitle');
    const links = loadPhone() ? [['/phone/', 'Your phone'], ...LINKS] : LINKS;
    const here = location.pathname.replace(/\/$/, '');
    this.innerHTML = `
<header>
  <a href="/" class="logo">Bru</a>
  <nav class="nav-links">
    ${links.map(([href, text]) => `<a href="${href}"${href.replace(/\/$/, '') === here ? ' aria-current="page"' : ''}>${text}</a>`).join('\n    ')}
  </nav>
  ${subtitle ? `<p class="subtitle">${subtitle}</p>` : ''}
</header>`;
  }
});
